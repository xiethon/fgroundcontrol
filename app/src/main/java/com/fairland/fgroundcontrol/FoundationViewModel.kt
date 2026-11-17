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
    val readout = app.readout
    val maps = app.maps.state
    fun selectMap(provider: String) = app.maps.select(provider)
    fun followMap(follow: Boolean) = app.maps.follow(follow)
    fun mapLoaded(style: String?, revision: Long, success: Boolean) = app.maps.loaded(style, revision, success)
    fun saveMapKeys(tianditu: String, maptiler: String) = app.maps.saveKeys(tianditu, maptiler)
    fun importMapArchive(open: () -> InputStream?) = app.maps.importArchive(open)
    val replay = app.replay.state
    fun selectPreset(id: String) = app.selectPreset(id)
    fun replaySample() = app.replaySample()
    fun stopReplay() = app.stopReplay()
    fun importReplay(open: () -> InputStream?) = app.importReplay(open)
    fun exportLogs(open: () -> OutputStream?) = app.exportLogs(open)
}
