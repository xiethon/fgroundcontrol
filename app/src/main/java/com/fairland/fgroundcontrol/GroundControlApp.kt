package com.fairland.fgroundcontrol

import android.app.Application
import com.fairland.fgroundcontrol.connection.ConnectionPresets
import com.fairland.fgroundcontrol.data.FoundationStore
import com.fairland.fgroundcontrol.data.LogStore
import com.fairland.fgroundcontrol.data.LogKind
import kotlinx.coroutines.*
import java.io.OutputStream
import java.util.UUID
import com.fairland.fgroundcontrol.data.ReplaySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GroundControlApp : Application() {
    val connection by lazy { com.fairland.fgroundcontrol.connection.DirectMavlinkConnection(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val maps by lazy { com.fairland.fgroundcontrol.maps.MapRepository(filesDir.resolve("maps"), scope, BuildConfig.TIANDITU_TOKEN, BuildConfig.MAPTILER_KEY) }
    val readout by lazy { com.fairland.fgroundcontrol.telemetry.TelemetryMonitor(connection.telemetry, scope,
        { record(LogKind.APP, it) }, { record(LogKind.FLIGHT, "${mutablePreset.value.id}: $it") }).state }
    val replay = com.fairland.fgroundcontrol.data.ReplayPlayer(scope)
    private val logs by lazy { LogStore(filesDir.resolve("logs")) }
    private val events = com.fairland.fgroundcontrol.data.LogEventQueue()
    private val session = UUID.randomUUID().toString()
    private val mutableStatus = MutableStateFlow("initializing")
    val status = mutableStatus.asStateFlow()
    private val mutablePreset = MutableStateFlow(ConnectionPresets.default)
    val configuredPreset = mutablePreset.asStateFlow()
    @Volatile private var connectionActive = false
    private val configurationLock = Mutex()
    private val mutableConnectionReady = MutableStateFlow(false)
    val connectionReady = mutableConnectionReady.asStateFlow()
    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("FgcBuild", BuildConfig.BUILD_IDENTITY)
        readout
        maps
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(com.fairland.fgroundcontrol.data.CrashHandler(
            record = { error -> logs.append(LogKind.CRASH, session, error.stackTraceToString()) },
            previous = previous,
            diagnose = { android.util.Log.e("FGroundControl", "Crash log write failed") }
        ))
        scope.launch {
            while (isActive) {
                val (kind, message) = events.take()
                try { logs.append(kind, session, message) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutableStatus.value = "log error: ${e.javaClass.simpleName}"
                }
            }
        }
        scope.launch {
            configurationLock.withLock {
                try {
                    record(LogKind.APP, "startup")
                    val store = FoundationStore(filesDir.resolve("foundation"))
                    mutablePreset.value = ConnectionPresets.resolve(store.read("udp-preset"))
                    store.write("startup", "schema=1\n")
                    check(store.read("startup") == "schema=1\n")
                    mutableStatus.value = "ready"
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutableStatus.value = "error: ${e.javaClass.simpleName}"
                }
                mutableConnectionReady.value = true
            }
        }
        scope.launch {
            connection.state.collectLatest { state ->
                if (state.startsWith("error:")) {
                    delay(3000)
                    configurationLock.withLock {
                        synchronized(this@GroundControlApp) {
                            if (connectionActive && mutableConnectionReady.value && connection.state.value == state) connection.connect(mutablePreset.value)
                        }
                    }
                }
            }
        }
    }
    @Synchronized fun startConnection() {
        if (connectionActive) return
        connectionActive = true
        scope.launch {
            connectionReady.first { it }
            configurationLock.withLock {
                synchronized(this@GroundControlApp) { if (connectionActive) connection.connect(mutablePreset.value) }
            }
        }
    }
    @Synchronized fun stopConnection() {
        connectionActive = false
        connection.disconnect()
    }
    fun reportServiceError(error: Exception) { mutableStatus.value = "service error: ${error.javaClass.simpleName}" }
    fun selectPreset(presetId: String) {
        val preset = ConnectionPresets.all.firstOrNull { it.id == presetId } ?: return
        scope.launch {
            configurationLock.withLock {
                if (mutablePreset.value == preset) return@withLock
                try {
                    FoundationStore(filesDir.resolve("foundation")).write("udp-preset", preset.id)
                    mutablePreset.value = preset
                    synchronized(this@GroundControlApp) { if (connectionActive) connection.connect(preset) }
                    record(LogKind.APP, "UDP preset ${preset.id} connection requested")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    mutableStatus.value = "connection error: ${e.javaClass.simpleName}"
                }
            }
        }
    }
    fun replaySample() {
        val token = replay.beginLoad()
        scope.launch {
            try {
                val samples = ReplaySource().read(sequenceOf("0,30,120", "1000,30.0001,120.0001"))
                if (!replay.playIfCurrent(token, samples)) return@launch
                samples.forEach { record(LogKind.FLIGHT, "SIMULATION ${it.offsetMs},${it.latitude},${it.longitude}") }
                mutableStatus.value = "simulation: ${samples.size} samples (offline)"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableStatus.value = "replay error: ${e.javaClass.simpleName}"
            }
        }
    }
    fun importReplay(openInput: () -> java.io.InputStream?) {
        val token = replay.beginLoad()
        scope.launch {
            try {
                val samples = checkNotNull(openInput()) { "Replay file unavailable" }.bufferedReader().use { ReplaySource().read(it) }
                if (!replay.playIfCurrent(token, samples)) return@launch
                record(LogKind.APP, "SIMULATION file replay started: ${samples.size} samples")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableStatus.value = "replay error: ${e.javaClass.simpleName}"
            }
        }
    }
    fun stopReplay() = replay.stop()
    fun exportLogs(openOutput: () -> OutputStream?) {
        scope.launch {
            var temp: java.io.File? = null
            try {
                temp = java.io.File.createTempFile("logs", ".zip", cacheDir)
                logs.export(temp)
                checkNotNull(openOutput()) { "Export destination unavailable" }.use { output -> temp.inputStream().use { it.copyTo(output) } }
                mutableStatus.value = "exported"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableStatus.value = "export error: ${e.javaClass.simpleName}"
            } finally { temp?.delete() }
        }
    }
    fun recordPlanningFailure(operation: String, error: Exception) {
        record(LogKind.APP, "field-planning $operation: ${error.javaClass.simpleName}: ${error.message}")
    }
    private fun record(kind: LogKind, message: String) {
        if (!events.offer(kind, message)) {
            mutableStatus.value = "log queue full: event dropped (${kind.name})"
        }
    }
}
