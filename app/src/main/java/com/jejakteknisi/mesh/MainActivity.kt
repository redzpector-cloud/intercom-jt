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
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) engine.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = IntercomEngine(this)
        setContent { IntercomScreen(engine) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            engine.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
                Text("MESH INTERCOM V1.2", style = MaterialTheme.typography.titleMedium)
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
                            if (connected) "Audio 2 arah aktif"
                            else "Hubungkan kedua HP ke Wi-Fi yang sama"
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    if (connected) "🎙️ ↔ 🔊 AUDIO AKTIF"
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

                Spacer(Modifier.height(12.dp))
                Text(
                    "V1.2: koneksi 2 HP nyata + auto reconnect.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
