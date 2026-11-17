package com.fairland.fgroundcontrol.maps

import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class OfflineArchiveTest {
    private fun header(): ByteArray {
        val data = ByteArray(129)
        "PMTiles".toByteArray().copyInto(data); data[7] = 3; data[99] = 2; data[100] = 0; data[101] = 18; data[118] = 10
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(8, 127); buffer.putLong(16, 1); buffer.putLong(56, 128); buffer.putLong(64, 1)
        buffer.putInt(102, -10000000); buffer.putInt(106, -10000000); buffer.putInt(110, 10000000); buffer.putInt(114, 10000000)
        return data
    }
    @Test fun validRasterHeaderAndBounds() {
        val file = Files.createTempFile("raster", ".pmtiles").toFile()
        try { file.writeBytes(header()); assertEquals(OfflineArchive(-1.0, -1.0, 1.0, 1.0, 10), OfflineArchive.inspect(file)) }
        finally { file.delete() }
    }
    @Test fun corruptUnsupportedAndTruncatedArchivesRejected() {
        val file = Files.createTempFile("invalid", ".pmtiles").toFile()
        try {
            file.writeBytes(ByteArray(10)); assertThrows(IllegalArgumentException::class.java) { OfflineArchive.inspect(file) }
            file.writeBytes(header().apply { this[99] = 1 }); assertThrows(IllegalArgumentException::class.java) { OfflineArchive.inspect(file) }
            file.writeBytes(header().apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putLong(56, Long.MAX_VALUE) }); assertThrows(IllegalArgumentException::class.java) { OfflineArchive.inspect(file) }
        } finally { file.delete() }
    }
    @Test fun mapSourceConfigurationRequiresKeysAndDoesNotAlterCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { MapStyles.tianditu("") }
        assertFalse(MapStyles.validKey("token&injection"))
        val style = MapStyles.tianditu("valid_token_123")
        assertTrue(style.contains("img_w")); assertTrue(style.contains("cia_w")); assertTrue(style.contains("TILECOL={x}"))
        assertTrue(MapStyles.maptiler("valid_token_123").contains("/hybrid/style.json"))
    }
}
