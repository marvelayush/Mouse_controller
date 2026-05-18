package com.example.gyroscope

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class WebSocketClient {
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var lastPingTime = 0L
    private var onLatencyUpdate: ((Int) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun setOnLatencyUpdate(callback: (Int) -> Unit) {
        onLatencyUpdate = callback
    }

    fun connect(serverIp: String, port: Int, onSuccess: () -> Unit) {
        scope.launch {
            try {
                val trustAllCerts = arrayOf<X509TrustManager>(TrustAllCerts())
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                val client = OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0])
                    .hostnameVerifier { _, _ -> true }
                    .build()

                val request = Request.Builder()
                    .url("wss://$serverIp:$port")
                    .build()

                webSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        isConnected = true
                        Log.d("WebSocket", "Connected to $serverIp:$port")
                        scope.launch(Dispatchers.Main) {
                            onSuccess()
                        }
                        startPingTimer()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val json = JsonParser.parseString(text).asJsonObject
                            if (json.has("type") && json.get("type").asString == "pong") {
                                val latency = (System.currentTimeMillis() - lastPingTime).toInt()
                                onLatencyUpdate?.invoke(latency)
                            }
                        } catch (e: Exception) {
                            Log.e("WebSocket", "Message parse error: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        isConnected = false
                        Log.e("WebSocket", "Connection failed: ${t.message}")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        isConnected = false
                        Log.d("WebSocket", "Closed: $reason")
                    }
                })
            } catch (e: Exception) {
                Log.e("WebSocket", "Error: ${e.message}")
            }
        }
    }

    private fun startPingTimer() {
        scope.launch {
            while (isConnected) {
                kotlinx.coroutines.delay(2000)
                if (isConnected) {
                    lastPingTime = System.currentTimeMillis()
                    val pingJson = """{"type":"ping"}"""
                    webSocket?.send(pingJson)
                }
            }
        }
    }

    fun sendMotion(alpha: Float, beta: Float, gamma: Float, sensitivity: Float) {
        if (!isConnected || webSocket == null) return
        try {
            val motionData = JsonObject().apply {
                addProperty("alpha", alpha * sensitivity)
                addProperty("beta", beta * sensitivity)
                addProperty("gamma", gamma * sensitivity)
            }
            webSocket?.send(motionData.toString())
        } catch (e: Exception) {
            Log.e("WebSocket", "Send error: ${e.message}")
        }
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Disconnect")
        webSocket = null
    }
}

class TrustAllCerts : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate>? = arrayOf()
}