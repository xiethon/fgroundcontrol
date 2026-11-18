package com.fairland.fgroundcontrol.connection

import java.net.*

/** 一个会话一个socket；不识别飞行器，不解码协议，不发布业务状态。 */
internal class UdpTransport : AutoCloseable {
    private val socket = DatagramSocket(null)
    private val buffer = ByteArray(65535)
    val localPort get() = socket.localPort
    fun open(port: Int, pollMs: Int) {
        socket.soTimeout = pollMs
        socket.bind(InetSocketAddress("0.0.0.0", port))
    }
    fun receive(): DatagramPacket = DatagramPacket(buffer, buffer.size).also(socket::receive)
    fun send(bytes: ByteArray, target: SocketAddress) = socket.send(DatagramPacket(bytes, bytes.size, target))
    override fun close() = socket.close()
}
