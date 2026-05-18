package com.airmouse.client

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class GyroPacket(val alpha: Double, val beta: Double, val gamma: Double)
data class ClickPacket(val type: String) // "left_click" or "right_click"

class AirMouseWebSocket(
    private val onStateChange: (ConnectionState, String) -> Unit,
    private val onLatencyUpdate: (Long) -> Unit
) {
    companion object {
        private const val TAG = "AirMouseWS"
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var pingJob: Job? = null
    private var pingTime: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(ip: String, port: Int, useTls: Boolean) {
        disconnect()

        val scheme = if (useTls) "wss" else "ws"
        val url = "$scheme://$ip:$port"

        Log.d(TAG, "Connecting to $url")
        onStateChange(ConnectionState.CONNECTING, "Connecting to $url...")

        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        // For wss with self-signed cert — trust all (dev mode)
        if (useTls) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        client = builder.build()

        val request = Request.Builder().url(url).build()
        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Connected!")
                onStateChange(ConnectionState.CONNECTED, "Connected")
                startPingJob(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // Handle pong from server — server sends ping, we get pong echo back
                // Your Python server sends ping every 2s and expects pong
                if (text == "pong" || text.contains("pong")) {
                    val latency = System.currentTimeMillis() - pingTime
                    onLatencyUpdate(latency)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // binary pong
                val latency = System.currentTimeMillis() - pingTime
                onLatencyUpdate(latency)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
                ws.close(1000, null)
                onStateChange(ConnectionState.DISCONNECTED, "Disconnected: $reason")
                stopPingJob()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onStateChange(ConnectionState.DISCONNECTED, "Closed")
                stopPingJob()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure: ${t.message}")
                onStateChange(ConnectionState.ERROR, "Error: ${t.message}")
                stopPingJob()
            }
        })
    }

    fun sendGyroData(packet: GyroPacket) {
        val json = """{"alpha":${packet.alpha},"beta":${packet.beta},"gamma":${packet.gamma}}"""
        webSocket?.send(json)
    }

    fun sendClick(type: String) {
        // Send click event — extend your server to handle this type field
        val json = """{"type":"$type"}"""
        webSocket?.send(json)
    }

    fun disconnect() {
        stopPingJob()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        onStateChange(ConnectionState.DISCONNECTED, "Disconnected")
    }

    private fun startPingJob(ws: WebSocket) {
        pingJob = scope.launch {
            while (isActive) {
                delay(2000)
                pingTime = System.currentTimeMillis()
//                ws.send("ping") // respond to server's ping/pong cycle
            }
        }
    }

    private fun stopPingJob() {
        pingJob?.cancel()
        pingJob = null
    }

    fun isConnected() = webSocket != null

    fun destroy() {
        scope.cancel()
        disconnect()
    }
}
