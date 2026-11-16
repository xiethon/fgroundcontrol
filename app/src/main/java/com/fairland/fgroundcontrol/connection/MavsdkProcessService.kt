package com.fairland.fgroundcontrol.connection

import android.app.Service
import android.content.Intent
import android.os.*
import kotlinx.coroutines.*

/** 仅在专用进程运行。停止通过进程退出回收阻塞中的native线程。 */
class MavsdkProcessService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = LocalMavsdkEngine()
    private var observer: Job? = null
    private val endpoint = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                1 -> {
                    val client = message.replyTo ?: return
                    val token = message.arg1
                    val data = message.data
                    val preset = ConnectionPreset(data.getString("id") ?: "", data.getString("label") ?: "", data.getInt("local"), data.getString("host") ?: "", data.getInt("remote"))
                    observer?.cancel()
                    observer = scope.launch {
                        engine.state.collect { state ->
                            try { client.send(Message.obtain(null, 3, token, 0).apply { this.data = Bundle().apply { putString("state", state) } }) }
                            catch (_: RemoteException) { terminate() }
                        }
                    }
                    engine.connect(preset)
                }
                2 -> terminate()
            }
        }
    })
    override fun onBind(intent: Intent?): IBinder = endpoint.binder
    override fun onUnbind(intent: Intent?): Boolean { terminate(); return false }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun terminate() {
        check(android.app.Application.getProcessName().endsWith(":mavsdk"))
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
