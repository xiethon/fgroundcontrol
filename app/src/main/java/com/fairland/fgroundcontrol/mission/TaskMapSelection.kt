package com.fairland.fgroundcontrol.mission

/** 以首点展开经度，兼容跨日期线；边上点击也算命中。 */
internal fun containsTaskPoint(points: List<BoundaryPoint>, latitude: Double, longitude: Double): Boolean {
    if(points.size < 3) return false
    val origin=points.first().longitude
    fun unwrap(value: Double) = ((value-origin+540.0)%360.0)-180.0
    val click=unwrap(longitude)
    fun x(p: BoundaryPoint) = unwrap(p.longitude)-click
    var inside=false
    points.indices.forEach { i ->
        val a=points[i]; val b=points[(i+1)%points.size]
        val ax=x(a);val bx=x(b);val ay=a.latitude-latitude;val by=b.latitude-latitude
        val cross=ax*by-bx*ay
        if(kotlin.math.abs(cross)<1e-12 && ax*bx<=0 && ay*by<=0) return true
        if((ay>0)!=(by>0) && ax+(bx-ax)*(-ay)/(by-ay)>0) inside=!inside
    }
    return inside
}

/** 先取面积重心；凹地块重心落在外部时，回退到内部最宽水平截面的中点。 */
internal fun taskLabelCenter(points: List<BoundaryPoint>): Pair<Double,Double> {
    require(points.isNotEmpty())
    val origin=points.first()
    val xy=points.map { (((it.longitude-origin.longitude+540.0)%360.0)-180.0) to (it.latitude-origin.latitude) }
    fun coordinate(x: Double,y: Double)= (origin.latitude+y) to (((origin.longitude+x+540.0)%360.0)-180.0)
    var area=0.0;var cx=0.0;var cy=0.0
    xy.indices.forEach { i ->
        val a=xy[i];val b=xy[(i+1)%xy.size];val cross=a.first*b.second-b.first*a.second
        area+=cross;cx+=(a.first+b.first)*cross;cy+=(a.second+b.second)*cross
    }
    if(kotlin.math.abs(area)>1e-16) {
        val center=coordinate(cx/(3*area),cy/(3*area))
        if(containsTaskPoint(points,center.first,center.second)) return center
    }
    val levels=xy.map {it.second}.distinct().sorted()
    var best: Pair<Double,Double>?=null;var width=-1.0
    levels.zipWithNext().forEach { (low,high) ->
        val y=(low+high)/2
        val hits=xy.indices.mapNotNull { i ->
            val a=xy[i];val b=xy[(i+1)%xy.size]
            if((a.second>y)==(b.second>y)) null else a.first+(b.first-a.first)*(y-a.second)/(b.second-a.second)
        }.sorted()
        hits.chunked(2).filter {it.size==2}.forEach { span ->
            if(span[1]-span[0]>width) {width=span[1]-span[0];best=coordinate((span[0]+span[1])/2,y)}
        }
    }
    return best ?: (origin.latitude to origin.longitude)
}

/** 资源箭头朝右；转换为MapLibre相对地图北向的顺时针旋转角。 */
internal fun routeEndpointRotation(points: List<BoundaryPoint>, start: Boolean): Double? {
    if(points.size<2) return null
    val endpoint=if(start) points.first() else points.last()
    val other=if(start) points.drop(1).firstOrNull {it!=endpoint} else points.dropLast(1).lastOrNull {it!=endpoint}
    other ?: return null
    val a=if(start) endpoint else other;val b=if(start) other else endpoint
    val lat1=Math.toRadians(a.latitude);val lat2=Math.toRadians(b.latitude)
    val lon=Math.toRadians(b.longitude-a.longitude)
    val y=kotlin.math.sin(lon)*kotlin.math.cos(lat2)
    val x=kotlin.math.cos(lat1)*kotlin.math.sin(lat2)-kotlin.math.sin(lat1)*kotlin.math.cos(lat2)*kotlin.math.cos(lon)
    return (Math.toDegrees(kotlin.math.atan2(y,x))-90+360)%360
}
