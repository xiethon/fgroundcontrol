package com.fairland.fgroundcontrol.maps

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PMTiles v3栅格包头校验；目录和瓦片内容仍由MapLibre加载并报告错误。 */
data class OfflineArchive(val west: Double, val south: Double, val east: Double, val north: Double, val zoom: Int) {
    companion object {
        fun inspect(file: File): OfflineArchive {
            RandomAccessFile(file, "r").use { input ->
                require(input.length() >= 127) { "Incomplete PMTiles header" }
                val bytes = ByteArray(127); input.readFully(bytes)
                require(String(bytes, 0, 7, Charsets.US_ASCII) == "PMTiles" && bytes[7].toInt() == 3) { "Expected PMTiles v3" }
                require(bytes[99].toInt() in 2..4) { "Only PNG/JPEG/WebP raster archives are supported" }
                val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                for ((offsetIndex, lengthIndex) in listOf(8 to 16, 24 to 32, 40 to 48, 56 to 64)) {
                    val offset = header.getLong(offsetIndex); val length = header.getLong(lengthIndex)
                    require(offset >= 0 && length >= 0 && offset <= input.length() && length <= input.length() - offset) { "Archive section outside file" }
                }
                require(header.getLong(8) >= 127 && header.getLong(16) > 0 && header.getLong(8) + header.getLong(16) <= 16384)
                val west = header.getInt(102) / 1e7; val south = header.getInt(106) / 1e7
                val east = header.getInt(110) / 1e7; val north = header.getInt(114) / 1e7
                require(west in -180.0..180.0 && east in -180.0..180.0 && west < east && south in -85.051129..85.051129 && north in -85.051129..85.051129 && south < north)
                val minZoom = bytes[100].toInt() and 255; val maxZoom = bytes[101].toInt() and 255
                require(minZoom in 0..24 && maxZoom in minZoom..24)
                return OfflineArchive(west, south, east, north, minOf(maxZoom, maxOf(minZoom, bytes[118].toInt() and 255)))
            }
        }
    }
}
