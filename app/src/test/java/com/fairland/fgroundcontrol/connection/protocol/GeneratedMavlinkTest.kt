package com.fairland.fgroundcontrol.connection.protocol

import com.MAVLink.Parser
import com.MAVLink.minimal.msg_heartbeat
import com.MAVLink.common.msg_global_position_int
import org.junit.Assert.*
import org.junit.Test

class GeneratedMavlinkTest {
    @Test fun heartbeatRoundTripAndCorruptCrc() {
        val packet = msg_heartbeat().apply { autopilot = 12; type = 2; mavlink_version = 3 }.pack().apply { sysid = 1; compid = 1 }
        val bytes = packet.encodePacket()
        fun decode(input: ByteArray): com.MAVLink.MAVLinkPacket? {
            val parser = Parser(); var result: com.MAVLink.MAVLinkPacket? = null
            input.forEach { parser.mavlink_parse_char(it.toInt() and 255)?.let { p -> result = p } }
            return result
        }
        assertEquals(12, (decode(bytes)!!.unpack() as msg_heartbeat).autopilot.toInt())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertNull(decode(bytes))
    }
    @Test fun scaledFieldsSurviveGeneratedWireFormat() {
        val expected = msg_global_position_int().apply { lat = -301234567; lon = 1201234567; relative_alt = -1250; vx = -123; vy = 456; vz = -78 }
        val bytes = expected.pack().encodePacket(); val parser = Parser()
        var result: msg_global_position_int? = null
        bytes.forEach { parser.mavlink_parse_char(it.toInt() and 255)?.let { p -> result = p.unpack() as msg_global_position_int } }
        assertEquals(expected.lat, result!!.lat); assertEquals(expected.lon, result!!.lon)
        assertEquals(expected.relative_alt, result!!.relative_alt); assertEquals(expected.vx, result!!.vx)
    }
}
