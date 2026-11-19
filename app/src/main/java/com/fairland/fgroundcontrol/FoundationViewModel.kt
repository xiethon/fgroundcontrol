package com.fairland.fgroundcontrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.InputStream
import java.io.OutputStream

class FoundationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GroundControlApp
    // 相机初始化按连接会话记忆，切换地图源不会重复抢回视角。
    var mapCenteredSession: Int? = null
    val boundaryCenter = kotlinx.coroutines.flow.MutableStateFlow<com.fairland.fgroundcontrol.mission.BoundaryPoint?>(null)
    val boundary = com.fairland.fgroundcontrol.mission.BoundaryEditor(
        { com.fairland.fgroundcontrol.mission.BoundaryDraftStore(com.fairland.fgroundcontrol.data.FoundationStore(application.filesDir.resolve("boundary"))) }, viewModelScope)
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
