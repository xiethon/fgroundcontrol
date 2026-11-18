package com.fairland.fgroundcontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fairland.fgroundcontrol.ui.theme.AppSpace
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fairland.fgroundcontrol.FoundationViewModel
import com.fairland.fgroundcontrol.R
import com.fairland.fgroundcontrol.connection.ConnectionPresets
import com.fairland.fgroundcontrol.maps.ReadOnlyPanel

/** 覆盖地图的独立配置面板，不创建第二个MapView或飞控连接。 */
@Composable
fun SettingsPanel(model: FoundationViewModel, onBack: () -> Unit, importArchive: () -> Unit,
    importReplay: () -> Unit, exportLogs: () -> Unit) {
    BackHandler(onBack = onBack)
    val preset by model.preset.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val replay by model.replay.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val replayPhase = stringResource(when (replay.phase) {
        "playing" -> R.string.state_playing; "completed" -> R.string.state_completed; "stopped" -> R.string.state_stopped; else -> R.string.state_idle
    })
    var presetsOpen by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(AppSpace.md).verticalScroll(rememberScrollState())) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.top_back_map)) }
            Text(stringResource(R.string.top_settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.top_diagnostics, status))
            Box {
                OutlinedButton(enabled = ready, onClick = { presetsOpen = true }) { Text(preset.label) }
                DropdownMenu(expanded = presetsOpen, onDismissRequest = { presetsOpen = false }) {
                    ConnectionPresets.all.forEach { item -> DropdownMenuItem(text = { Text(item.label) },
                        onClick = { model.selectPreset(item.id); presetsOpen = false }) }
                }
            }
            ReadOnlyPanel(model, importArchive, showMap = false)
            HorizontalDivider()
            Text(stringResource(R.string.replay_status, replayPhase, replay.index, replay.count))
            OutlinedButton(onClick = importReplay) { Text(stringResource(R.string.import_replay)) }
            OutlinedButton(onClick = model::replaySample) { Text(stringResource(R.string.replay)) }
            OutlinedButton(onClick = model::stopReplay) { Text(stringResource(R.string.stop_replay)) }
            OutlinedButton(onClick = exportLogs) { Text(stringResource(R.string.export_logs)) }
        }
    }
}
