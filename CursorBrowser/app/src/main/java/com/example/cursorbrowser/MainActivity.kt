package com.example.cursorbrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BrowserApp()
        }
    }
}

@Composable
fun BrowserApp() {
    val isConnected = remember { mutableStateOf(false) }
    val serverIp = remember { mutableStateOf("192.168.1.") }
    val port = remember { mutableStateOf("8765") }
    val webUrl = remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1a1a1a)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F0F),
                            Color(0xFF1a1a1a)
                        )
                    )
                )
        ) {
            if (!isConnected.value) {
                LandingScreen(
                    ip = serverIp.value,
                    port = port.value,
                    onIpChange = { serverIp.value = it },
                    onPortChange = { port.value = it },
                    onConnect = {
                        webUrl.value = "https://${serverIp.value}:${port.value}"
                        isConnected.value = true
                    }
                )
            } else {
                BrowserScreen(
                    url = webUrl.value,
                    onDisconnect = {
                        isConnected.value = false
                        webUrl.value = ""
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    ip: String,
    port: String,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "🌐",
            fontSize = 80.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            "Cursor Browser",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "Control your cursor wirelessly",
            fontSize = 16.sp,
            color = Color(0xFFB0B0B0),
            modifier = Modifier.padding(bottom = 48.dp),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("IP Address", color = Color(0xFF888888)) },
            placeholder = { Text("192.168.1.100") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp)),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp)
        )

        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Port", color = Color(0xFF888888)) },
            placeholder = { Text("8765") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(12.dp)),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp)
        )

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2196F3)
            ),
            enabled = ip.isNotBlank() && port.isNotBlank()
        ) {
            Text(
                "CONNECT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            "Enter your server IP and port to get started",
            fontSize = 12.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    url: String,
    onDisconnect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5252)
            )
        ) {
            Text("Disconnect", fontSize = 12.sp, color = Color.White)
        }
    }
}