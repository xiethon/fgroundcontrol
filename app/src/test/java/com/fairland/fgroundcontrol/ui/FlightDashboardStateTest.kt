package com.fairland.fgroundcontrol.ui
import com.fairland.fgroundcontrol.telemetry.*
import org.junit.Assert.*
import org.junit.Test

class FlightDashboardStateTest {
    private fun sample(connected: Boolean=true, now: Long=1000) = Readout(vehicle=VehicleTelemetry(session=1,connected=connected,readings=mapOf(
        "attitude" to Reading(listOf(15.0,-10.0,-90.0),receivedMs=1000),
        "altitude" to Reading(listOf(-1.2,50.0),receivedMs=1000),
        "speed" to Reading(listOf(5.0,2.0),receivedMs=1000))),nowMs=now)
    @Test fun validValuesAndHeadingNormalization() {
        val state=FlightDashboardState.from(sample())
        assertEquals(270.0,state.heading);assertEquals(-1.2,state.relativeHeight)
        assertEquals(5.0,state.speed);assertEquals(15.0,state.roll);assertEquals(-10.0,state.pitch)
    }
    @Test fun disconnectedDefaultsNorthButHeightRemainsMissing() {
        val state=FlightDashboardState.from(sample(false))
        assertEquals(0.0,state.heading);assertNull(state.relativeHeight)
        assertNull(state.roll);assertNull(state.pitch);assertNull(state.speed)
    }
    @Test fun staleHasNoLiveNeedles() {
        for (readout in listOf(sample(now=4001))) {
            val state=FlightDashboardState.from(readout)
            assertNull(state.heading);assertNull(state.roll);assertNull(state.pitch);assertNull(state.speed);assertNull(state.relativeHeight)
        }
    }
    @Test fun invalidFieldsDoNotInvalidateIndependentTopics() {
        val readout=sample();val vehicle=readout.vehicle.copy(readings=readout.vehicle.readings+("attitude" to Reading(listOf(Double.NaN,0.0,0.0),receivedMs=1000)))
        val state=FlightDashboardState.from(readout.copy(vehicle=vehicle))
        assertNull(state.heading);assertEquals(5.0,state.speed)
    }
    @Test fun formatterNeverInventsMissingZero() {
        assertEquals("—",FlightDashboardState.format(null));assertEquals("—",FlightDashboardState.format(Double.NaN))
        assertEquals("0.0",FlightDashboardState.format(0.0));assertEquals("12.3",FlightDashboardState.format(12.3))
        assertEquals("0.0",FlightDashboardState.format(-0.01));assertEquals("-0.1",FlightDashboardState.format(-0.06))
    }
}
