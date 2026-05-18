package com.example.gyrocursor

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var webTransportClient: WebTransportClient
    private lateinit var gyroSensorManager: GyroscopeSensorManager
    private lateinit var sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensorManager = GyroscopeSensorManager(sensorManager)
        webTransportClient = WebTransportClient()

        setContent {
            GyroCursorApp(
                webTransportClient = webTransportClient,
                gyroSensorManager = gyroSensorManager,
                context = this
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gyroSensorManager.stop()
        webTransportClient.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GyroCursorApp(
    webTransportClient: WebTransportClient,
    gyroSensorManager: GyroscopeSensorManager,
    context: Context
) {
    val isConnected = remember { mutableStateOf(false) }
    val serverIp = remember { mutableStateOf("192.168.1.") }
    val wtPort = remember { mutableStateOf("8766") }
    val latency = remember { mutableStateOf("--") }
    val tracking = remember { mutableStateOf(true) }
    val alpha = remember { mutableStateOf(0f) }
    val beta = remember { mutableStateOf(0f) }
    val gamma = remember { mutableStateOf(0f) }
    val sensitivity = remember { mutableStateOf(18f) }

    LaunchedEffect(Unit) {
        webTransportClient.setOnLatencyUpdate { lat ->
            latency.value = lat.toString()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6c63ff),
            secondary = Color(0xFF00D4FF)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0a0e27))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    "🖱️ GyroCursor",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6c63ff)
                )
                
                Text(
                    "Control your cursor with your phone",
                    fontSize = 14.sp,
                    color = Color(0xFF999),
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (!isConnected.value) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1f3a)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Connection Settings", fontWeight = FontWeight.Bold)
                            
                            OutlinedTextField(
                                value = serverIp.value,
                                onValueChange = { serverIp.value = it },
                                label = { Text("Server IP") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = wtPort.value,
                                onValueChange = { wtPort.value = it },
                                label = { Text("Port") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    webTransportClient.connect(serverIp.value, wtPort.value.toInt()) {
                                        isConnected.value = true
                                        gyroSensorManager.start { a, b, g ->
                                            alpha.value = a
                                            beta.value = b
                                            gamma.value = g
                                            if (tracking.value && isConnected.value) {
                                                webTransportClient.sendMotion(a, b, g, sensitivity.value)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1f3a)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status: Connected", color = Color(0xFF00D4FF))
                            Text("Latency: ${latency.value}ms", color = Color(0xFF6c63ff), fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1f3a)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Orientation", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("α: ${String.format("%.1f", alpha.value)}°")
                            Text("β: ${String.format("%.1f", beta.value)}°")
                            Text("γ: ${String.format("%.1f", gamma.value)}°")
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1f3a)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Sensitivity", fontWeight = FontWeight.Bold)
                            Slider(
                                value = sensitivity.value,
                                onValueChange = { sensitivity.value = it },
                                valueRange = 3f..50f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Value: ${sensitivity.value.toInt()}", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            isConnected.value = false
                            gyroSensorManager.stop()
                            webTransportClient.disconnect()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFe74c3c)
                        )
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}
