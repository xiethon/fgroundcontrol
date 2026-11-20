package com.fairland.fgroundcontrol.mission

/** 边界坐标使用WGS84；本地规划结果与执行事实分离。 */
data class BoundaryPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
}
data class BoundaryDraft(val revision: Long = 0, val points: List<BoundaryPoint> = emptyList(), val parameters: FieldPlanningParameters? = null, val plan: FieldPlan? = null, val name: String? = null, val address: String? = null) {
    fun append(point: BoundaryPoint): BoundaryDraft {
        require(points.size < 128) { "At most 128 boundary points" }
        require(point !in points) { "Duplicate boundary point" }
        return copy(revision = Math.addExact(revision, 1), points = points + point, plan = null)
    }
}

data class SavedFieldTask(val id: String,val draft: BoundaryDraft,val modifiedMs: Long)

data class SavedTaskScan(val tasks: List<SavedFieldTask>,val invalidIds: List<String>)
