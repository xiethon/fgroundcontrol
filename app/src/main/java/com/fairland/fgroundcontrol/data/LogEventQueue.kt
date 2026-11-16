package com.fairland.fgroundcontrol.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

/** 普通队列不能占用飞行事件保留槽；崩溃现场另走同步写入。 */
class LogEventQueue {
    private val ordinary = Channel<Pair<LogKind, String>>(96)
    private val flight = Channel<Pair<LogKind, String>>(32)
    fun offer(kind: LogKind, message: String): Boolean =
        (if (kind == LogKind.FLIGHT) flight else ordinary).trySend(kind to message).isSuccess
    suspend fun take(): Pair<LogKind, String> = flight.tryReceive().getOrNull() ?: select {
        flight.onReceive { it }
        ordinary.onReceive { it }
    }
}
