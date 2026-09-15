package com.jejakteknisi.mesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { Screen() }
        askAudio()
    }
    private fun askAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }
}

@Composable
fun Screen() {
    var server by remember { mutableStateOf("wss://YOUR-SERVER.example/ws") }
    var room by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(IntercomService.engine?.status ?: "Siap — masukkan kode ruang") }
    DisposableEffect(Unit) {
        IntercomService.engine?.onStatus = { status = it }
        onDispose { IntercomService.engine?.onStatus = null }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("JEJAK TEKNISI", style = MaterialTheme.typography.headlineMedium)
                Text("INTERNET INTERCOM V2.0", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(status, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(), label = { Text("Server WebSocket") }, singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(room, { room = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Kode Ruang") }, placeholder = { Text("Contoh: JT1234") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                        Spacer(Modifier.height(8.dp))
                        Text("Kedua HP cukup memakai Wi-Fi atau data seluler dan memasukkan kode ruang yang sama.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.weight(1f))
                val context = androidx.compose.ui.platform.LocalContext.current
                Button(onClick = {
                    IntercomService.serverUrl = server
                    IntercomService.roomCode = room
                    ContextCompat.startForegroundService(context, Intent(context, IntercomService::class.java))
                }, enabled = room.length >= 4 && (server.startsWith("ws://") || server.startsWith("wss://")), modifier = Modifier.fillMaxWidth()) { Text("MULAI INTERKOM") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.startService(Intent(context, IntercomService::class.java).setAction(IntercomService.ACTION_STOP)) }, modifier = Modifier.fillMaxWidth()) { Text("PUTUSKAN") }
            }
        }
    }
}
