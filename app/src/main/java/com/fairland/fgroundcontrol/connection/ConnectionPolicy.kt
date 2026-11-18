package com.fairland.fgroundcontrol.connection

import com.fairland.fgroundcontrol.telemetry.VehicleTelemetry

/** 同一快照中描述连接和遥测；旧StateFlow仅为现有调用方兼容投影。 */
data class ConnectionSnapshot(val phase: String = "disconnected", val vehicle: VehicleTelemetry = VehicleTelemetry())

data class ConnectionPolicy(
    val heartbeatTimeoutMs: Long = 3000,
    val heartbeatIntervalMs: Long = 1000,
    val receivePollMs: Int = 250,
    val systemId: Int = 255,
    val componentId: Int = 190
) {
    init {
        require(heartbeatTimeoutMs > heartbeatIntervalMs && heartbeatIntervalMs > 0)
        require(receivePollMs > 0 && receivePollMs <= heartbeatTimeoutMs)
        require(systemId in 1..255 && componentId in 1..255)
    }
}
