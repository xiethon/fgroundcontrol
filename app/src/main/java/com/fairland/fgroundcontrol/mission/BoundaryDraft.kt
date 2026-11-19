package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore

/** 只保存编辑事实，不生成航线或执行状态。 */
data class BoundaryPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
}
data class BoundaryDraft(val revision: Long = 0, val points: List<BoundaryPoint> = emptyList()) {
    fun append(point: BoundaryPoint): BoundaryDraft {
        require(point !in points) { "Duplicate boundary point" }
        return copy(revision = Math.addExact(revision, 1), points = points + point)
    }
}
/** 调用者必须在IO线程串行调用；FoundationStore原子替换完整记录。 */
class BoundaryDraftStore(private val store: FoundationStore) {
    fun save(draft: BoundaryDraft) {
        require(draft.revision >= 0)
        store.write("field-boundary-draft", buildString {
            append("boundary-v1\n").append(draft.revision).append('\n')
            draft.points.forEach { append(it.latitude).append(',').append(it.longitude).append('\n') }
        })
    }
    fun load(): BoundaryDraft? {
        val text = store.read("field-boundary-draft") ?: return null
        val lines = text.lines()
        require(lines.size >= 3 && lines[0] == "boundary-v1" && lines.last().isEmpty()) { "Invalid boundary draft" }
        val revision = lines[1].toLong().also { require(it >= 0) }
        val points = lines.subList(2, lines.lastIndex).map { line ->
            val parts = line.split(','); require(parts.size == 2)
            BoundaryPoint(parts[0].toDouble(), parts[1].toDouble())
        }
        require(points.distinct().size == points.size)
        return BoundaryDraft(revision, points)
    }
}
