package com.fairland.fgroundcontrol.connection.protocol
import com.MAVLink.common.*
import com.fairland.fgroundcontrol.telemetry.VehicleTelemetry
import org.junit.Assert.*
import org.junit.Test

class TelemetryMapperTest {
    @Test fun positionUnitsAndOriginalTime() {
        val state = TelemetryMapper.map(VehicleTelemetry(session=4,connected=true),msg_global_position_int().apply {
            lat=-301234567;lon=1201234567;relative_alt=-1250;alt=20000;vx=300;vy=400;vz=-200
        },1234)
        assertEquals(listOf(-30.1234567,120.1234567),state.readings.getValue("position").values)
        assertEquals(listOf(-1.25,20.0),state.readings.getValue("altitude").values)
        assertEquals(listOf(5.0,2.0),state.readings.getValue("speed").values)
        assertEquals(1234L,state.readings.getValue("position").receivedMs)
        assertEquals(4,state.session)
    }
    @Test fun missingValuesAndBatterySourcePriority() {
        var state=VehicleTelemetry(session=1,connected=true)
        state=TelemetryMapper.map(state,msg_sys_status().apply { battery_remaining=-1;voltage_battery=65535 },10)
        assertNull(state.readings.getValue("battery").values[0]);assertNull(state.readings.getValue("voltage").values[0])
        state=TelemetryMapper.map(state,msg_sys_status().apply { battery_remaining=50;voltage_battery=12000 },11)
        assertEquals(50.0,state.readings.getValue("battery").values[0])
        state=TelemetryMapper.map(state,msg_battery_status().apply { battery_remaining=60;id=0;voltages.fill(65535);voltages[0]=12500 },12)
        state=TelemetryMapper.map(state,msg_sys_status().apply { battery_remaining=1;voltage_battery=1000 },13)
        assertEquals(60.0,state.readings.getValue("battery").values[0])
        assertEquals(12.5,state.readings.getValue("voltage").values[0])
    }
}
