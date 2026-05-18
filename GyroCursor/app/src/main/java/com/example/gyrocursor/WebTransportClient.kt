package com.example.gyrocursor

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLContext

class WebTransportClient {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
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
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(TrustAllCerts()), java.security.SecureRandom())
                
                socket = sslContext.socketFactory.createSocket(serverIp, port) as Socket
                writer = PrintWriter(socket!!.getOutputStream(), true)
                isConnected = true
                
                Log.d("WebTransport", "Connected to $serverIp:$port")
                
                CoroutineScope(Dispatchers.Main).launch {
                    onSuccess()
                }
                
                val reader = socket!!.getInputStream().bufferedReader()
                while (isConnected) {
                    val line = reader.readLine() ?: break
                    if (line.contains("pong", ignoreCase = true)) {
                        val latency = (System.currentTimeMillis() - lastPingTime).toInt()
                        onLatencyUpdate?.invoke(latency)
                    }
                }
            } catch (e: Exception) {
                Log.e("WebTransport", "Connection failed: ${e.message}")
                isConnected = false
            }
        }
    }

    fun sendMotion(alpha: Float, beta: Float, gamma: Float, sensitivity: Float) {
        scope.launch {
            try {
                if (isConnected && writer != null) {
                    val buf = ByteBuffer.allocate(12).apply {
                        order(ByteOrder.BIG_ENDIAN)
                        putFloat(alpha)
                        putFloat(beta)
                        putFloat(gamma)
                    }
                    socket?.getOutputStream()?.write(buf.array())
                    socket?.getOutputStream()?.flush()
                }
            } catch (e: Exception) {
                Log.e("WebTransport", "Send failed: ${e.message}")
            }
        }
    }

    fun sendPing() {
        scope.launch {
            try {
                if (isConnected && writer != null) {
                    lastPingTime = System.currentTimeMillis()
                    writer?.println("""{"type":"ping"}""")
                    writer?.flush()
                }
            } catch (e: Exception) {
                Log.e("WebTransport", "Ping failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        isConnected = false
        writer?.close()
        socket?.close()
    }
}

class TrustAllCerts : javax.net.ssl.X509TrustManager {
    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = arrayOf()
}
