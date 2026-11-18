package com.fairland.fgroundcontrol.connection

import java.net.*
import org.junit.Assert.*
import org.junit.Test

class UdpTransportTest {
    @Test fun datagramBoundariesAndPortRelease() {
        val transport=UdpTransport()
        transport.open(0,1000)
        val port=transport.localPort
        try {
            DatagramSocket().use { sender ->
                val bytes=byteArrayOf(1,2,3)
                sender.send(DatagramPacket(bytes,bytes.size,InetSocketAddress("127.0.0.1",port)))
                val packet=transport.receive()
                assertEquals(3,packet.length)
                assertArrayEquals(bytes,packet.data.copyOf(packet.length))
                assertEquals(sender.localPort,packet.port)
            }
        } finally { transport.close() }
        DatagramSocket(port).use { assertEquals(port,it.localPort) }
    }
    @Test fun idleReceiveTimesOut() {
        val transport=UdpTransport();transport.open(0,50)
        try { transport.receive();fail("expected timeout") }
        catch (_: SocketTimeoutException) { }
        finally { transport.close() }
    }
}
