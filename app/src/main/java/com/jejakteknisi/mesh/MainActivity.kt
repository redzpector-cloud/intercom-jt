package com.jejakteknisi.mesh

import android.Manifest
import android.content.pm.PackageManager
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
    private lateinit var engine: IntercomEngine
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) engine.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = IntercomEngine(this)
        setContent { IntercomScreen(engine) }
        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else engine.start()
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }
}

@Composable
fun IntercomScreen(engine: IntercomEngine) {
    var status by remember { mutableStateOf(engine.status) }
    var connected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        engine.onStatus = {
            status = it
            connected = it.contains("TERHUBUNG")
        }
        onDispose { engine.onStatus = null }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("JEJAK TEKNISI", style = MaterialTheme.typography.headlineMedium)
                Text("MESH INTERCOM V1.1", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (connected) "🟢 TERHUBUNG" else "🟡 $status",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Mode: AUTO LOCAL")
                        Text(if (connected) "Audio real-time aktif" else "Pastikan kedua HP di Wi-Fi yang sama")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    if (connected) "🎙️ Mic ↔ 🔊 Speaker AKTIF"
                    else "🔎 MENCARI HP LAIN...",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        engine.stop()
                        engine.start()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CARI ULANG")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "V1.1 menggunakan Wi-Fi yang sama untuk tes 2 HP.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
