package com.fairland.fgroundcontrol.data

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class ReplayPlayerTest {
    @Test fun relativeTimingAndCompletion() = runBlocking {
        val waits = mutableListOf<Long>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val player = ReplayPlayer(scope) { waits.add(it) }
            player.play(listOf(ReplaySample(0, 30.0, 120.0), ReplaySample(1500, 31.0, 121.0)))
            assertEquals(listOf(0L, 1500L), waits)
            assertEquals("completed", player.state.value.phase)
            assertEquals(2, player.state.value.index)
        } finally { scope.cancel() }
    }
    @Test fun stopPreventsDelayedSampleAndNewRunReplacesOld() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val gate = CompletableDeferred<Unit>()
        try {
            val player = ReplayPlayer(scope) { if (it > 0) gate.await() }
            player.play(listOf(ReplaySample(100, 30.0, 120.0)))
            player.stop()
            player.play(listOf(ReplaySample(0, 40.0, 110.0)))
            gate.complete(Unit)
            yield()
            assertEquals("completed", player.state.value.phase)
            assertEquals(40.0, player.state.value.sample!!.latitude, 0.0)
        } finally { scope.cancel() }
    }
    @Test fun slowImportCannotRestartAfterStopOrOverwriteNewerImport() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val player = ReplayPlayer(scope) { }
            val old = player.beginLoad()
            player.stop()
            assertFalse(player.playIfCurrent(old, listOf(ReplaySample(0, 30.0, 120.0))))
            val first = player.beginLoad()
            val latest = player.beginLoad()
            assertTrue(player.playIfCurrent(latest, listOf(ReplaySample(0, 40.0, 110.0))))
            assertFalse(player.playIfCurrent(first, listOf(ReplaySample(0, 30.0, 120.0))))
            assertEquals(40.0, player.state.value.sample!!.latitude, 0.0)
        } finally { scope.cancel() }
    }
    @Test fun fileRejectsEmptyOversizeAndExcessDuration() {
        val source = ReplaySource()
        assertThrows(IllegalArgumentException::class.java) { source.read("".reader()) }
        assertThrows(IllegalArgumentException::class.java) { source.read("x".repeat(1048577).reader()) }
        assertThrows(IllegalArgumentException::class.java) { source.read(sequenceOf("86400001,30,120")) }
        assertEquals(2, source.read("0,30,120\n100,31,121\n".reader()).size)
    }
}
