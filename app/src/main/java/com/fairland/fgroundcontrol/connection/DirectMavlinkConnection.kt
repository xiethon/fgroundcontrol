package com.fairland.fgroundcontrol.connection

import android.content.Context
import android.os.SystemClock
import com.MAVLink.minimal.msg_heartbeat
import com.MAVLink.common.*
import com.fairland.fgroundcontrol.connection.protocol.MavlinkFrames
import com.fairland.fgroundcontrol.telemetry.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.*
import kotlin.math.*

/** 每次连接独立socket和解析器；锁内发布，旧会话不能污染新会话。 */
class DirectMavlinkConnection(@Suppress("UNUSED_PARAMETER") context: Context,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val policy: ConnectionPolicy = ConnectionPolicy()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSnapshot = MutableStateFlow(ConnectionSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()
    private val mutableState = MutableStateFlow("disconnected")
    val state = mutableState.asStateFlow()
    private val mutableTelemetry = MutableStateFlow(VehicleTelemetry())
    val telemetry = mutableTelemetry.asStateFlow()
    private var generation = 0
    private var socket: UdpTransport? = null
    private var job: Job? = null
    @Synchronized fun disconnect() {
        generation++; socket?.close(); socket = null; job?.cancel(); job = null
        publish(generation, "disconnected", VehicleTelemetry(session = generation))
    }
    @Synchronized fun connect(preset: ConnectionPreset) {
        disconnect()
        val token = generation
        publish(token, "connecting", VehicleTelemetry(session = token))
        job = scope.launch { receive(preset, token) }
    }
    private fun now() = clock()
    @Synchronized private fun publish(token: Int, state: String, value: VehicleTelemetry) {
        if (token != generation) return
        mutableSnapshot.value = ConnectionSnapshot(state, value)
        mutableTelemetry.value = value; mutableState.value = state
    }
    private suspend fun receive(preset: ConnectionPreset, token: Int) {
        val transport = UdpTransport()
        var vehicle = VehicleTelemetry(session = token)
        var peer: SocketAddress? = null
        var system = -1
        var component = -1
        var heartbeatAt = -1L
        var sentAt = -1L
        var sequence = 0
        try {
            transport.open(preset.localPort, policy.receivePollMs)
            synchronized(this) { if (token != generation) { transport.close(); return }; socket = transport }
            val host = if (preset.id == "sitl" && android.os.Build.HARDWARE in setOf("ranchu", "goldfish") && preset.remoteHost == "127.0.0.1") "10.0.2.2" else preset.remoteHost
            val fixed = InetSocketAddress(host, preset.remotePort)
            // 每个数据报独立解析；UDP不承诺跨数据报的字节流连续性。
            while (currentCoroutineContext().isActive) {
                try {
                    val packet = transport.receive()
                    if (heartbeatAt >= 0 && now() - heartbeatAt > policy.heartbeatTimeoutMs) {
                        peer = null; system = -1; component = -1; heartbeatAt = -1
                        vehicle = VehicleTelemetry(session = token)
                        publish(token, "connecting", vehicle)
                    }
                    if (!(packet.address.isLoopbackAddress && packet.port == transport.localPort)) {
                        fun consume(bytes: ByteArray) {
                            com.fairland.fgroundcontrol.connection.protocol.MavlinkDecoder.decode(bytes)?.let { frame ->
                                val msg = frame.unpack()
                                if (msg is msg_heartbeat && msg.autopilot.toInt() == 12 && frame.compid == 1 && frame.sysid in 1..254 && msg.type.toInt() != 6) {
                                    if (peer == null || (peer == packet.socketAddress && system == frame.sysid && component == frame.compid)) {
                                        if (peer == null) { vehicle = VehicleTelemetry(session = token, connected = true); system = frame.sysid; component = frame.compid; peer = packet.socketAddress }
                                        heartbeatAt = now()
                                    }
                                }
                                if (peer == packet.socketAddress && frame.sysid == system && frame.compid == component && heartbeatAt >= 0) {
                                    vehicle = com.fairland.fgroundcontrol.connection.protocol.TelemetryMapper.map(vehicle, msg, now())
                                    publish(token, "connected", vehicle)
                                }
                            }
                        }
                        val frames = MavlinkFrames(::consume, ::consume)
                        frames.accept(packet.data, packet.length)
                    }
                } catch (_: SocketTimeoutException) { }
                val at = now()
                if (heartbeatAt >= 0 && at - heartbeatAt > policy.heartbeatTimeoutMs) {
                    peer = null; system = -1; component = -1; heartbeatAt = -1
                    vehicle = VehicleTelemetry(session = token)
                    publish(token, "connecting", vehicle)
                }
                if (sentAt < 0 || at - sentAt >= policy.heartbeatIntervalMs) {
                    val heartbeat = msg_heartbeat().apply { type = 6; autopilot = 8; mavlink_version = 3 }
                    val frame = heartbeat.pack().apply { sysid = policy.systemId; compid = policy.componentId; seq = sequence++ and 255 }
                    val bytes = frame.encodePacket()
                    val targets = setOfNotNull(fixed.takeUnless { it.address?.isLoopbackAddress == true && it.port == transport.localPort }, peer)
                    targets.forEach { target -> transport.send(bytes, target) }
                    sentAt = at
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            publish(token, "error: ${error.javaClass.simpleName}", VehicleTelemetry(session = token))
        } finally { transport.close(); synchronized(this) { if (socket === transport) socket = null } }
    }
}
