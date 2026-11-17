package com.fairland.fgroundcontrol

import com.fairland.fgroundcontrol.connection.ConnectionPreset
import com.fairland.fgroundcontrol.connection.MavsdkConnection
import com.fairland.fgroundcontrol.telemetry.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs

/** 只连接专用标准MAVLink测试源，不能用于真实设备。 */
internal object ProtocolProbe {
    fun stableRestart(adapter: MavsdkConnection): String {
        fun await(condition: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 20000
            while (!condition()) { check(android.os.SystemClock.elapsedRealtime() < end) { "Stable PX4 timeout: ${adapter.state.value}" }; Thread.sleep(100) }
        }
        fun phase(name: String) {
            val bytes = "PHASE:$name".toByteArray()
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("10.0.2.2"), 14661)) }
        }
        fun fresh() = adapter.state.value == "connected" && periodicTopics.all { adapter.telemetry.value.quality(it, android.os.SystemClock.elapsedRealtime()) == Quality.VALID }
        try {
            adapter.connect(ConnectionPreset("stable-restart", "SIMULATION stable PX4", 14561, "10.0.2.2", 14661))
            await { fresh() }
            val before = adapter.telemetry.value.readings.getValue("position").receivedMs
            phase("disconnect")
            await { adapter.state.value == "disconnected" }
            check(adapter.telemetry.value.readings.isEmpty())
            phase("restart")
            await { fresh() && (adapter.telemetry.value.readings["position"]?.receivedMs ?: 0) > before }
            return "PASS: fixed PX4 v1.17.0 SIH stop/restart, invalidation and fresh telemetry reconnect; no flight commands"
        } finally { adapter.disconnect() }
    }

    fun run(adapter: MavsdkConnection): String {
        fun await(timeout: Long = 10000, condition: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + timeout
            while (!condition()) {
                check(android.os.SystemClock.elapsedRealtime() < end) { "Protocol condition timed out: ${adapter.state.value}; ${adapter.telemetry.value.readings}" }
                Thread.sleep(50)
            }
        }
        fun phase(name: String) {
            val bytes = "PHASE:$name".toByteArray()
            DatagramSocket().use { it.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("10.0.2.2"), 14660)) }
        }
        fun fields(battery: Double = 55.0) {
            val topics = periodicTopics + setOf("mode", "armed", "health", "status")
            await { val now = android.os.SystemClock.elapsedRealtime(); topics.all { adapter.telemetry.value.quality(it, now) == Quality.VALID } && adapter.telemetry.value.readings["battery"]?.values?.firstOrNull() == battery && adapter.telemetry.value.readings["health"]?.values == listOf(0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0) }
            val data = adapter.telemetry.value.readings
            fun numbers(topic: String, expected: List<Double>, tolerance: Double = .01) {
                val actual = data.getValue(topic).values
                check(actual.size == expected.size && actual.zip(expected).all { (a, e) -> a != null && abs(a - e) <= tolerance }) { "$topic expected=$expected actual=$actual" }
            }
            numbers("position", listOf(47.397742, 8.545594), .0000001)
            numbers("altitude", listOf(12.5, 488.125))
            numbers("velocity", listOf(3.0, 4.0, -2.0))
            numbers("speed", listOf(5.0, 2.0))
            numbers("attitude", listOf(Math.toDegrees(.1), Math.toDegrees(-.2), Math.toDegrees(.3)))
            numbers("battery", listOf(battery)); numbers("voltage", listOf(12.0)); numbers("gps", listOf(12.0))
            numbers("armed", listOf(0.0))
            check(data.getValue("gps").text == "FIX_3D")
            check(data.getValue("mode").text == "HOLD")
            check(data.getValue("status").text == "INFO: FGC_PROTOCOL_FIXTURE")
            numbers("health", listOf(0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0))
        }
        try {
            phase("baseline")
            adapter.connect(ConnectionPreset("protocol-probe", "SIMULATION protocol fixture", 14560, "10.0.2.2", 14660))
            await { adapter.state.value == "connected" }
            fields()
            for (name in listOf("loss", "delay", "reorder")) {
                val previous = adapter.telemetry.value.readings.getValue("position").receivedMs
                phase(name)
                Thread.sleep(1600)
                await { (adapter.telemetry.value.readings["position"]?.receivedMs ?: 0) > previous }
                fields()
            }
            phase("stale")
            await { adapter.telemetry.value.quality("position", android.os.SystemClock.elapsedRealtime()) == Quality.STALE }
            check(adapter.state.value == "connected")
            check(HealthRules.evaluate(adapter.telemetry.value, android.os.SystemClock.elapsedRealtime()).any { it.id == "telemetry" })
            phase("baseline"); fields()
            phase("disconnect")
            await(15000) { adapter.state.value == "disconnected" }
            check(adapter.telemetry.value.readings.isEmpty())
            phase("restart")
            await(15000) { adapter.state.value == "connected" }
            fields(66.0)
            return "PASS: all telemetry units/values, 50% loss, 1s delay, duplicate/reorder, stale, disconnect, fixture reboot/reconnect"
        } finally { adapter.disconnect() }
    }
}
