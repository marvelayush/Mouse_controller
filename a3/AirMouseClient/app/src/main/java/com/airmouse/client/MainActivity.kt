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

    // Throttling — controls how often we send gyro data
    private var sendRateHz = 60
    private var lastSendTime = 0L

    // Sensitivity multiplier (1.0 = same as raw)
    private var sensitivity = 1.0

    // Last gyro reading (for send throttling without dropping latest value)
    private var latestAlpha = 0.0
    private var latestBeta = 0.0
    private var latestGamma = 0.0

    // Packet counter
    private var packetCount = 0L

    // Send loop using Handler
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
            // Called at sensor rate — we store latest and send at controlled rate
            latestAlpha = alpha * sensitivity
            latestBeta = beta * sensitivity
            latestGamma = gamma * sensitivity

            // Update UI (throttled to ~30fps for readability)
            if (System.currentTimeMillis() - lastSendTime > 33) {
                mainHandler.post {
                    binding.tvAlpha.text = "%.1f°".format(latestAlpha)
                    binding.tvBeta.text = "%.1f°".format(latestBeta)
                    binding.tvGamma.text = "%.1f°".format(latestGamma)
                }
            }
        }

        if (!gyroManager!!.hasGyroscope()) {
            Toast.makeText(this, "⚠️ No gyroscope detected on this device!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        // Connect / Disconnect button
        binding.btnConnect.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                connect()
            }
        }

        // Sensitivity slider: range 0.5x to 4.5x mapped over 40 steps
        binding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                sensitivity = 0.5 + progress * 0.1
                binding.tvSensitivity.text = "%.1fx".format(sensitivity)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { savePrefs() }
        })

        // Send rate slider: 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 Hz (10 steps)
        val rates = intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        binding.seekSendRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                sendRateHz = rates[progress]
                binding.tvSendRate.text = "$sendRateHz Hz"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { savePrefs() }
        })

        // Left click
        binding.btnLeftClick.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            webSocketManager?.sendClick("left_click")
        }

        // Right click
        binding.btnRightClick.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            webSocketManager?.sendClick("right_click")
        }

        // Calibrate — zeros current orientation as the neutral position
        binding.btnCalibrate.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            gyroManager?.calibrate(latestAlpha, latestBeta, latestGamma)
            Toast.makeText(this, "✓ Calibrated", Toast.LENGTH_SHORT).show()
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
        val useTls = binding.rbWss.isChecked

        savePrefs()

        webSocketManager = AirMouseWebSocket(
            onStateChange = { state, message ->
                mainHandler.post { updateConnectionState(state, message) }
            },
            onLatencyUpdate = { latencyMs ->
                mainHandler.post {
                    binding.tvLatency.text = "${latencyMs}ms"
                }
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
                binding.tvStatus.text = "Connected ✓"
                binding.btnConnect.text = "DISCONNECT"
                binding.btnConnect.isEnabled = true
                binding.btnConnect.backgroundTintList =
                    getColorStateList(android.R.color.holo_red_dark)
                isConnected = true
                gyroManager?.start(100) // Sample at 100Hz, send at sendRateHz
                startSendLoop()
                packetCount = 0
            }

            ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                binding.statusDot.setBackgroundResource(R.drawable.status_dot_disconnected)
                binding.tvStatus.text = if (state == ConnectionState.ERROR) "Error: ${message.take(40)}" else "Disconnected"
                binding.tvLatency.text = "--ms"
                binding.btnConnect.text = "CONNECT"
                binding.btnConnect.isEnabled = true
                binding.btnConnect.backgroundTintList =
                    getColorStateList(R.color.accent_green)
                isConnected = false
                gyroManager?.stop()
                stopSendLoop()
            }
        }
    }

    private fun sendGyroReading() {
        val packet = GyroPacket(latestAlpha, latestBeta, latestGamma)
        webSocketManager?.sendGyroData(packet)
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
        binding.rbWss.isChecked = prefs.getBoolean("wss", false)
        binding.rbWs.isChecked = !prefs.getBoolean("wss", false)
        binding.seekSensitivity.progress = prefs.getInt("sensitivity", 10)
        binding.seekSendRate.progress = prefs.getInt("send_rate", 5)
    }

    override fun onPause() {
        super.onPause()
        // Keep running in background if connected
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
