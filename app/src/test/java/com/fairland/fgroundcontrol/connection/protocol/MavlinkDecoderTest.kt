package com.fairland.fgroundcontrol.connection.protocol
import com.MAVLink.minimal.msg_heartbeat
import org.junit.Assert.*
import org.junit.Test

class MavlinkDecoderTest {
    private fun signed() = msg_heartbeat().apply { autopilot=12;type=2;mavlink_version=3 }.pack().apply {
        isMavlink2=true;incompatFlags=1;sysid=1;compid=1
    }.encodePacket() + ByteArray(13)
    @Test fun signatureIsConsumedButNotAuthenticated() {
        val bytes=signed()
        assertNotNull(MavlinkDecoder.decode(bytes))
        bytes[bytes.lastIndex]=99
        assertNotNull(MavlinkDecoder.decode(bytes))
    }
    @Test fun badCrcAndMissingSignatureAreRejected() {
        val bytes=signed()
        assertNull(MavlinkDecoder.decode(bytes.copyOf(bytes.size-1)))
        bytes[bytes.size-14]=(bytes[bytes.size-14].toInt() xor 1).toByte()
        assertNull(MavlinkDecoder.decode(bytes))
    }
    @Test fun signedTailCannotCreateSecondMessage() {
        val frames=mutableListOf<ByteArray>()
        val bytes=signed();bytes[bytes.size-13]=0xfe.toByte()
        MavlinkFrames({ frames.add(it) },{ frames.add(it) }).accept(bytes)
        assertEquals(1,frames.size)
        assertNotNull(MavlinkDecoder.decode(frames.single()))
    }
}
