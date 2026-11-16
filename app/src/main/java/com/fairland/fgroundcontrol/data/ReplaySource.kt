package com.fairland.fgroundcontrol.data

/** 只读离线记录，不持有真实连接或命令出口。时间为相对毫秒。 */
data class ReplaySample(val offsetMs: Long, val latitude: Double, val longitude: Double) {
    init { require(offsetMs in 0..86400000 && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}
class ReplaySource {
    fun read(reader: java.io.Reader): List<ReplaySample> {
        val content = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(content.length + count <= 1024 * 1024) { "Replay file too large" }
            content.append(buffer, 0, count)
        }
        return read(content.toString().lineSequence().filter { it.isNotBlank() })
    }
    fun read(lines: Sequence<String>): List<ReplaySample> {
        var previous = -1L
        val result = lines.mapIndexed { index, line ->
            require(index < 10000) { "Replay too large" }
            val fields = line.split(',')
            require(fields.size == 3) { "Invalid replay row ${index + 1}" }
            ReplaySample(fields[0].toLong(), fields[1].toDouble(), fields[2].toDouble()).also {
                require(it.offsetMs >= previous) { "Replay time moved backwards" }; previous = it.offsetMs
            }
        }.toList()
        require(result.isNotEmpty()) { "Empty replay" }
        return result
    }
}
