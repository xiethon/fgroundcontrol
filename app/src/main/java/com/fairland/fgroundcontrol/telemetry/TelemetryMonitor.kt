package com.fairland.fgroundcontrol.telemetry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackPoint(val latitude: Double, val longitude: Double)
data class Readout(val vehicle: VehicleTelemetry = VehicleTelemetry(), val nowMs: Long = 0, val alerts: List<HealthAlert> = emptyList(), val track: List<List<TrackPoint>> = emptyList(), val trackGeoJson: String = "{\"type\":\"FeatureCollection\",\"features\":[]}")
class TelemetryMonitor(source: StateFlow<VehicleTelemetry>, scope: CoroutineScope, private val event: (String) -> Unit, private val flight: (String) -> Unit) {
    private val mutable = MutableStateFlow(Readout())
    val state = mutable.asStateFlow()
    init { scope.launch {
        var session = -1
        var alerts = emptyMap<String, HealthAlert>()
        var track = emptyList<List<TrackPoint>>()
        var lastPosition = -1L
        var gap = true
        var logged = 0L
        var lastStatus: String? = null
        var lastRateStatus: String? = null
        while (isActive) {
            val snapshot = source.value
            val now = android.os.SystemClock.elapsedRealtime()
            if (snapshot.session != session) { session = snapshot.session; track = emptyList(); lastPosition = -1; gap = true; alerts = emptyMap(); logged = 0; lastStatus = null; lastRateStatus = null }
            snapshot.readings["status"]?.text?.let { text ->
                if (text != lastStatus) { event("fc_status session=$session $text"); lastStatus = text }
            }
            snapshot.readings["rate_status"]?.text?.let { text ->
                if (text != lastRateStatus) { event("telemetry_rate session=$session $text"); lastRateStatus = text }
            }
            val next = HealthRules.evaluate(snapshot, now).associateBy { it.id }
            next.forEach { (id, alert) -> if (alerts[id] != alert) event("health active session=$session id=$id severity=${alert.severity}") }
            alerts.keys.filter { it !in next }.forEach { id ->
                event("health ${HealthRules.resolution(id, snapshot, now)} session=$session id=$id")
            }
            alerts = next
            if (snapshot.quality("position", now) == Quality.VALID) {
                val position = snapshot.readings.getValue("position")
                if (position.receivedMs != lastPosition) {
                    val point = TrackPoint(position.values[0]!!, position.values[1]!!)
                    track = if (gap || track.isEmpty() || startsTrackSegment(lastPosition, position.receivedMs)) (track + listOf(listOf(point))) else track.dropLast(1) + listOf(track.last() + point)
                    while (track.sumOf { it.size } > 5000) track = if (track.first().size > 1) listOf(track.first().drop(1)) + track.drop(1) else track.drop(1)
                    lastPosition = position.receivedMs; gap = false
                }
            } else { gap = true }
            if (snapshot.connected && now - logged >= 1000 && snapshot.readings.isNotEmpty()) {
                flight("telemetry session=$session " + snapshot.readings.entries.joinToString(" ") { (topic, reading) -> "$topic:${reading.values}:${reading.text.orEmpty()}@${reading.receivedMs}" })
                logged = now
            }
            val geoJson = if (track === mutable.value.track) mutable.value.trackGeoJson else {
                "{\"type\":\"FeatureCollection\",\"features\":[" + track.filter { it.size >= 2 }.joinToString(",") { segment ->
                    "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" + segment.joinToString(",") { "[${it.longitude},${it.latitude}]" } + "]}}"
                } + "]}"
            }
            mutable.value = Readout(snapshot, now, next.values.toList(), track, geoJson)
            delay(250)
        }
    } }
}
