package com.fairland.fgroundcontrol.telemetry

/** 接收时间来自设备单调时钟；不得以UI刷新重写。 */
data class Reading(val values: List<Double?> = emptyList(), val text: String? = null, val receivedMs: Long) {
    fun valid() = values.all { it != null && it.isFinite() } && (values.isNotEmpty() || (!text.isNullOrBlank() && text != "UNKNOWN" && !text.startsWith("UNAVAILABLE")))
}
enum class Quality { MISSING, INVALID, STALE, VALID }
val periodicTopics = setOf("position", "altitude", "velocity", "speed", "attitude", "battery", "gps", "voltage")
data class VehicleTelemetry(val session: Int = 0, val connected: Boolean = false, val readings: Map<String, Reading> = emptyMap()) {
    fun quality(topic: String, now: Long): Quality {
        val reading = readings[topic] ?: return Quality.MISSING
        if (!reading.valid() || reading.receivedMs < 0 || reading.receivedMs > now) return Quality.INVALID
        val dimensions = mapOf("position" to 2, "altitude" to 2, "velocity" to 3, "speed" to 2, "attitude" to 3, "battery" to 1, "voltage" to 1, "gps" to 1, "armed" to 1, "health" to 7)
        if (dimensions[topic]?.let { reading.values.size != it } == true) return Quality.INVALID
        if (topic == "position" && (reading.values[0]!! !in -90.0..90.0 || reading.values[1]!! !in -180.0..180.0)) return Quality.INVALID
        if (topic == "battery" && reading.values[0]!! !in 0.0..100.0) return Quality.INVALID
        if (topic == "voltage" && reading.values[0]!! < 0) return Quality.INVALID
        if (topic == "gps" && (reading.values[0]!! < 0 || reading.text.isNullOrBlank())) return Quality.INVALID
        if (topic in setOf("armed", "health") && reading.values.any { it !in listOf(0.0, 1.0) }) return Quality.INVALID
        if (!connected || (topic in periodicTopics && now - reading.receivedMs > 3000)) return Quality.STALE
        return Quality.VALID
    }
    fun accept(token: Int, topic: String, reading: Reading): VehicleTelemetry {
        if (token != session) return this
        if (readings[topic]?.receivedMs?.let { it > reading.receivedMs } == true) return this
        return copy(readings = readings + (topic to reading))
    }
}
data class HealthAlert(val id: String, val severity: String)
fun startsTrackSegment(previousMs: Long, currentMs: Long) = previousMs < 0 || currentMs - previousMs > 3000
object HealthRules {
    fun resolution(id: String, state: VehicleTelemetry, now: Long): String {
        val available = when (id) {
            "link" -> state.connected
            "telemetry" -> state.connected && periodicTopics.filter { it != "voltage" }.all { state.quality(it, now) == Quality.VALID }
            else -> state.quality(id, now) == Quality.VALID
        }
        return if (available) "resolved" else "suspended_data_unavailable"
    }
    fun evaluate(state: VehicleTelemetry, now: Long): List<HealthAlert> = buildList {
        if (!state.connected) { add(HealthAlert("link", "critical")); return@buildList }
        val stale = periodicTopics.filter { it != "voltage" }.filter { state.quality(it, now) != Quality.VALID }
        if (stale.isNotEmpty()) add(HealthAlert("telemetry", "warning"))
        if (state.quality("battery", now) == Quality.VALID) {
            val remaining = state.readings.getValue("battery").values[0]!!
            if (remaining < 10) add(HealthAlert("battery", "critical"))
            else if (remaining < 20) add(HealthAlert("battery", "warning"))
        }
        if (state.quality("gps", now) == Quality.VALID && state.readings["gps"]?.text !in setOf("FIX_3D", "FIX_DGPS", "RTK_FLOAT", "RTK_FIXED")) add(HealthAlert("gps", "warning"))
        val health = state.readings["health"]
        if (state.quality("health", now) == Quality.VALID && health?.values?.any { it == 0.0 } == true) add(HealthAlert("health", "warning"))
    }
}
