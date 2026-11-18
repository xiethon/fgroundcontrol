package com.fairland.fgroundcontrol

import android.app.Instrumentation
import android.os.Bundle
import com.fairland.fgroundcontrol.connection.*
import com.MAVLink.minimal.msg_heartbeat
import com.MAVLink.common.msg_global_position_int
import java.net.*

/** 仅本机回环合成帧，不启动Activity或发送飞行指令。 */
class DirectUdpProbe : Instrumentation() {
    private var sitl = false
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); sitl = arguments?.getString("sitl") == "true"; start() }
    override fun onStart() {
        val out = Bundle()
        val adapter = DirectMavlinkConnection(targetContext)
        try {
            if (sitl) {
                adapter.connect(ConnectionPresets.resolve("sitl"))
                val end = android.os.SystemClock.elapsedRealtime()+15000
                while (adapter.state.value != "connected" || !adapter.telemetry.value.readings.containsKey("position")) {
                    check(android.os.SystemClock.elapsedRealtime()<end) { "SITL timeout: ${adapter.state.value}" }; Thread.sleep(100)
                }
                out.putString("sitl", "PASS: actual PX4 heartbeat and position received")
                finish(-1,out); return
            }
            val port = DatagramSocket(0).use { it.localPort }
            DatagramSocket().use { source ->
                adapter.connect(ConnectionPreset("fixture", "fixture", port, "127.0.0.1", source.localPort))
                fun send(message: com.MAVLink.Messages.MAVLinkMessage) {
                    val bytes = message.pack().apply { sysid=1;compid=1 }.encodePacket()
                    source.send(DatagramPacket(bytes, bytes.size, InetSocketAddress("127.0.0.1",port)))
                }
                fun await(test: () -> Boolean) {
                    val end = android.os.SystemClock.elapsedRealtime()+8000
                    while (!test()) { check(android.os.SystemClock.elapsedRealtime()<end) { "probe timed out: ${adapter.state.value}" }; Thread.sleep(25) }
                }
                Thread.sleep(300)
                send(msg_global_position_int().apply { lat=300000000;lon=1200000000 })
                Thread.sleep(100);check(adapter.state.value!="connected")
                send(msg_heartbeat().apply { type=2;autopilot=12;mavlink_version=3 })
                await { adapter.state.value=="connected" }
                send(msg_global_position_int().apply { lat=301234567;lon=1201234567;relative_alt=12340;vx=300;vy=400;vz=-200 })
                await { adapter.telemetry.value.readings.containsKey("position") }
                check(adapter.telemetry.value.readings.getValue("position").values==listOf(30.1234567,120.1234567))
                check(adapter.telemetry.value.readings.getValue("speed").values==listOf(5.0,2.0))
                await { adapter.state.value=="connecting" }
                check(adapter.telemetry.value.readings.isEmpty())
                send(msg_heartbeat().apply { type=2;autopilot=12;mavlink_version=3 })
                await { adapter.state.value=="connected" }
                adapter.disconnect();check(adapter.state.value=="disconnected")
                out.putString("direct_udp", "PASS: no-heartbeat rejection, discovery, position/units, timeout clearing, reconnect, stop")
            }
            finish(-1,out)
        } catch (e: Throwable) { out.putString("error",e.stackTraceToString());finish(0,out) }
        finally { adapter.disconnect() }
    }
}
