package com.fairland.fgroundcontrol.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReplayState(val phase: String = "idle", val index: Int = 0, val count: Int = 0, val sample: ReplaySample? = null)

/** 只更新独立回放状态，绝不引用真实连接。 */
class ReplayPlayer(private val scope: CoroutineScope, private val wait: suspend (Long) -> Unit = { delay(it) }) {
    private val mutableState = MutableStateFlow(ReplayState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var loadGeneration = 0L
    @Synchronized fun beginLoad(): Long = ++loadGeneration
    @Synchronized fun playIfCurrent(token: Long, samples: List<ReplaySample>): Boolean {
        if (token != loadGeneration) return false
        play(samples)
        return true
    }
    private var job: Job? = null

    @Synchronized fun play(samples: List<ReplaySample>) {
        require(samples.isNotEmpty() && samples.size <= 10000)
        require(samples.zipWithNext().all { (a, b) -> b.offsetMs >= a.offsetMs })
        val snapshot = samples.toList()
        job?.cancel()
        val current = ++generation
        mutableState.value = ReplayState("playing", count = snapshot.size)
        job = scope.launch {
            var previous = 0L
            snapshot.forEachIndexed { index, sample ->
                wait(sample.offsetMs - previous)
                ensureActive()
                synchronized(this@ReplayPlayer) {
                    if (current != generation) return@launch
                    mutableState.value = ReplayState("playing", index + 1, snapshot.size, sample)
                }
                previous = sample.offsetMs
            }
            synchronized(this@ReplayPlayer) {
                if (current == generation) mutableState.value = mutableState.value.copy(phase = "completed")
            }
        }
    }
    @Synchronized fun stop() {
        ++generation
        ++loadGeneration
        job?.cancel(); job = null
        mutableState.value = mutableState.value.copy(phase = "stopped")
    }
}
