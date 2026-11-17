package com.fairland.fgroundcontrol.connection

import android.content.*
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 控制面在主线程串行；原生SDK在独立进程，无飞行命令。 */
class MavsdkConnection(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow("disconnected")
    val state = mutableState.asStateFlow()
    private val mutableTelemetry = MutableStateFlow(com.fairland.fgroundcontrol.telemetry.VehicleTelemetry())
    val telemetry = mutableTelemetry.asStateFlow()
    private var desired: ConnectionPreset? = null
    private var token = 0
    private var binding: ServiceConnection? = null
    private var endpoint: Messenger? = null
    private var stopping = false
    private val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == 4 && message.arg1 == token && !stopping && mutableTelemetry.value.connected) {
                var next = mutableTelemetry.value
                message.data.keySet().forEach { topic ->
                    message.data.getBundle(topic)?.let { entry ->
                        next = next.accept(token, topic, com.fairland.fgroundcontrol.telemetry.Reading(
                            entry.getDoubleArray("values")?.map { it.takeIf(Double::isFinite) } ?: emptyList(), entry.getString("text"), entry.getLong("at")))
                    }
                }
                mutableTelemetry.value = next
            }
            if (message.what == 3 && message.arg1 == token && !stopping && desired != null) {
                publish(message.data.getString("state") ?: "error: missing SDK state")
            }
        }
    })
    fun connect(preset: ConnectionPreset) = main.post {
        ++token
        desired = preset
        publish("connecting")
        if (endpoint != null) requestStop() else if (binding == null) bind()
    }
    fun disconnect() = main.post {
        ++token
        desired = null
        if (endpoint != null) requestStop()
        publish("disconnected")
    }
    private fun publish(state: String) {
        mutableState.value = state
        mutableTelemetry.value = if (state == "connected") mutableTelemetry.value.copy(session = token, connected = true)
            else com.fairland.fgroundcontrol.telemetry.VehicleTelemetry(session = token)
        android.util.Log.i("FgcConnection", "session=$token state=$state")
    }
    private fun requestStop() {
        if (stopping) return
        stopping = true
        try { endpoint?.send(Message.obtain(null, 2)) }
        catch (_: RemoteException) { recycle() }
    }
    private fun bind() {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binding !== this) return
                endpoint = Messenger(binder)
                val preset = desired
                if (preset == null) { requestStop(); return }
                val start = Message.obtain(null, 1, token, 0).apply {
                    replyTo = receiver
                    data = Bundle().apply {
                        putString("id", preset.id); putString("label", preset.label)
                        putInt("local", preset.localPort); putString("host", preset.remoteHost); putInt("remote", preset.remotePort)
                    }
                }
                try { endpoint!!.send(start) } catch (_: RemoteException) { recycle() }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (binding === this) recycle() }
            override fun onBindingDied(name: ComponentName) { if (binding === this) recycle() }
            override fun onNullBinding(name: ComponentName) { if (binding === this) { desired = null; recycle(); publish("error: SDK binding rejected") } }
        }
        binding = connection
        if (!context.bindService(Intent(context, MavsdkProcessService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            binding = null
            publish("error: SDK process unavailable")
        }
    }
    private fun recycle() {
        if (desired != null) { ++token; publish("connecting") }
        binding?.let { context.unbindService(it) }
        binding = null; endpoint = null; stopping = false
        if (desired != null) main.postDelayed({ if (desired != null && binding == null) bind() }, 300)
    }
}
