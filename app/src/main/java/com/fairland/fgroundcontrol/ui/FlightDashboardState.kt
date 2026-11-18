package com.fairland.fgroundcontrol.ui

import com.fairland.fgroundcontrol.telemetry.*
import java.util.Locale

/** 未连接时按约定指北；其他缺失遥测不推断。 */
data class FlightDashboardState(val roll: Double?, val pitch: Double?, val heading: Double?, val relativeHeight: Double?, val speed: Double?) {
    companion object {
        fun from(readout: Readout): FlightDashboardState {
            fun values(topic: String) = readout.vehicle.readings[topic]?.values
                ?.takeIf { readout.vehicle.quality(topic,readout.nowMs)==Quality.VALID }
            if (!readout.vehicle.connected) return FlightDashboardState(null, null, 0.0, null, null)
            val attitude=values("attitude")
            return FlightDashboardState(attitude?.getOrNull(0),attitude?.getOrNull(1),attitude?.getOrNull(2)?.let { (it%360+360)%360 },values("altitude")?.getOrNull(0),values("speed")?.getOrNull(0))
        }
        fun format(value: Double?) = value?.takeIf(Double::isFinite)?.let { String.format(Locale.ROOT,"%.1f",it).let { text -> if (text == "-0.0") "0.0" else text } } ?: "—"
    }
}
