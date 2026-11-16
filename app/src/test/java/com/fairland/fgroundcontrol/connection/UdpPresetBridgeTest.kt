package com.fairland.fgroundcontrol.connection

import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.Assert.*

class UdpPresetBridgeTest {
    private fun socket() = DatagramSocket(InetSocketAddress("127.0.0.1", 0)).apply { soTimeout = 2000 }
    private fun receive(socket: DatagramSocket): DatagramPacket = DatagramPacket(ByteArray(1024), 1024).also { socket.receive(it) }
    @Test fun presetsMatchReferenceAndUnknownFallsBack() {
        assertEquals(14551, ConnectionPresets.default.localPort)
        assertEquals("local", ConnectionPresets.resolve("removed").id)
        assertEquals(14550, ConnectionPresets.resolve("p401d").remotePort)
        assertEquals("192.168.144.20", ConnectionPresets.resolve("rc7").remoteHost)
    }
    @Test fun bidirectionalForwardingKeepsConfiguredTargetAndClosesPort() {
        val port = socket().use { it.localPort }
        socket().use { target -> socket().use { peer -> socket().use { sdk ->
            val failure = AtomicReference<Exception?>()
            val bridge = UdpPresetBridge(ConnectionPreset("test", "test", port, "127.0.0.1", target.localPort)) { failure.set(it) }
            try {
                bridge.start(sdk.localPort)
                peer.send(DatagramPacket(byteArrayOf(1, 2), 2, InetSocketAddress("127.0.0.1", port)))
                val inbound = receive(sdk)
                assertArrayEquals(byteArrayOf(1, 2), inbound.data.copyOf(inbound.length))
                sdk.send(DatagramPacket(byteArrayOf(3), 1, inbound.socketAddress))
                assertEquals(3, receive(target).data[0].toInt())
                assertEquals(3, receive(peer).data[0].toInt())
                assertNull(failure.get())
            } finally { bridge.close() }
            DatagramSocket(InetSocketAddress("127.0.0.1", port)).close()
        } } }
    }
}
