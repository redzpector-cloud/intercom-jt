package com.jejakteknisi.mesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startIntercomService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IntercomScreen() }
        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }

        if (needed.isEmpty()) startIntercomService()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startIntercomService() {
        val intent = Intent(this, IntercomService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, IntercomService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }
}

@Composable
fun IntercomScreen() {
    var status by remember { mutableStateOf("Menyiapkan interkom...") }
    var connected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val engine = IntercomService.engine
        if (engine != null) {
            status = engine.status
            connected = engine.status.contains("TERHUBUNG")
            engine.onStatus = {
                status = it
                connected = it.contains("TERHUBUNG")
            }
        }
        onDispose {
            IntercomService.engine?.onStatus = null
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("JEJAK TEKNISI", style = MaterialTheme.typography.headlineMedium)
                Text("MESH INTERCOM V1.5", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (connected) "🟢 TERHUBUNG" else "🟡 $status",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Mode: AUTO LOCAL")
                        Text(
                            if (connected) "🎙️ ↔ 🔊 Audio aktif di latar belakang"
                            else "Mencari HP lain..."
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "LAYAR MATI / MINIMIZE",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Interkom tetap berjalan melalui foreground service.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("INTERKOM AKTIF")
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "V1.5: tetap aktif saat aplikasi diminimalkan dan layar mati.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

