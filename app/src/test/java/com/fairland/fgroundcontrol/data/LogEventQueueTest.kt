package com.fairland.fgroundcontrol.data

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class LogEventQueueTest {
    @Test fun ordinaryOverloadPreservesFlightCapacityAndPriority() = runBlocking {
        val queue = LogEventQueue()
        repeat(96) { assertTrue(queue.offer(LogKind.APP, "$it")) }
        assertFalse(queue.offer(LogKind.APP, "overflow"))
        repeat(32) { assertTrue(queue.offer(LogKind.FLIGHT, "$it")) }
        assertFalse(queue.offer(LogKind.FLIGHT, "overflow"))
        repeat(32) { assertEquals(LogKind.FLIGHT to "$it", queue.take()) }
        repeat(96) { assertEquals(LogKind.APP to "$it", queue.take()) }
    }
    @Test fun emptyQueueWaitCanBeCancelled() = runBlocking {
        val queue = LogEventQueue()
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) { queue.take(); fail("Unexpected event") }
        waiter.cancelAndJoin()
        assertTrue(waiter.isCancelled)
    }
}
