package com.airmouse.client

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

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

        // Trust all certs — needed for your self-signed cert
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        builder.hostnameVerifier { _, _ -> true }

        client = builder.build()

        val request = Request.Builder().url(url).build()
        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Connected!")
                onStateChange(ConnectionState.CONNECTED, "Connected")
                startPingJob(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                // Handle pong response from server
                try {
                    if (text.contains("pong")) {
                        val latency = System.currentTimeMillis() - pingTime
                        onLatencyUpdate(latency)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling message: ${e.message}")
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {}

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
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

    fun sendGyroData(alpha: Double, beta: Double, gamma: Double) {
        // Match exactly what your server expects: {"type":"move","alpha":x,"beta":y,"gamma":z}
        val json = """{"type":"move","alpha":$alpha,"beta":$beta,"gamma":$gamma}"""
        webSocket?.send(json)
    }

    fun sendClick(button: String) {
        // Match server: {"type":"click","button":"left"} or {"type":"click","button":"right"}
        val json = """{"type":"click","button":"$button"}"""
        webSocket?.send(json)
    }

    fun sendScroll(dy: Int) {
        val json = """{"type":"scroll","dy":$dy}"""
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
                // Send ping in the format your server expects
                ws.send("""{"type":"ping"}""")
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
