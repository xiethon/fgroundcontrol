package com.fairland.fgroundcontrol.connection.protocol

import com.MAVLink.minimal.msg_heartbeat
import com.MAVLink.common.*
import com.fairland.fgroundcontrol.telemetry.*
import kotlin.math.hypot

/** 协议类型止于适配边界；无Android/IO依赖，统一转换线上单位与无效值。 */
object TelemetryMapper {
    fun map(old: VehicleTelemetry, message: com.MAVLink.Messages.MAVLinkMessage, at: Long): VehicleTelemetry {
        var next = old
        fun value(topic: String, values: List<Double?> = emptyList(), text: String? = null) {
            next = next.accept(old.session, topic, Reading(values.map { it?.takeIf(Double::isFinite) }, text, at))
        }
        when (message) {
            is msg_heartbeat -> {
                value("armed", listOf(if (message.base_mode.toInt() and 128 != 0) 1.0 else 0.0))
                val main = (message.custom_mode shr 16).toInt() and 255
                val sub = (message.custom_mode shr 24).toInt() and 255
                value("mode", text = when (main) { 1 -> "MANUAL"; 2 -> "ALTCTL"; 3 -> "POSCTL"; 4 -> when(sub) { 3 -> "HOLD"; 4 -> "MISSION"; 5 -> "RETURN_TO_LAUNCH"; 6 -> "LAND"; 2 -> "TAKEOFF"; else -> "AUTO" }; 5 -> "ACRO"; 6 -> "OFFBOARD"; 7 -> "STABILIZED"; else -> "UNKNOWN" })
            }
            is msg_global_position_int -> {
                value("position", listOf(message.lat / 1e7, message.lon / 1e7))
                value("altitude", listOf(message.relative_alt / 1000.0, message.alt / 1000.0))
                val n = message.vx / 100.0; val e = message.vy / 100.0; val d = message.vz / 100.0
                value("velocity", listOf(n,e,d)); value("speed", listOf(hypot(n,e),-d))
            }
            is msg_attitude -> value("attitude", listOf(message.roll.toDouble(),message.pitch.toDouble(),message.yaw.toDouble()).map { Math.toDegrees(it) })
            is msg_gps_raw_int -> value("gps", listOf(message.satellites_visible.toInt().takeUnless { it == 255 }?.toDouble()), when(message.fix_type.toInt()) { 0 -> "NO_GPS"; 1 -> "NO_FIX"; 2 -> "FIX_2D"; 3 -> "FIX_3D"; 4 -> "FIX_DGPS"; 5 -> "RTK_FLOAT"; 6 -> "RTK_FIXED"; else -> "UNKNOWN" })
            is msg_battery_status -> {
                value("battery", listOf(message.battery_remaining.toInt().takeIf { it in 0..100 }?.toDouble()), "id=${message.id}")
                val volts = message.voltages.filter { it != 65535 }
                value("voltage", listOf(if (volts.isEmpty()) null else volts.sum().toDouble() / 1000))
            }
            is msg_sys_status -> if (!next.readings.containsKey("battery") || next.readings["battery"]?.text == "source=SYS_STATUS") {
                value("battery", listOf(message.battery_remaining.toInt().takeIf { it in 0..100 }?.toDouble()), "source=SYS_STATUS")
                value("voltage", listOf(message.voltage_battery.takeUnless { it == 65535 }?.div(1000.0)))
            }
            is msg_statustext -> value("status", text = "${message.severity}: ${message.getText()}")
        }
        return next
    }
}
