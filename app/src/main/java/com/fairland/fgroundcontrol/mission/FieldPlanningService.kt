package com.fairland.fgroundcontrol.mission

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.*

/** 只记录本地规划结果，不包含上传或执行事实。 */
data class FieldPlan(
    val revision: Long,
    val fingerprint: String,
    val algorithm: String,
    val points: List<BoundaryPoint>,
    val distanceMeters: Double,
    val estimatedCoverageAreaSquareMeters: Double,
)

fun interface FieldPlanningEngine {
    fun plan(vertices: DoubleArray, spacing: Double, inset: Double): DoubleArray
}

/** 单实例串行JNI；取消不能中断C++，但取消后的结果不得继续发布。 */
class FieldPlanningService(
    private val engine: FieldPlanningEngine = FieldPlanningEngine { points, spacing, inset -> NativeFieldPlanner.plan(points, spacing, inset) },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()
    suspend fun plan(draft: BoundaryDraft): FieldPlan = withContext(dispatcher) {
        val snapshot = draft.copy(points = draft.points.toList(), plan = null)
        val parameters = requireNotNull(snapshot.parameters) { "Planning parameters required" }
        val local = FieldBoundaryValidation.projectAndValidate(snapshot.points)
        require(local.size <= 128) { "At most 128 boundary points" }
        val minX = local.minOf { it.east }; val maxX = local.maxOf { it.east }
        val minY = local.minOf { it.north }; val maxY = local.maxOf { it.north }
        val diagonal = hypot(maxX - minX, maxY - minY)
        require(diagonal <= 5000 && local.all { abs(it.east) <= 5000 && abs(it.north) <= 5000 } &&
            (ceil(diagonal / parameters.spacing) + 1) * local.size <= 8192) { "Planning complexity limit exceeded" }
        val coordinates = local.flatMap { listOf(it.east, it.north) }.toDoubleArray()
        lock.withLock {
                coroutineContext.ensureActive()
                val output = engine.plan(coordinates, parameters.spacing, parameters.inset)
                coroutineContext.ensureActive()
                check(output.size >= 3 && output[0] == 0.0) { "Planner could not generate a route" }
                check(output.size in 7..8195 && (output.size - 3) % 2 == 0 && output.all(Double::isFinite)) { "Invalid planner output" }
                val origin = snapshot.points.first()
                val route = (3 until output.size step 2).map { index -> unproject(origin, output[index], output[index + 1]) }
                val result = FieldPlan(snapshot.revision, fingerprint(snapshot), ALGORITHM, route, output[1], output[2])
                validateResult(snapshot, result)
                result
        }
    }

    companion object {
        const val ALGORITHM = "field-a73f9c6-fgc4"
        fun fingerprint(draft: BoundaryDraft): String {
            val text = buildString {
                draft.parameters?.let { append(it.altitude).append(',').append(it.speed).append(',').append(it.spacing).append(',').append(it.inset).append(',').append(it.obstacleMargin) }
                append('\n')
                draft.points.forEach { append(it.latitude).append(',').append(it.longitude).append('\n') }
            }
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        fun validateResult(draft: BoundaryDraft, result: FieldPlan) {
            require(draft.parameters != null && result.revision == draft.revision && result.fingerprint == fingerprint(draft) && result.algorithm == ALGORITHM) { "Stale planning result" }
            require(result.points.size in 2..4096 && result.distanceMeters.isFinite() && result.distanceMeters > 0 &&
                result.estimatedCoverageAreaSquareMeters.isFinite() && result.estimatedCoverageAreaSquareMeters >= 0) { "Invalid result statistics" }
            val boundary = FieldBoundaryValidation.projectAndValidate(draft.points)
            val origin = draft.points.first()
            val local = result.points.map { project(origin, it) }
            require(local.all { abs(it.east) <= 5000 && abs(it.north) <= 5000 }) { "Invalid result extent" }
            // 独立领域防线：端点及连接段不能穿出原边界；内缩安全区另由C++逐段校验。
            require(local.zipWithNext().all { (a,b) -> segmentInside(boundary, a, b) }) { "Route outside boundary" }
            val distance = local.zipWithNext().sumOf { (a,b) -> hypot(a.east-b.east,a.north-b.north) }
            require(abs(distance - result.distanceMeters) <= max(0.05, distance * 1e-6)) { "Inconsistent route length" }
        }
        private const val RADIUS = 6378137.0
        private fun unproject(origin: BoundaryPoint, east: Double, north: Double): BoundaryPoint {
            check(abs(east) <= 5000 && abs(north) <= 5000) { "Invalid planner coordinate" }
            return BoundaryPoint(origin.latitude + Math.toDegrees(north / RADIUS), origin.longitude + Math.toDegrees(east / (RADIUS * cos(Math.toRadians(origin.latitude)))))
        }
        private fun project(origin: BoundaryPoint, point: BoundaryPoint) = LocalBoundaryPoint(
            RADIUS * Math.toRadians(point.longitude-origin.longitude) * cos(Math.toRadians(origin.latitude)),
            RADIUS * Math.toRadians(point.latitude-origin.latitude))
        private fun segmentInside(polygon: List<LocalBoundaryPoint>, a: LocalBoundaryPoint, b: LocalBoundaryPoint): Boolean {
            // 按边界交点分段后检测每个开区间，凹角不能用少量固定采样漏过。
            val dx=b.east-a.east; val dy=b.north-a.north
            val cuts=mutableListOf(0.0,1.0)
            for (i in polygon.indices) {
                val c=polygon[i]; val d=polygon[(i+1)%polygon.size]
                val ex=d.east-c.east; val ey=d.north-c.north
                val determinant=dx*ey-dy*ex
                if(abs(determinant)<1e-10) continue
                val t=((c.east-a.east)*ey-(c.north-a.north)*ex)/determinant
                val u=((c.east-a.east)*dy-(c.north-a.north)*dx)/determinant
                if(t in 0.0..1.0 && u in 0.0..1.0) cuts.add(t)
            }
            fun inside(t: Double): Boolean {
                val x=a.east+t*dx; val y=a.north+t*dy
                var inside=false
                for(i in polygon.indices) {
                    val c=polygon[i];val d=polygon[(i+1)%polygon.size]
                    val ex=d.east-c.east; val ey=d.north-c.north
                    val length=hypot(ex,ey)
                    val u=if(length==0.0) 0.0 else ((x-c.east)*ex+(y-c.north)*ey)/(length*length)
                    val clamped=u.coerceIn(0.0,1.0)
                    if(hypot(x-c.east-clamped*ex,y-c.north-clamped*ey)<=0.0001) return true
                    if((c.north>y)!=(d.north>y) && x < c.east+(y-c.north)*ex/ey) inside=!inside
                }
                return inside
            }
            return inside(0.0) && inside(1.0) && cuts.sorted().zipWithNext().all { (l,r) -> inside((l+r)/2) }
        }
    }
}
