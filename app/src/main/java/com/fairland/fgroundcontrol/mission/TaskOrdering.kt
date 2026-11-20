package com.fairland.fgroundcontrol.mission

import kotlin.math.*

/** 飞行器至任务首点的大圆距离；缺失位置排在末尾。 */
object TaskOrdering {
    fun distance(origin: BoundaryPoint,target: BoundaryPoint?): Double {
        if(target==null) return Double.POSITIVE_INFINITY
        val dLat=Math.toRadians(target.latitude-origin.latitude)
        val dLon=Math.toRadians(target.longitude-origin.longitude)
        val a=sin(dLat/2).pow(2)+cos(Math.toRadians(origin.latitude))*cos(Math.toRadians(target.latitude))*sin(dLon/2).pow(2)
        return 6378137.0*2*asin(sqrt(a.coerceIn(0.0,1.0)))
    }
}
