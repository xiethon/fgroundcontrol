package com.fairland.fgroundcontrol.telemetry

import org.junit.Test
import org.junit.Assert.*

class TelemetryStateTest {
    @Test fun freshnessDistinguishesMissingInvalidBoundaryAndDisconnect() {
        var state = VehicleTelemetry(session = 1, connected = true)
        assertEquals(Quality.MISSING, state.quality("position", 0))
        state = state.accept(1, "position", Reading(listOf(0.0, 0.0), receivedMs = 100))
        assertEquals(Quality.VALID, state.quality("position", 3100))
        assertEquals(Quality.STALE, state.quality("position", 3101))
        assertEquals(Quality.STALE, state.copy(connected = false).quality("position", 100))
        state = state.accept(1, "battery", Reading(listOf(null), receivedMs = 100))
        assertEquals(Quality.INVALID, state.quality("battery", 100))
    }
    @Test fun oldSessionAndOutOfOrderCannotOverrideNewData() {
        val original = VehicleTelemetry(2, true).accept(2, "position", Reading(listOf(30.0, 120.0), receivedMs = 200))
        assertEquals(original, original.accept(1, "position", Reading(listOf(0.0, 0.0), receivedMs = 300)))
        assertEquals(original, original.accept(2, "position", Reading(listOf(0.0, 0.0), receivedMs = 100)))
        assertTrue(VehicleTelemetry(3).readings.isEmpty())
    }
    @Test fun batteryThresholdsAndInvalidVoltageDoNotMaskLowBattery() {
        fun severity(level: Double): String? = HealthRules.evaluate(VehicleTelemetry(1, true).accept(1, "battery", Reading(listOf(level), receivedMs = 0)), 0).firstOrNull { it.id == "battery" }?.severity
        assertNull(severity(20.0)); assertEquals("warning", severity(19.9)); assertEquals("warning", severity(10.0)); assertEquals("critical", severity(9.9))
        assertFalse(HealthRules.evaluate(VehicleTelemetry(1, true).accept(1, "battery", Reading(listOf(null), receivedMs = 0)), 0).any { it.id == "battery" })
    }
    @Test fun disconnectedAndExpiredDoNotProduceLiveBatteryClaims() {
        val state = VehicleTelemetry(1, true).accept(1, "battery", Reading(listOf(1.0), receivedMs = 0))
        assertFalse(HealthRules.evaluate(state, 3001).any { it.id == "battery" })
        assertEquals(listOf(HealthAlert("link", "critical")), HealthRules.evaluate(state.copy(connected = false), 100))
    }
    @Test fun malformedNumericPayloadAndFutureTimestampAreInvalid() {
        val state = VehicleTelemetry(1, true)
        assertEquals(Quality.INVALID, state.accept(1, "battery", Reading(text = "id=0", receivedMs = 0)).quality("battery", 0))
        assertEquals(Quality.INVALID, state.accept(1, "battery", Reading(listOf(101.0), receivedMs = 0)).quality("battery", 0))
        assertEquals(Quality.INVALID, state.accept(1, "position", Reading(listOf(91.0, 0.0), receivedMs = 0)).quality("position", 0))
        assertEquals(Quality.INVALID, state.accept(1, "position", Reading(listOf(30.0, 120.0), receivedMs = 10)).quality("position", 0))
    }
    @Test fun subscriptionErrorIsInvalidAndEventFieldsAreNotExpiredByLackOfChange() {
        val state = VehicleTelemetry(1, true).accept(1, "battery", Reading(text = "UNAVAILABLE", receivedMs = 0)).accept(1, "mode", Reading(text = "HOLD", receivedMs = 0))
        assertEquals(Quality.INVALID, state.quality("battery", 100))
        assertEquals(Quality.VALID, state.quality("mode", 100000))
    }
    @Test fun dataLossDoesNotResolveTelemetryAndSamplingGapBreaksTrack() {
        assertEquals("suspended_data_unavailable", HealthRules.resolution("telemetry", VehicleTelemetry(1, false), 4000))
        val valid = periodicTopics.filter { it != "voltage" }.fold(VehicleTelemetry(1, true)) { state, topic ->
            val values = when (topic) { "position", "altitude", "speed" -> listOf(0.0, 0.0); "velocity", "attitude" -> listOf(0.0, 0.0, 0.0); else -> listOf(50.0) }
            state.accept(1, topic, Reading(values, if (topic == "gps") "FIX_3D" else null, 100))
        }
        assertEquals("resolved", HealthRules.resolution("telemetry", valid, 3100))
        assertEquals("suspended_data_unavailable", HealthRules.resolution("telemetry", valid, 3101))
        assertFalse(startsTrackSegment(100, 3100)); assertTrue(startsTrackSegment(100, 3101))
    }
}
