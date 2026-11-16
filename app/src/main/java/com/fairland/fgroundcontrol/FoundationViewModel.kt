package com.fairland.fgroundcontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import java.io.InputStream
import java.io.OutputStream

class FoundationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GroundControlApp
    val status = app.status
    val connection = app.connection.state
    val preset = app.configuredPreset
    val ready = app.connectionReady
    val replay = app.replay.state
    fun selectPreset(id: String) = app.selectPreset(id)
    fun replaySample() = app.replaySample()
    fun stopReplay() = app.stopReplay()
    fun importReplay(open: () -> InputStream?) = app.importReplay(open)
    fun exportLogs(open: () -> OutputStream?) = app.exportLogs(open)
}
