package com.fairland.fgroundcontrol.connection

import io.mavsdk.MavsdkEventQueue
import io.mavsdk.mavsdkserver.MavsdkServer
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 仅连接原型，无飞行命令。由进程容器持有，页面不能创建独立实例。 */
internal class LocalMavsdkEngine {
    private val mutableState = MutableStateFlow("disconnected")
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var server: MavsdkServer? = null
    private var system: io.mavsdk.System? = null
    private var bridge: UdpPresetBridge? = null
    private var subscription: Disposable? = null

    fun connect(preset: ConnectionPreset) {
        MavsdkEventQueue.executor().execute {
            release()
            val current = ++generation
            mutableState.value = "connecting"
            try {
                val transport = UdpPresetBridge(preset) { error ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) {
                            mutableState.value = "error: ${error.javaClass.simpleName}"
                            release()
                        }
                    }
                }
                bridge = transport
                val sdkPort = java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
                transport.start(sdkPort)
                val nativeServer = MavsdkServer()
                server = nativeServer
                val rpcPort = nativeServer.run("udpin://127.0.0.1:$sdkPort", 0)
                check(rpcPort > 0) { "MAVSDK server did not start" }
                val client = io.mavsdk.System("127.0.0.1", rpcPort)
                system = client
                subscription = client.core.connectionState.subscribe({ connection ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) mutableState.value = if (connection.isConnected) "connected" else "disconnected"
                    }
                }, { error ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) {
                            mutableState.value = "error: ${error.javaClass.simpleName}"
                            release()
                        }
                    }
                })
            } catch (error: Exception) {
                mutableState.value = "error: ${error.javaClass.simpleName}"
                release()
            } catch (error: UnsatisfiedLinkError) {
                mutableState.value = "unsupported ABI"
                release()
            }
        }
    }
    fun disconnect() {
        MavsdkEventQueue.executor().execute { release(); mutableState.value = "disconnected" }
    }
    private fun release() {
        ++generation
        bridge?.close(); bridge = null
        subscription?.dispose(); subscription = null
        system?.dispose(); system = null
        server?.stop(); server?.destroy(); server = null
    }
}
