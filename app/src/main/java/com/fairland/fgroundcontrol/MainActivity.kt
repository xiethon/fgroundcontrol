package com.fairland.fgroundcontrol

import android.os.Bundle
import com.fairland.fgroundcontrol.connection.ConnectionPresets
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val resolver = applicationContext.contentResolver
        val app = application as GroundControlApp
        val model = androidx.lifecycle.ViewModelProvider(this)[FoundationViewModel::class.java]
        try { startForegroundService(android.content.Intent(this, ConnectionService::class.java)) }
        catch (error: Exception) { app.reportServiceError(error) }
        val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) model.exportLogs { resolver.openOutputStream(uri) }
        }
        val importReplay = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) model.importReplay { resolver.openInputStream(uri) }
        }
        val importMapArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) model.importMapArchive { resolver.openInputStream(uri) }
        }
        setContent {
            val status = model.status.collectAsStateWithLifecycle()
            val connection = model.connection.collectAsStateWithLifecycle()
            val savedPreset = model.preset.collectAsStateWithLifecycle()
            val replay = model.replay.collectAsStateWithLifecycle()
            val ready = model.ready.collectAsStateWithLifecycle()
            val selected = savedPreset.value
            var menuOpen by remember { mutableStateOf(false) }
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.readonly_title))
                    Text(stringResource(R.string.storage_status, statusLabel(status.value)))
                    Text(stringResource(R.string.connection_status, connectionLabel(connection.value)))
                    Text(stringResource(if (selected.id == "sitl") R.string.simulation_connection else R.string.device_connection))
                    Box {
                        OutlinedButton(enabled = ready.value, onClick = { menuOpen = true }) { Text(selected.label) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            ConnectionPresets.all.forEach { preset ->
                                DropdownMenuItem(text = { Text(preset.label) }, onClick = { model.selectPreset(preset.id); menuOpen = false })
                            }
                        }
                    }
                    com.fairland.fgroundcontrol.maps.ReadOnlyPanel(model,
                        { importMapArchive.launch(arrayOf("application/octet-stream", "application/vnd.pmtiles", "*/*")) })
                    Text(stringResource(R.string.replay_status, replayLabel(replay.value.phase), replay.value.index, replay.value.count))
                    replay.value.sample?.let { Text("${it.offsetMs} ms · ${it.latitude}, ${it.longitude}") }
                    Button(onClick = { importReplay.launch(arrayOf("text/*", "application/octet-stream")) }) { Text(stringResource(R.string.import_replay)) }
                    Button(onClick = { model.stopReplay() }) { Text(stringResource(R.string.stop_replay)) }
                    Button(onClick = { model.replaySample() }) { Text(stringResource(R.string.replay)) }
                    Button(onClick = { export.launch("fgroundcontrol-logs.zip") }) { Text(stringResource(R.string.export_logs)) }
                }
            } }
        }
    }
}

@Composable
private fun connectionLabel(value: String): String = stringResource(when (value) {
    "connecting" -> R.string.state_connecting
    "connected" -> R.string.state_connected
    "disconnected" -> R.string.state_disconnected
    else -> R.string.state_connection_error
})

@Composable
private fun replayLabel(value: String): String = stringResource(when (value) {
    "playing" -> R.string.state_playing
    "completed" -> R.string.state_completed
    "stopped" -> R.string.state_stopped
    else -> R.string.state_idle
})

@Composable
private fun statusLabel(value: String): String = stringResource(when {
    value == "initializing" -> R.string.status_initializing
    value == "ready" -> R.string.status_ready
    value == "exported" -> R.string.status_exported
    value.startsWith("simulation:") -> R.string.status_simulation
    value.startsWith("log queue full:") -> R.string.status_log_full
    value.startsWith("log error:") -> R.string.status_log_error
    value.startsWith("replay error:") -> R.string.status_replay_error
    value.startsWith("connection error:") || value.startsWith("service error:") -> R.string.state_connection_error
    else -> R.string.status_storage_error
})
