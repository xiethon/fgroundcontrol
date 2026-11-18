package com.fairland.fgroundcontrol

import android.app.Instrumentation
import android.os.Bundle
import com.fairland.fgroundcontrol.connection.ConnectionPreset
import com.fairland.fgroundcontrol.connection.DirectMavlinkConnection
import java.net.DatagramSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 不启动Activity，不解锁、不上传或发送飞行命令。 */
class SdkProbeInstrumentation : Instrumentation() {
    private var includeSitl = false
    private var lifecycle = false
    private var protocol = false
    private var stableRestart = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); stableRestart = arguments?.getString("stable_restart") == "true"; protocol = arguments?.getString("protocol") == "true"; includeSitl = arguments?.getString("sitl") == "true"; lifecycle = arguments?.getString("lifecycle") == "true"; start() }
    override fun onStart() {
        val result = Bundle()
        result.putLong("page_size", android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE))
        val worker = Executors.newSingleThreadExecutor()
        try {
            val dataRoot = java.io.File(targetContext.cacheDir, "m1-device-probe")
            dataRoot.deleteRecursively()
            try {
                val store = com.fairland.fgroundcontrol.data.FoundationStore(java.io.File(dataRoot, "records"))
                store.write("probe", "设备离线记录")
                check(com.fairland.fgroundcontrol.data.FoundationStore(java.io.File(dataRoot, "records")).read("probe") == "设备离线记录")
                val logs = com.fairland.fgroundcontrol.data.LogStore(java.io.File(dataRoot, "logs"))
                com.fairland.fgroundcontrol.data.LogKind.entries.forEach { logs.append(it, "probe", "device validation") }
                val zip = java.io.File(dataRoot, "export.zip")
                logs.export(zip)
                java.util.zip.ZipFile(zip).use { archive ->
                    check(archive.getEntry("manifest.txt") != null)
                    com.fairland.fgroundcontrol.data.LogKind.entries.forEach { check(archive.getEntry("${it.name}.log") != null) }
                }
                result.putString("storage_logs", "PASS: atomic reopen, three log kinds, ZIP export")
            } finally { dataRoot.deleteRecursively() }
            val started = android.os.SystemClock.elapsedRealtime()
            val first = DatagramSocket(0).use { it.localPort }
            val second = DatagramSocket(0).use { it.localPort }
            val adapter = DirectMavlinkConnection(targetContext)
            adapter.connect(ConnectionPreset("probe1", "probe1", first, "127.0.0.1", first))
            await { bound(first) }
            adapter.connect(ConnectionPreset("probe2", "probe2", second, "127.0.0.1", second))
            await { !bound(first) && bound(second) }
            result.putLong("switch_without_vehicle_ms", android.os.SystemClock.elapsedRealtime() - started)
            adapter.disconnect()
            await { adapter.state.value == "disconnected" && !bound(second) }
            if (stableRestart) result.putString("stable_restart", ProtocolProbe.stableRestart(adapter))
            if (protocol) result.putString("protocol", ProtocolProbe.run(adapter))
            if (includeSitl) {
                adapter.connect(com.fairland.fgroundcontrol.connection.ConnectionPresets.resolve("sitl"))
                await { adapter.state.value == "connected" }
                await { adapter.telemetry.value.quality("position", android.os.SystemClock.elapsedRealtime()) == com.fairland.fgroundcontrol.telemetry.Quality.VALID }
                await { adapter.telemetry.value.quality("battery", android.os.SystemClock.elapsedRealtime()) == com.fairland.fgroundcontrol.telemetry.Quality.VALID }
                val snapshot = adapter.telemetry.value
                check(snapshot.readings.getValue("position").values[0]!! in -90.0..90.0)
                check(snapshot.readings.getValue("battery").values[0]!! in 0.0..100.0)
                result.putString("sitl", "PASS: connected, live position and battery, declared units valid")
                adapter.disconnect()
                await { !bound(14540) }
            }
            if (lifecycle) {
                val app = targetContext.applicationContext as GroundControlApp
                val intent = android.content.Intent(targetContext, ConnectionService::class.java)
                targetContext.startForegroundService(intent)
                val manager = targetContext.getSystemService(android.app.ActivityManager::class.java)
                await { manager.getRunningServices(20).any { it.service.className == ConnectionService::class.java.name && it.foreground } }
                val notifications = targetContext.getSystemService(android.app.NotificationManager::class.java)
                if (notifications.areNotificationsEnabled()) {
                    check(notifications.activeNotifications.any { it.id == 1 })
                    result.putString("notification_permission", "GRANTED: foreground notification present")
                } else {
                    result.putString("notification_permission", "DENIED: foreground service remains active without drawer notification")
                }
                val servicePort = app.configuredPreset.value.localPort
                await { bound(servicePort) }
                val processName = targetContext.packageName + ":mavsdk"
                fun childPid() = manager.runningAppProcesses?.firstOrNull { it.processName == processName }?.pid
                val previousPid = checkNotNull(childPid())
                android.os.Process.killProcess(previousPid)
                await { childPid()?.let { it != previousPid } == true && bound(servicePort) }
                targetContext.startForegroundService(intent)
                check(childPid() != null)
                check(targetContext.stopService(intent))
                await { app.connection.state.value == "disconnected" && !bound(servicePort) }
                result.putString("lifecycle", "PASS: foreground notification, child crash recovery, repeated start, service stop releases port")
            }
            runOnMainSync {
                org.maplibre.android.MapLibre.getInstance(targetContext)
                org.maplibre.android.LibraryLoader.load()
            }
            result.putString("map_native", "PASS: MapLibre native library loaded; no MapView created")
            val mapRoot = java.io.File(targetContext.cacheDir, "m2-map-probe")
            mapRoot.deleteRecursively()
            val mapScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            try {
                val repository = com.fairland.fgroundcontrol.maps.MapRepository(mapRoot, mapScope, "config_file_token_123", "config_file_key_456")
                await { repository.state.value.style?.contains("config_file_token_123") == true }
                repository.select("maptiler")
                await { repository.state.value.style?.contains("config_file_key_456") == true }
                repository.select("tianditu")
                await { repository.state.value.provider == "tianditu" }
                repository.saveKeys("local_test_key_123", "local_test_key_456")
                await { repository.state.value.style?.contains("local_test_key_123") == true }
                check(repository.state.value.style!!.contains("img_w"))
                repository.select("maptiler")
                await { repository.state.value.provider == "maptiler" }
                check(repository.state.value.remoteStyle)
                val obsoleteRevision = repository.state.value.revision
                repository.saveKeys("  replacement_key_789  ", "")
                val keyStore = com.fairland.fgroundcontrol.data.FoundationStore(mapRoot)
                await { keyStore.read("map-keys")?.contains("replacement_key_789") == true }
                val savedKeys = org.json.JSONObject(keyStore.read("map-keys")!!)
                check(savedKeys.getString("tiandituKey") == "replacement_key_789")
                check(savedKeys.getString("maptilerKey") == "local_test_key_456")
                val reopened = com.fairland.fgroundcontrol.maps.MapRepository(mapRoot, mapScope)
                await { reopened.state.value.provider == "maptiler" && reopened.state.value.style != null }
                await { repository.state.value.revision != obsoleteRevision }
                val oldStyle = repository.state.value.style
                kotlinx.coroutines.runBlocking {
                    repository.loaded(oldStyle, repository.state.value.revision, true).join()
                    repository.loaded(oldStyle, obsoleteRevision, false).join()
                }
                check(repository.state.value.status == "ready")
                repository.saveKeys("bad key", "")
                await { repository.state.value.status == "error" }
                check(repository.state.value.style == oldStyle)
                repository.importArchive { java.io.ByteArrayInputStream(rasterFixture()) }
                await { repository.state.value.provider == "offline" && repository.state.value.style != null }
                val firstArchiveStyle = repository.state.value.style
                check(org.json.JSONObject(firstArchiveStyle!!).getJSONObject("sources").getJSONObject("offline").getString("url").startsWith("pmtiles://file:///"))
                check(repository.state.value.attribution == "FGC synthetic format test")
                repository.importArchive { java.io.ByteArrayInputStream(rasterFixture()) }
                await { repository.state.value.style != firstArchiveStyle }
                val retainedStyle = repository.state.value.style
                repository.importArchive { java.io.ByteArrayInputStream(ByteArray(12)) }
                await { repository.state.value.status == "error" }
                check(repository.state.value.style == retainedStyle)
                val selectionBefore = keyStore.read("map-selection")
                val blockedWrite = java.io.File(mapRoot, "map-selection.txt.tmp")
                check(blockedWrite.mkdir())
                java.io.File(blockedWrite, "keep").writeText("block atomic write")
                repository.loaded(retainedStyle, repository.state.value.revision, true)
                await { repository.state.value.status == "ready" }
                repository.select("tianditu")
                await { repository.state.value.status == "error" }
                check(repository.state.value.provider == "offline" && repository.state.value.style == retainedStyle)
                val importing = java.util.concurrent.CountDownLatch(1)
                repository.importArchive { importing.countDown(); java.io.ByteArrayInputStream(rasterFixture()) }
                check(importing.await(5, java.util.concurrent.TimeUnit.SECONDS))
                await { repository.state.value.status == "error" }
                check(repository.state.value.provider == "offline" && repository.state.value.style == retainedStyle)
                check(keyStore.read("map-selection") == selectionBefore)
                val afterFailure = com.fairland.fgroundcontrol.maps.MapRepository(mapRoot, mapScope)
                await { afterFailure.state.value.provider == "offline" && afterFailure.state.value.style != null }
                check(afterFailure.state.value.style == retainedStyle)
                blockedWrite.deleteRecursively()
                result.putString("map_config", "PASS: local keys, source selection, raster PMTiles import/replacement, invalid import rollback; no provider requests")
            } finally { mapScope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); mapRoot.deleteRecursively() }
            result.putString("result", "PASS: isolated no-vehicle start, switch, disconnect, port release")
            finish(-1, result)
        } catch (error: Exception) {
            result.putString("result", "FAIL: ${error.javaClass.simpleName}: ${error.message}")
            result.putString("failure_trace", error.stackTraceToString().take(1800))
            finish(0, result)
        } finally { worker.shutdownNow() }
    }
    private fun rasterFixture(): ByteArray {
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNc0AAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
        val metadata = "{\"attribution\":\"FGC synthetic format test\"}".toByteArray()
        val directory = byteArrayOf(1, 0, 1, png.size.toByte(), 1)
        val bytes = ByteArray(127 + directory.size + metadata.size + png.size)
        "PMTiles".toByteArray().copyInto(bytes); bytes[7] = 3
        val header = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.putLong(8, 127); header.putLong(16, directory.size.toLong())
        header.putLong(24, 127L + directory.size); header.putLong(32, metadata.size.toLong())
        header.putLong(56, 127L + directory.size + metadata.size); header.putLong(64, png.size.toLong())
        header.putLong(72, 1); header.putLong(80, 1); header.putLong(88, 1)
        bytes[96] = 1; bytes[97] = 1; bytes[98] = 1; bytes[99] = 2
        header.putInt(102, -1800000000); header.putInt(106, -850000000); header.putInt(110, 1800000000); header.putInt(114, 850000000)
        directory.copyInto(bytes, 127); metadata.copyInto(bytes, 127 + directory.size); png.copyInto(bytes, 127 + directory.size + metadata.size)
        return bytes
    }
    private fun bound(port: Int): Boolean = try { DatagramSocket(port).use { }; false } catch (_: java.net.BindException) { true }
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < end) { "Timed out waiting for connection lifecycle" }
            Thread.sleep(50)
        }
    }
}
