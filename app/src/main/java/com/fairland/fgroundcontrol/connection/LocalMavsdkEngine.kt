package com.fairland.fgroundcontrol.connection

import io.mavsdk.MavsdkEventQueue
import io.mavsdk.mavsdkserver.MavsdkServer
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.CompositeDisposable
import com.fairland.fgroundcontrol.telemetry.Reading
import com.fairland.fgroundcontrol.telemetry.VehicleTelemetry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 仅连接原型，无飞行命令。由进程容器持有，页面不能创建独立实例。 */
internal class LocalMavsdkEngine {
    private val mutableState = MutableStateFlow("disconnected")
    val state = mutableState.asStateFlow()
    private val mutableTelemetry = MutableStateFlow(VehicleTelemetry())
    val telemetry = mutableTelemetry.asStateFlow()
    private val telemetrySubscriptions = CompositeDisposable()
    private var generation = 0L
    private var telemetryGeneration = 0L
    private var server: MavsdkServer? = null
    private var system: io.mavsdk.System? = null
    private var bridge: UdpPresetBridge? = null
    private var subscription: Disposable? = null

    fun connect(preset: ConnectionPreset) {
        MavsdkEventQueue.executor().execute {
            release()
            val current = ++generation
            mutableState.value = "connecting"
            try {
                val transport = UdpPresetBridge(preset) { error ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) {
                            mutableState.value = "error: ${error.javaClass.simpleName}"
                            release()
                        }
                    }
                }
                bridge = transport
                val sdkPort = java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
                transport.start(sdkPort)
                val nativeServer = MavsdkServer()
                server = nativeServer
                val rpcPort = nativeServer.run("udpin://127.0.0.1:$sdkPort", 0)
                check(rpcPort > 0) { "MAVSDK server did not start" }
                val client = io.mavsdk.System("127.0.0.1", rpcPort)
                system = client
                subscription = client.core.connectionState.subscribe({ connection ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) {
                            if (connection.isConnected && mutableState.value != "connected") subscribeTelemetry(client, current)
                            if (!connection.isConnected) { ++telemetryGeneration; telemetrySubscriptions.clear() }
                            mutableState.value = if (connection.isConnected) "connected" else "disconnected"
                            if (!connection.isConnected) mutableTelemetry.value = VehicleTelemetry()
                        }
                    }
                }, { error ->
                    MavsdkEventQueue.executor().execute {
                        if (current == generation) {
                            mutableState.value = "error: ${error.javaClass.simpleName}"
                            release()
                        }
                    }
                })
            } catch (error: Exception) {
                mutableState.value = "error: ${error.javaClass.simpleName}"
                release()
            } catch (error: UnsatisfiedLinkError) {
                mutableState.value = "unsupported ABI"
                release()
            }
        }
    }
    fun disconnect() {
        MavsdkEventQueue.executor().execute { release(); mutableState.value = "disconnected" }
    }
    private fun subscribeTelemetry(client: io.mavsdk.System, current: Long) {
        val telemetrySession = ++telemetryGeneration
        val telemetry = client.telemetry
        fun value(topic: String, values: List<Double?> = emptyList(), text: String? = null) {
            val received = android.os.SystemClock.elapsedRealtime()
            MavsdkEventQueue.executor().execute {
                if (current == generation && telemetrySession == telemetryGeneration) mutableTelemetry.value = mutableTelemetry.value.copy(
                    readings = mutableTelemetry.value.readings + (topic to Reading(values, text?.take(512), received))
                )
            }
        }
        fun <T : Any> observe(topic: String, source: io.reactivex.Flowable<T>, consume: (T) -> Unit) {
            telemetrySubscriptions.add(source.subscribe(consume) { value(topic, text = "UNAVAILABLE: ${it.javaClass.simpleName}") })
        }
        observe("position", telemetry.position) {
            val lat = it.latitudeDeg.takeIf { v -> v.isFinite() && v in -90.0..90.0 }
            val lon = it.longitudeDeg.takeIf { v -> v.isFinite() && v in -180.0..180.0 }
            value("position", listOf(lat, lon))
            value("altitude", listOf(it.relativeAltitudeM.toDouble().takeIf(Double::isFinite), it.absoluteAltitudeM.toDouble().takeIf(Double::isFinite)))
        }
        observe("velocity", telemetry.velocityNed) {
            value("velocity", listOf(it.northMS.toDouble(), it.eastMS.toDouble(), it.downMS.toDouble()).map { v -> v.takeIf(Double::isFinite) })
            value("speed", listOf(kotlin.math.hypot(it.northMS.toDouble(), it.eastMS.toDouble()).takeIf(Double::isFinite), (-it.downMS.toDouble()).takeIf(Double::isFinite)))
        }
        observe("attitude", telemetry.attitudeEuler) { value("attitude", listOf(it.rollDeg.toDouble(), it.pitchDeg.toDouble(), it.yawDeg.toDouble()).map { v -> v.takeIf(Double::isFinite) }) }
        observe("battery", telemetry.battery) {
            value("battery", listOf(it.remainingPercent.toDouble().takeIf { v -> v.isFinite() && v in 0.0..100.0 }), "id=${it.id}")
            value("voltage", listOf(it.voltageV.toDouble().takeIf { v -> v.isFinite() && v >= 0 }))
        }
        observe("gps", telemetry.gpsInfo) { value("gps", listOf(it.numSatellites.toDouble().takeIf { v -> v >= 0 }), it.fixType.name) }
        observe("health", telemetry.health) { value("health", listOf(it.isGyrometerCalibrationOk, it.isAccelerometerCalibrationOk, it.isMagnetometerCalibrationOk, it.isLocalPositionOk, it.isGlobalPositionOk, it.isHomePositionOk, it.isArmable).map { flag -> if (flag) 1.0 else 0.0 }) }
        observe("mode", telemetry.flightMode) { value("mode", text = it.name) }
        observe("armed", telemetry.armed) { value("armed", listOf(if (it) 1.0 else 0.0)) }
        observe("status", telemetry.statusText) { value("status", text = "${it.type.name}: ${it.text}") }
        listOf("position" to telemetry.setRatePosition(2.0), "velocity" to telemetry.setRateVelocityNed(2.0), "attitude" to telemetry.setRateAttitudeEuler(5.0), "battery" to telemetry.setRateBattery(1.0), "gps" to telemetry.setRateGpsInfo(1.0)).forEach { (topic, request) ->
            telemetrySubscriptions.add(request.subscribe({}, { value("rate_status", text = "$topic rate request failed: ${it.javaClass.simpleName}") }))
        }
    }
    private fun release() {
        ++generation
        ++telemetryGeneration
        telemetrySubscriptions.clear()
        mutableTelemetry.value = VehicleTelemetry()
        bridge?.close(); bridge = null
        subscription?.dispose(); subscription = null
        system?.dispose(); system = null
        server?.stop(); server?.destroy(); server = null
    }
}
