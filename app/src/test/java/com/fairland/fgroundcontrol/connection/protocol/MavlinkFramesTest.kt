package com.fairland.fgroundcontrol.connection.protocol

import org.junit.Assert.*
import org.junit.Test

class MavlinkFramesTest {
    @Test fun splitAndCombinedFrames() {
        val frames = mutableListOf<ByteArray>()
        val reader = MavlinkFrames({ fail("unexpected signature") }, frames::add)
        val v1 = byteArrayOf(0xfe.toByte(), 0, 1, 1, 1, 0, 0, 0)
        val v2 = ByteArray(12).apply { this[0] = 0xfd.toByte() }
        reader.accept(v1.copyOfRange(0, 3))
        assertTrue(frames.isEmpty())
        reader.accept(v1.copyOfRange(3, v1.size) + v2)
        assertEquals(2, frames.size)
        assertArrayEquals(v1, frames[0]); assertArrayEquals(v2, frames[1])
    }
    @Test fun signedTailCannotInjectUnsignedFrame() {
        var rejected = 0
        val frames = mutableListOf<ByteArray>()
        val reader = MavlinkFrames({ rejected++ }, frames::add)
        val signed = ByteArray(25).apply { this[0] = 0xfd.toByte(); this[2] = 1; this[12] = 0xfe.toByte() }
        reader.accept(signed)
        assertEquals(1, rejected); assertTrue(frames.isEmpty())
        reader.accept(byteArrayOf(0xfe.toByte(), 0, 1, 1, 1, 0, 0, 0))
        assertEquals(1, frames.size)
    }
    @Test fun maximumSignedFrameAndReset() {
        var rejected = 0
        val reader = MavlinkFrames({ rejected++ }, { fail("unexpected frame") })
        val signed = ByteArray(280).apply { this[0] = 0xfd.toByte(); this[1] = 255.toByte(); this[2] = 1 }
        reader.accept(signed); assertEquals(1, rejected)
        reader.accept(signed.copyOf(15)); reader.reset(); reader.accept(signed)
        assertEquals(2, rejected)
    }
    @Test fun incompatibleFlagsAreNotDispatched() {
        val reader = MavlinkFrames({ fail("signature") }, { fail("incompatible frame") })
        reader.accept(ByteArray(12).apply { this[0] = 0xfd.toByte(); this[2] = 2 })
    }
}
