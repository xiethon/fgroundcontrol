package com.fairland.fgroundcontrol

import com.fairland.fgroundcontrol.mission.*
import com.fairland.fgroundcontrol.data.FoundationStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

/** 无界面JNI数值回归，不发送飞行指令。 */
internal object NativePlannerProbe {
    fun verify(cacheRoot: File) {
        val rectangle = doubleArrayOf(0.0, 0.0, 100.0, 0.0, 100.0, 60.0, 0.0, 60.0)
        fun valid(points: DoubleArray, inset: Double = 0.0): DoubleArray {
            val result = NativeFieldPlanner.plan(points, 10.0, inset)
            check(result.size >= 7 && result[0] == 0.0 && result.all(Double::isFinite))
            return result
        }
        val route = valid(rectangle)
        check(route.size == 27 && abs(route[1] - 650.0) < 1e-6)
        check((4 until route.size step 2).map { (route[it] * 1000).roundToLong() }.toSet() == setOf(5000L, 15000L, 25000L, 35000L, 45000L, 55000L))
        check(abs(valid(rectangle, 2.0)[1] - 626.0) < 1e-6)
        check(NativeFieldPlanner.plan(rectangle, 10.0, 40.0).let { it.size == 3 && it[0] != 0.0 })
        valid(doubleArrayOf(0.0, 0.0, 80.0, 60.0, 44.0, 108.0, -36.0, 48.0))
        valid(doubleArrayOf(0.0, 0.0, 100.0, 0.0, 100.0, 30.0, 60.0, 30.0, 60.0, 60.0, 0.0, 60.0))
        for (invalid in listOf(doubleArrayOf(0.0, 0.0), rectangle.copyOf().apply { this[0] = Double.NaN },
            doubleArrayOf(0.0, 0.0, 100.0, 60.0, 0.0, 60.0, 100.0, 0.0))) {
            check(runCatching { NativeFieldPlanner.plan(invalid, 10.0, 0.0) }.exceptionOrNull() is IllegalArgumentException)
        }
        check(runCatching { NativeFieldPlanner.plan(DoubleArray(258), 10.0, 0.0) }.exceptionOrNull() is IllegalArgumentException)
        check(runCatching { NativeFieldPlanner.plan(doubleArrayOf(0.0,0.0,6000.0,0.0,6000.0,60.0,0.0,60.0),10.0,0.0) }.exceptionOrNull() is IllegalArgumentException)
        check(runCatching { NativeFieldPlanner.plan(rectangle, 0.0, 0.0) }.exceptionOrNull() is IllegalArgumentException)
        check(runCatching { NativeFieldPlanner.plan(doubleArrayOf(0.0,0.0,40.0,0.0,40.0,18.0,60.0,18.0,60.0,0.0,100.0,0.0,100.0,40.0,60.0,40.0,60.0,22.0,40.0,22.0,40.0,40.0,0.0,40.0),10.0,3.0) }.exceptionOrNull() is IllegalStateException)
        check(valid(rectangle).contentEquals(route))
        runBlocking {
            val root=File(cacheRoot,"native-field-probe-${java.util.UUID.randomUUID()}")
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
            try {
                fun point(x: Double,y: Double)=BoundaryPoint(22.0+Math.toDegrees(y/6378137.0),113.0+Math.toDegrees(x/(6378137.0*kotlin.math.cos(Math.toRadians(22.0)))))
                val draft=BoundaryDraft(3,listOf(point(0.0,0.0),point(100.0,0.0),point(100.0,60.0),point(0.0,60.0)),FieldPlanningParameters(30.0,5.0,10.0))
                val store=BoundaryDraftStore(FoundationStore(root));store.save(draft)
                val editor=BoundaryEditor({store},scope)
                withTimeout(10000) { editor.state.first { it.loaded } }
                editor.generate()
                val generated=withTimeout(10000) { editor.state.first { it.draft?.plan!=null || it.planningError || it.error } }
                check(!generated.error && !generated.planningError)
                val plan=checkNotNull(generated.draft?.plan)
                check(plan.points.size==12 && abs(plan.distanceMeters-650)<0.01)
                check(store.load()==generated.draft)
                editor.configure(draft.parameters!!.copy(spacing=12.0))
                val changed=withTimeout(10000) { editor.state.first { !it.busy && it.draft?.revision==4L } }
                check(changed.draft!!.plan==null && store.load()!!.plan==null)
                editor.generate()
                val recalculated=withTimeout(10000) { editor.state.first { it.draft?.plan!=null || it.planningError || it.error } }
                check(!recalculated.error && !recalculated.planningError && recalculated.draft?.plan?.revision==4L)
                check(store.load()==recalculated.draft)
            } finally { scope.cancel();root.deleteRecursively() }
        }
    }
}
