package com.jejakteknisi.mesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class Peer(val name: String, val status: String)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        setContent { IntercomScreen() }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun IntercomScreen() {
    var connected by remember { mutableStateOf(false) }
    var micOn by remember { mutableStateOf(false) }
    val peers = remember {
        mutableStateListOf(
            Peer("HP-01", "Siap mencari"),
            Peer("HP-02", "Siap mencari")
        )
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("JEJAK TEKNISI", style = MaterialTheme.typography.headlineMedium)
                Text("MESH INTERCOM V1", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (connected) "🟢 TERHUBUNG" else "🟡 SIAP TERHUBUNG",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Mode: AUTO LOCAL")
                        Text("V1: 2 HP • audio engine siap dikembangkan")
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("ANGGOTA", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(peers) { peer ->
                        ListItem(
                            headlineContent = { Text(peer.name) },
                            supportingContent = { Text(peer.status) }
                        )
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { connected = !connected },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (connected) "PUTUSKAN" else "CARI & HUBUNGKAN")
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { micOn = !micOn },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = connected
                ) {
                    Text(if (micOn) "🎙️ MICROPHONE AKTIF" else "🎙️ BICARA")
                }
            }
        }
    }
}
