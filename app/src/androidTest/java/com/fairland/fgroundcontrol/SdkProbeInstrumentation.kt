package com.fairland.fgroundcontrol

import android.app.Instrumentation
import android.os.Bundle
import com.fairland.fgroundcontrol.connection.ConnectionPreset
import com.fairland.fgroundcontrol.connection.MavsdkConnection
import java.net.DatagramSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 不启动Activity，不解锁、不上传或发送飞行命令。 */
class SdkProbeInstrumentation : Instrumentation() {
    private var includeSitl = false
    private var lifecycle = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); includeSitl = arguments?.getString("sitl") == "true"; lifecycle = arguments?.getString("lifecycle") == "true"; start() }
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
            val adapter = MavsdkConnection(targetContext)
            adapter.connect(ConnectionPreset("probe1", "probe1", first, "127.0.0.1", first))
            await { bound(first) }
            adapter.connect(ConnectionPreset("probe2", "probe2", second, "127.0.0.1", second))
            await { !bound(first) && bound(second) }
            result.putLong("switch_without_vehicle_ms", android.os.SystemClock.elapsedRealtime() - started)
            adapter.disconnect()
            await { adapter.state.value == "disconnected" && !bound(second) }
            if (includeSitl) {
                adapter.connect(com.fairland.fgroundcontrol.connection.ConnectionPresets.resolve("sitl"))
                await { adapter.state.value == "connected" }
                result.putString("sitl", "PASS: SDK connectionState=connected")
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
            result.putString("result", "PASS: isolated no-vehicle start, switch, disconnect, port release")
            finish(-1, result)
        } catch (error: Exception) {
            result.putString("result", "FAIL: ${error.javaClass.simpleName}: ${error.message}")
            finish(0, result)
        } finally { worker.shutdownNow() }
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
