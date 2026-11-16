package com.fairland.fgroundcontrol.connection

data class ConnectionPreset(val id: String, val label: String, val localPort: Int, val remoteHost: String, val remotePort: Int)

object ConnectionPresets {
    val all = listOf(
        ConnectionPreset("p401d", "P401D RC UDP", 14551, "127.0.0.1", 14550),
        ConnectionPreset("local", "Local UDP 14551", 14551, "127.0.0.1", 14551),
        ConnectionPreset("rc7", "RC7 Remote Control UDP", 14550, "192.168.144.20", 19856),
        ConnectionPreset("mk32", "MK32 Remote Control UDP", 14550, "192.168.144.12", 19856),
        ConnectionPreset("sitl", "PX4 SITL", 14540, "127.0.0.1", 18570)
    )
    val default = all.first { it.id == "local" }
    fun resolve(id: String?) = all.firstOrNull { it.id == id } ?: default
}
