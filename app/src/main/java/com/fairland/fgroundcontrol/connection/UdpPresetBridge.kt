package com.fairland.fgroundcontrol.connection

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** 仅转发数据报，MAVLink 仍由 MAVSDK 处理；固定发送目标不被入站来源覆盖。 */
class UdpPresetBridge(preset: ConnectionPreset, private val onError: (Exception) -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val remote = InetSocketAddress(preset.remoteHost, preset.remotePort)
    private val external = DatagramSocket(null)
    private val internal: DatagramSocket
    @Volatile private var learnedPeer: java.net.SocketAddress? = null

    init {
        try {
            external.reuseAddress = true
            external.bind(InetSocketAddress("0.0.0.0", preset.localPort))
            internal = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        } catch (error: Exception) { external.close(); throw error }
    }

    fun start(sdkPort: Int) {
        val sdk = InetSocketAddress("127.0.0.1", sdkPort)
        listOf(
            pump("udp-to-sdk", external) { packet ->
                // 本地同端口预设不能把自身发送的数据再次送回 SDK。
                if (!(packet.address.isLoopbackAddress && packet.port == external.localPort)) {
                    learnedPeer = packet.socketAddress
                    internal.send(DatagramPacket(packet.data, packet.length, sdk))
                }
            },
            pump("udp-from-sdk", internal) { packet ->
                if (packet.socketAddress == sdk) {
                    external.send(DatagramPacket(packet.data, packet.length, remote))
                    learnedPeer?.takeIf { it != remote }?.let { peer ->
                        external.send(DatagramPacket(packet.data, packet.length, peer))
                    }
                }
            }
        )
    }

    private fun pump(name: String, socket: DatagramSocket, forward: (DatagramPacket) -> Unit) = thread(name = name, isDaemon = true) {
        try {
            while (!closed.get()) {
                val packet = DatagramPacket(ByteArray(65535), 65535)
                socket.receive(packet)
                if (!closed.get()) forward(packet)
            }
        } catch (error: Exception) {
            if (!closed.get()) { close(); onError(error) }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) { external.close(); internal.close() }
    }
}
