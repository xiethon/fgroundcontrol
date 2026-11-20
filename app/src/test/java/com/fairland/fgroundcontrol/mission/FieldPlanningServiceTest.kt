package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FieldPlanningServiceTest {
    private fun point(x: Double,y: Double) = BoundaryPoint(y/111319.49079327358,x/111319.49079327358)
    private fun draft() = BoundaryDraft(7, listOf(point(0.0,0.0),point(100.0,0.0),point(100.0,60.0),point(0.0,60.0)), FieldPlanningParameters(30.0,5.0,10.0))
    private fun validOutput() = doubleArrayOf(0.0,100.0,1000.0,0.0,5.0,100.0,5.0)
    private fun planner(output: DoubleArray = validOutput()) = FieldPlanningService(FieldPlanningEngine { _,_,_ -> output })

    @Test fun resultBindsRevisionParametersAndCoordinates() = runBlocking<Unit> {
        val input = draft()
        val result = planner().plan(input)
        assertEquals(input.revision,result.revision)
        assertEquals(FieldPlanningService.fingerprint(input), result.fingerprint)
        assertEquals(100.0,result.distanceMeters,0.0)
        assertEquals(point(0.0,5.0).latitude,result.points[0].latitude,1e-10)
        assertThrows(IllegalArgumentException::class.java) {
            FieldPlanningService.validateResult(input.copy(parameters=input.parameters!!.copy(speed=6.0)),result)
        }
        assertThrows(IllegalArgumentException::class.java) { FieldPlanningService.validateResult(input.copy(revision=8),result) }
    }
    @Test fun corruptOutputAndConcaveShortcutAreRejected() = runBlocking<Unit> {
        for (output in listOf(doubleArrayOf(), doubleArrayOf(0.0,1.0,0.0),validOutput().apply { this[1]=Double.NaN },
            validOutput().apply { this[1]=1.0 }, validOutput().apply { this[3]=200.0 },doubleArrayOf(5.0,0.0,0.0))) {
            assertTrue(runCatching { planner(output).plan(draft()) }.isFailure)
        }
        val concave = draft().copy(points=listOf(point(0.0,0.0),point(100.0,0.0),point(100.0,30.0),point(60.0,30.0),point(60.0,60.0),point(0.0,60.0)))
        val shortcut = doubleArrayOf(0.0,kotlin.math.hypot(45.0,25.0),0.0,95.0,25.0,50.0,50.0)
        assertTrue(runCatching { planner(shortcut).plan(concave) }.isFailure)
    }
    @Test fun complexityIsRejectedBeforeEngine() = runBlocking<Unit> {
        val service=FieldPlanningService(FieldPlanningEngine { _,_,_ -> error("must not call engine") })
        val tooWide=draft().copy(points=listOf(point(0.0,0.0),point(6000.0,0.0),point(6000.0,60.0),point(0.0,60.0)))
        val failure=runCatching { service.plan(tooWide) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        FieldPlanningParameters(1.0,1.0,1.5,99.0)
        assertThrows(IllegalArgumentException::class.java) { FieldPlanningParameters(1.0,1.0,1.5,99.1) }
    }
    @Test fun v1MigrationV2RoundTripAndStaleAlgorithm() = runBlocking<Unit> {
        val root=Files.createTempDirectory("field-draft").toFile()
        try {
            val foundation=FoundationStore(root); val store=BoundaryDraftStore(foundation)
            foundation.write("field-boundary-draft","boundary-v1\n2\n0.0,0.0\n0.0,0.01\n")
            assertEquals(2,store.load()!!.points.size)
            assertNull(store.load()!!.parameters)
            val input=draft();val complete=input.copy(plan=planner().plan(input))
            store.save(complete);assertEquals(complete,store.load())
            foundation.write("field-boundary-draft",foundation.read("field-boundary-draft")!!.replace(FieldPlanningService.ALGORITHM,"old-algorithm"))
            assertEquals(input,store.load())
            store.save(complete)
            foundation.write("field-boundary-draft",foundation.read("field-boundary-draft")!!.replace(complete.plan!!.fingerprint,"bad-hash"))
            assertThrows(IllegalArgumentException::class.java) { store.load() }
        } finally { root.deleteRecursively() }
    }
    @Test fun editingInvalidatesPlanAndFailedWriteKeepsReliableVersion() = runBlocking<Unit> {
        val root=Files.createTempDirectory("field-version").toFile()
        try {
            val store=BoundaryDraftStore(FoundationStore(root))
            val complete=draft().let { it.copy(plan=planner().plan(it)) }
            store.save(complete)
            assertNull(complete.append(point(-1.0,1.0)).plan)
            // 验证非法版本在替换文件前拒绝，不损坏已有可靠结果。
            assertThrows(IllegalArgumentException::class.java) { store.save(complete.copy(revision=8)) }
            assertEquals(complete,store.load())
        } finally { root.deleteRecursively() }
    }
    @Test fun lateNativeResultCannotOverwriteNewParameters() = runBlocking<Unit> {
        val root=Files.createTempDirectory("field-late").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val finished=CountDownLatch(1)
        try {
            val store=BoundaryDraftStore(FoundationStore(root));store.save(draft())
            val service=FieldPlanningService(FieldPlanningEngine { _,_,_ ->
                entered.countDown();check(release.await(5,TimeUnit.SECONDS));finished.countDown();validOutput()
            })
            val editor=BoundaryEditor({store},scope,service)
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.generate()
            assertTrue(withContext(Dispatchers.IO) { entered.await(5,TimeUnit.SECONDS) })
            editor.configure(draft().parameters!!.copy(spacing=12.0))
            withTimeout(5000) { editor.state.first { !it.busy && it.draft?.revision==8L } }
            release.countDown()
            assertTrue(withContext(Dispatchers.IO) { finished.await(5,TimeUnit.SECONDS) })
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertNull(editor.state.value.draft!!.plan)
            assertFalse(editor.state.value.planning)
            assertEquals(12.0,store.load()!!.parameters!!.spacing,0.0)
            assertNull(store.load()!!.plan)
        } finally { release.countDown();scope.cancel();root.deleteRecursively() }
    }
    @Test fun generationPersistsAndReopensWithoutNativeRecompute() = runBlocking<Unit> {
        val root=Files.createTempDirectory("field-generate").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root));store.save(draft())
            val editor=BoundaryEditor({store},scope,planner())
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.generate()
            val state=withTimeout(5000) { editor.state.first { it.draft?.plan!=null } }
            assertEquals(state.draft,store.load())
            val restarted=BoundaryEditor({store},scope,FieldPlanningService(FieldPlanningEngine { _,_,_ -> error("must not recompute") }))
            assertEquals(state.draft,withTimeout(5000) { restarted.state.first { it.loaded } }.draft)
        } finally { scope.cancel();root.deleteRecursively() }
    }
    @Test fun failedRecalculationRetainsLastReliableRouteAndCanRetry() = runBlocking<Unit> {
        val root=Files.createTempDirectory("field-retry").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root))
            val input=draft();val complete=input.copy(plan=planner().plan(input));store.save(complete)
            val attempts=java.util.concurrent.atomic.AtomicInteger()
            val failures=java.util.concurrent.ConcurrentLinkedQueue<String>()
            val service=FieldPlanningService(FieldPlanningEngine { _,_,_ ->
                if(attempts.incrementAndGet()==1) error("fixture planner failure")
                validOutput()
            })
            val editor=BoundaryEditor({store},scope,service) { operation,_ -> failures.add(operation) }
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.generate()
            val failed=withTimeout(5000) { editor.state.first { it.planningError } }
            assertEquals(complete,failed.draft);assertEquals(complete,store.load())
            editor.generate()
            val recovered=withTimeout(5000) { editor.state.first { !it.planning && !it.planningError && it.draft?.plan!=null } }
            assertEquals(complete,recovered.draft)
            assertEquals(2,attempts.get());assertEquals(listOf("plan revision=7"),failures.toList())
        } finally { scope.cancel();root.deleteRecursively() }
    }

}
