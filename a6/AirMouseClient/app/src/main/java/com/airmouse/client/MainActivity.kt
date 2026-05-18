package com.airmouse.client

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airmouse.client.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var webSocketManager: AirMouseWebSocket? = null
    private var gyroManager: GyroscopeManager? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isConnected = false

    private var sendRateHz = 60
    private var sensitivity = 1.0

    private var latestAlpha = 0.0
    private var latestBeta = 0.0
    private var latestGamma = 0.0

    private var packetCount = 0L

    private val sendRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                sendGyroReading()
                mainHandler.postDelayed(this, (1000L / sendRateHz))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("airmouse_prefs", MODE_PRIVATE)

        setupGyroscope()
        setupUI()
        restorePrefs()
    }

    private fun setupGyroscope() {
        gyroManager = GyroscopeManager(this) { alpha, beta, gamma ->
            latestAlpha = alpha * sensitivity
            latestBeta = beta * sensitivity
            latestGamma = gamma * sensitivity

            mainHandler.post {
                binding.tvAlpha.text = "%.1f".format(latestAlpha)
                binding.tvBeta.text = "%.1f".format(latestBeta)
                binding.tvGamma.text = "%.1f".format(latestGamma)
            }
        }

        if (!gyroManager!!.hasGyroscope()) {
            Toast.makeText(this, "No gyroscope detected!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            if (isConnected) disconnect() else connect()
        }

        val rates = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        binding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                sensitivity = 0.5 + progress * 0.1
                binding.tvSensitivity.text = "%.1fx".format(sensitivity)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { savePrefs() }
        })

        binding.seekSendRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                sendRateHz = rates[progress]
                binding.tvSendRate.text = "$sendRateHz Hz"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { savePrefs() }
        })

        // Left click → {"type":"click","button":"left"}
        binding.btnLeftClick.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            webSocketManager?.sendClick("left")
        }

        // Right click → {"type":"click","button":"right"}
        binding.btnRightClick.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            webSocketManager?.sendClick("right")
        }

        binding.btnCalibrate.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            gyroManager?.calibrate(latestAlpha, latestBeta, latestGamma)
            Toast.makeText(this, "Calibrated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connect() {
        val ip = binding.etIpAddress.text.toString().trim()
        val portStr = binding.etPort.text.toString().trim()

        if (ip.isEmpty()) {
            binding.etIpAddress.error = "Enter IP address"
            return
        }
        val port = portStr.toIntOrNull() ?: 8765
        // Always use TLS since your server always runs WSS
        val useTls = binding.rbWss.isChecked

        savePrefs()

        webSocketManager = AirMouseWebSocket(
            onStateChange = { state, message ->
                mainHandler.post { updateConnectionState(state, message) }
            },
            onLatencyUpdate = { latencyMs ->
                mainHandler.post { binding.tvLatency.text = "${latencyMs}ms" }
            }
        )

        webSocketManager!!.connect(ip, port, useTls)
    }

    private fun disconnect() {
        webSocketManager?.disconnect()
        webSocketManager = null
    }

    private fun updateConnectionState(state: ConnectionState, message: String) {
        when (state) {
            ConnectionState.CONNECTING -> {
                binding.statusDot.setBackgroundResource(R.drawable.status_dot_connecting)
                binding.tvStatus.text = "Connecting…"
                binding.btnConnect.text = "CONNECTING…"
                binding.btnConnect.isEnabled = false
                isConnected = false
                stopSendLoop()
            }
            ConnectionState.CONNECTED -> {
                binding.statusDot.setBackgroundResource(R.drawable.status_dot_connected)
                binding.tvStatus.text = "Connected"
                binding.btnConnect.text = "DISCONNECT"
                binding.btnConnect.isEnabled = true
                binding.btnConnect.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
                isConnected = true
                gyroManager?.start(100)
                startSendLoop()
                packetCount = 0
            }
            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                binding.statusDot.setBackgroundResource(R.drawable.status_dot_disconnected)
                binding.tvStatus.text = if (state == ConnectionState.ERROR) message.take(50) else "Disconnected"
                binding.tvLatency.text = "--ms"
                binding.btnConnect.text = "CONNECT"
                binding.btnConnect.isEnabled = true
                binding.btnConnect.backgroundTintList = getColorStateList(R.color.accent_green)
                isConnected = false
                gyroManager?.stop()
                stopSendLoop()
            }
        }
    }

    private fun sendGyroReading() {
        webSocketManager?.sendGyroData(latestAlpha, latestBeta, latestGamma)
        packetCount++
        binding.tvPacketCount.text = "packets sent: $packetCount"
    }

    private fun startSendLoop() {
        mainHandler.removeCallbacks(sendRunnable)
        mainHandler.post(sendRunnable)
    }

    private fun stopSendLoop() {
        mainHandler.removeCallbacks(sendRunnable)
    }

    private fun savePrefs() {
        prefs.edit()
            .putString("ip", binding.etIpAddress.text.toString())
            .putString("port", binding.etPort.text.toString())
            .putBoolean("wss", binding.rbWss.isChecked)
            .putInt("sensitivity", binding.seekSensitivity.progress)
            .putInt("send_rate", binding.seekSendRate.progress)
            .apply()
    }

    private fun restorePrefs() {
        binding.etIpAddress.setText(prefs.getString("ip", ""))
        binding.etPort.setText(prefs.getString("port", "8765"))
        val wasWss = prefs.getBoolean("wss", true) // default to wss since server always uses TLS
        binding.rbWss.isChecked = wasWss
        binding.rbWs.isChecked = !wasWss
        binding.seekSensitivity.progress = prefs.getInt("sensitivity", 10)
        binding.seekSendRate.progress = prefs.getInt("send_rate", 5)
    }

    override fun onPause() {
        super.onPause()
        if (!isConnected) gyroManager?.stop()
    }

    override fun onResume() {
        super.onResume()
        if (isConnected) gyroManager?.start(100)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSendLoop()
        gyroManager?.stop()
        webSocketManager?.destroy()
    }
}
