package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore

/** 调用者在IO线程串行调用，单记录原子替换；兼容旧boundary-v1草稿。 */
class BoundaryDraftStore(private val store: FoundationStore) {
    fun save(draft: BoundaryDraft) = save(draft, "field-boundary-draft")
    fun saveNamed(draft: BoundaryDraft) {
        require(!draft.name.isNullOrBlank() && draft.points.isNotEmpty())
        save(draft, "field-task-${java.util.UUID.randomUUID()}")
    }
    private fun save(draft: BoundaryDraft, key: String) {
        require(draft.revision >= 0 && draft.points.distinct().size == draft.points.size)
        require(draft.name == null || draft.name.isNotBlank() && draft.name.length <= 80)
        require(draft.address==null || draft.address.isNotBlank() && draft.address.length<=1024)
        draft.plan?.let { FieldPlanningService.validateResult(draft, it) }
        // 默认参数不是地块编辑内容；空边界只留内存，不产生续编缓存。
        if (draft.points.isEmpty()) { store.remove(key); return }
        store.write(key, buildString {
            append("boundary-v2\n").append(draft.revision).append('|').append(draft.name?.let { java.util.Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }.orEmpty()).append('|').append(draft.address?.let { java.util.Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }.orEmpty()).append('\n')
            append(draft.parameters?.let { "${it.altitude},${it.speed},${it.spacing},${it.inset},${it.obstacleMargin}" } ?: "-").append('\n')
            append(draft.points.size).append('\n')
            draft.points.forEach { append(it.latitude).append(',').append(it.longitude).append('\n') }
            val result=draft.plan
            if(result==null) append("-\n") else {
                append(result.revision).append(',').append(result.fingerprint).append(',').append(result.algorithm).append(',')
                    .append(result.distanceMeters).append(',').append(result.estimatedCoverageAreaSquareMeters).append(',').append(result.points.size).append('\n')
                result.points.forEach { append(it.latitude).append(',').append(it.longitude).append('\n') }
            }
        })
    }
    fun scanNamed(): SavedTaskScan {
        val tasks=mutableListOf<SavedFieldTask>();val invalid=mutableListOf<String>()
        for(key in store.keys("field-task-")) {
            try { tasks.add(SavedFieldTask(key,requireNotNull(load(key)),store.modified(key))) }
            catch(error: Exception) { invalid.add(key) }
        }
        return SavedTaskScan(tasks,invalid)
    }
    fun listNamed(): List<SavedFieldTask> = store.keys("field-task-").map { key ->
        SavedFieldTask(key, requireNotNull(load(key)), store.modified(key))
    }
    fun renameNamed(key: String, name: String) {
        require(key.startsWith("field-task-") && name.isNotBlank() && name.trim().length<=80)
        save(requireNotNull(load(key)).copy(name=name.trim()),key)
    }
    fun updateNamedAddress(key: String,point: BoundaryPoint,address: String) {
        require(key.startsWith("field-task-"))
        val current=load(key) ?: return
        if(current.points.firstOrNull()!=point || current.address!=null) return
        val modified=store.modified(key)
        save(current.copy(address=address),key)
        store.restoreModified(key,modified)
    }
    fun deleteNamed(key: String) { require(key.startsWith("field-task-"));store.remove(key) }
    fun load(key: String = "field-boundary-draft"): BoundaryDraft? {
        val text=store.read(key) ?: return null
        val lines=text.lines()
        require(lines.size>=3 && lines.last().isEmpty()) { "Invalid boundary draft" }
        val identity=lines[1].split('|');require(identity.size in 1..3)
        val revision=identity[0].toLong().also { require(it>=0) }
        val name=identity.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { String(java.util.Base64.getDecoder().decode(it),Charsets.UTF_8) }
        val address=identity.getOrNull(2)?.takeIf {it.isNotEmpty()}?.let {String(java.util.Base64.getDecoder().decode(it),Charsets.UTF_8)}
        require(address==null || address.isNotBlank() && address.length<=1024)
        require(name==null || name.isNotBlank() && name.length<=80)
        fun point(line: String): BoundaryPoint {
            val parts=line.split(','); require(parts.size==2)
            return BoundaryPoint(parts[0].toDouble(),parts[1].toDouble())
        }
        if(lines[0]=="boundary-v1") {
            val points=lines.subList(2,lines.lastIndex).map(::point)
            require(points.distinct().size==points.size)
            if (points.isEmpty()) { store.remove(key); return null }
            return BoundaryDraft(revision,points)
        }
        require(lines[0]=="boundary-v2" && lines.size>=6)
        val parameters=if(lines[2]=="-") null else lines[2].split(',').let {
            require(it.size in 4..5);FieldPlanningParameters(it[0].toDouble(),it[1].toDouble(),it[2].toDouble(),it[3].toDouble(),it.getOrNull(4)?.toDouble() ?: 1.0)
        }
        val count=lines[3].toInt();require(count>=0 && count<=lines.size-6)
        val points=lines.subList(4,4+count).map(::point)
        require(points.distinct().size==points.size)
        val draft=BoundaryDraft(revision,points,parameters,name=name,address=address)
        val header=lines[4+count]
        if(header=="-") { require(lines.size==6+count); if(points.isEmpty()) { store.remove(key); return null };return draft }
        val fields=header.split(',');require(fields.size==6)
        val routeCount=fields[5].toInt();require(routeCount in 2..4096 && lines.size==6+count+routeCount)
        val result=FieldPlan(fields[0].toLong(),fields[1],fields[2],lines.subList(5+count,lines.lastIndex).map(::point),fields[3].toDouble(),fields[4].toDouble())
        // 旧算法结果失效，边界/参数仍可重开；不把过期结果当当前有效路线。
        if(result.algorithm!=FieldPlanningService.ALGORITHM) return draft
        FieldPlanningService.validateResult(draft,result)
        return draft.copy(plan=result)
    }
}
