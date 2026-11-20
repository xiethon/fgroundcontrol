package com.fairland.fgroundcontrol.mission

import kotlin.math.*

/** 规划输入契约；UI步长不作为算法强制量化。 */
data class FieldPlanningParameters(val altitude: Double, val speed: Double, val spacing: Double, val inset: Double = 0.0, val obstacleMargin: Double = 1.0) {
    init {
        require(obstacleMargin.isFinite() && obstacleMargin in 1.0..5.0)
        require(altitude.isFinite() && altitude in 1.0..120.0)
        require(speed.isFinite() && speed in 1.0..12.0)
        require(inset.isFinite() && inset in 0.0..99.0)
        require(spacing.isFinite() && spacing in 1.5..70.0)
    }
}
data class LocalBoundaryPoint(val east: Double,val north: Double)

/** 只校验/投影，不修补输入，不产生假规划结果。 */
object FieldBoundaryValidation {
    private const val radius=6378137.0
    private const val duplicateTolerance=0.05
    private const val epsilon=1e-8
    fun projectAndValidate(points: List<BoundaryPoint>): List<LocalBoundaryPoint> {
        require(points.size in 3..128) { "Between 3 and 128 boundary points are required" }
        val origin=points.first()
        require(points.all { abs(it.latitude)<85.0 && abs(it.longitude-origin.longitude)<180.0 }) { "Unsupported polar or antimeridian boundary" }
        val latitude=Math.toRadians(origin.latitude)
        val local=points.map { LocalBoundaryPoint(radius*Math.toRadians(it.longitude-origin.longitude)*cos(latitude),radius*Math.toRadians(it.latitude-origin.latitude)) }
        for(i in local.indices) for(j in i+1 until local.size) {
            require(hypot(local[i].east-local[j].east,local[i].north-local[j].north)>duplicateTolerance) { "Boundary points are too close" }
        }
        for(i in local.indices) for(j in i+1 until local.size) {
            if(j==i+1 || i==0 && j==local.lastIndex) continue
            require(!intersects(local[i],local[(i+1)%local.size],local[j],local[(j+1)%local.size])) { "Boundary self-intersects" }
        }
        val twiceArea=local.indices.sumOf { i -> val a=local[i];val b=local[(i+1)%local.size];a.east*b.north-b.east*a.north }
        require(abs(twiceArea)>epsilon) { "Boundary is degenerate" }
        // 邻边回折重叠也不可作为有效多边形。
        for(i in local.indices) {
            val a=local[(i+local.size-1)%local.size];val b=local[i];val c=local[(i+1)%local.size]
            if(abs(cross(a,b,c))<=epsilon) require((a.east-b.east)*(c.east-b.east)+(a.north-b.north)*(c.north-b.north)<=0) { "Boundary backtracks" }
        }
        return local
    }
    private fun cross(a: LocalBoundaryPoint,b: LocalBoundaryPoint,c: LocalBoundaryPoint)=(b.east-a.east)*(c.north-a.north)-(b.north-a.north)*(c.east-a.east)
    private fun on(a: LocalBoundaryPoint,b: LocalBoundaryPoint,p: LocalBoundaryPoint)=abs(cross(a,b,p))<=epsilon && p.east>=min(a.east,b.east)-epsilon && p.east<=max(a.east,b.east)+epsilon && p.north>=min(a.north,b.north)-epsilon && p.north<=max(a.north,b.north)+epsilon
    private fun intersects(a: LocalBoundaryPoint,b: LocalBoundaryPoint,c: LocalBoundaryPoint,d: LocalBoundaryPoint): Boolean {
        val x=cross(a,b,c);val y=cross(a,b,d);val z=cross(c,d,a);val w=cross(c,d,b)
        return (x>epsilon && y< -epsilon || x< -epsilon && y>epsilon) && (z>epsilon && w< -epsilon || z< -epsilon && w>epsilon) || on(a,b,c) || on(a,b,d) || on(c,d,a) || on(c,d,b)
    }
}
