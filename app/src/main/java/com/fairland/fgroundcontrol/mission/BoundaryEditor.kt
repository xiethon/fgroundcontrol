package com.fairland.fgroundcontrol.mission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class BoundaryEditorState(
    val loaded: Boolean = false,
    val draft: BoundaryDraft? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val planning: Boolean = false,
    val planningError: Boolean = false,
)
/** 修改和提交串行，写盘后发布；原生计算在锁外运行，过期/取消结果不得落盘。 */
class BoundaryEditor(
    private val createStore: () -> BoundaryDraftStore,
    private val scope: CoroutineScope,
    private val planner: FieldPlanningService = FieldPlanningService(),
    private val resolveAddress: suspend (BoundaryPoint)->String? = {null},
    private val onFailure: (String, Exception) -> Unit = { _, _ -> },
) {
    private val store by lazy(createStore)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(BoundaryEditorState())
    private val writePending = AtomicBoolean(false)
    private var generation = 0L
    private var planningJob: Job? = null
    val state = mutable.asStateFlow()
    private val savedVersion = MutableStateFlow(0L)
    val saveVersion = savedVersion.asStateFlow()
    private val saved = MutableStateFlow<List<SavedFieldTask>>(emptyList())
    val tasks = saved.asStateFlow()
    private val selected = MutableStateFlow<SavedFieldTask?>(null)
    val selectedTask = selected.asStateFlow()
    private val listError = MutableStateFlow(false)
    val taskListError = listError.asStateFlow()
    private var addressRefresh: Job? = null
    fun loadTaskPreviews() { taskOperation { } }
    fun refreshTasks() {
        taskOperation { }
        addressRefresh?.cancel()
        addressRefresh=scope.launch { try {
            val tasks=lock.withLock { withContext(Dispatchers.IO) { readTasks() } }
            tasks.filter {it.draft.address==null}.forEach { task ->
                val point=task.draft.points.firstOrNull() ?: return@forEach
                val address=lookupAddress(point) ?: return@forEach
                lock.withLock {
                    try {
                        val updated=withContext(Dispatchers.IO) {store.updateNamedAddress(task.id,point,address);readTasks()}
                        saved.value=updated
                        selected.value=updated.firstOrNull {it.id==selected.value?.id}
                    } catch(error: Exception) {if(error is CancellationException) throw error;onFailure("save-address",error)}
                }
            }
        } catch(error: Exception) {
            if(error is CancellationException) throw error
            listError.value=true;onFailure("refresh-addresses",error)
        } }
    }
    private fun readTasks(): List<SavedFieldTask> {
        val result=store.scanNamed()
        listError.value=result.invalidIds.isNotEmpty()
        if(result.invalidIds.isNotEmpty()) onFailure("task-list",IllegalStateException("${result.invalidIds.size} invalid task records"))
        return result.tasks
    }
    private suspend fun lookupAddress(point: BoundaryPoint): String? = try {
        resolveAddress(point)?.trim()?.takeIf {it.isNotEmpty() && it.length<=1024}
    } catch(error: Exception) {if(error is CancellationException) throw error;onFailure("lookup-address",IllegalStateException("Address lookup failed: ${error.javaClass.simpleName}"));null}
    fun selectTask(task: SavedFieldTask) { selected.value=task }
    fun clearTaskSelection() { selected.value=null }
    fun renameTask(task: SavedFieldTask,name: String) = taskOperation { store.renameNamed(task.id,name) }
    fun deleteTask(task: SavedFieldTask) = taskOperation { store.deleteNamed(task.id) }
    private fun taskOperation(action: ()->Unit) { scope.launch { lock.withLock {
        try {
            val tasks=withContext(Dispatchers.IO) { action();readTasks() }
            saved.value=tasks
            selected.value=tasks.firstOrNull { it.id==selected.value?.id }
        } catch(error: Exception) {
            if(error is CancellationException) throw error
            listError.value=true;onFailure("task-list",error)
        }
    } } }
    init { retry() }

    fun retry() { scope.launch { lock.withLock {
        generation++
        planningJob?.cancel()
        try {
            val draft = withContext(Dispatchers.IO) { store.load() }
            mutable.value = BoundaryEditorState(true, draft)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutable.value = mutable.value.copy(loaded = true, busy = false, planning = false, error = true)
            onFailure("load", error)
        }
    } } }
    fun save() = change { it ?: error("No draft") }
    fun save(name: String) = change(persistNamed = true) {
        require(name.isNotBlank() && name.trim().length <= 80)
        (it ?: error("No draft")).copy(name = name.trim())
    }
    fun discardDraft() = change { BoundaryDraft() }
    fun create() { clearTaskSelection();change { BoundaryDraft() } }
    fun append(point: BoundaryPoint) = change { (it ?: error("No draft")).append(point) }
    fun configure(parameters: FieldPlanningParameters) = change {
        val draft = it ?: error("No draft")
        if (draft.parameters == parameters) draft else draft.copy(
            revision = Math.addExact(draft.revision, 1), parameters = parameters, plan = null)
    }
    private fun change(persistNamed: Boolean = false, transform: (BoundaryDraft?) -> BoundaryDraft): Boolean {
        if(!writePending.compareAndSet(false,true)) return false
        while(true) {
            val previous=mutable.value
            if(!previous.loaded || previous.busy || previous.error) {writePending.set(false);return false}
            if(mutable.compareAndSet(previous,previous.copy(busy=true))) break
        }
        scope.launch { lock.withLock {
            generation++
            planningJob?.cancel()
            try {
                var draft = transform(mutable.value.draft)
                if(persistNamed && draft.address==null) draft.points.firstOrNull()?.let { point -> draft=draft.copy(address=lookupAddress(point)) }
                withContext(Dispatchers.IO) { store.save(draft); if(persistNamed) store.saveNamed(draft) }
                mutable.value = BoundaryEditorState(true, draft)
                if(persistNamed) { savedVersion.value=Math.addExact(savedVersion.value,1);taskOperation { } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.value = mutable.value.copy(busy = false, planning = false, error = true)
                onFailure("save", error)
            } finally { writePending.set(false) }
        } }
        return true
    }
    fun generate() { scope.launch {
        val input = lock.withLock {
            val current = mutable.value
            if (!current.loaded || current.busy || current.error || writePending.get() || current.planning || current.draft == null) return@launch
            val ticket = ++generation
            mutable.value = current.copy(planning = true, planningError = false)
            planningJob = coroutineContext[Job]
            ticket to current.draft.copy(points = current.draft.points.toList())
        }
        try {
            val result = planner.plan(input.second)
            lock.withLock {
                if (input.first != generation || mutable.value.busy) return@withLock
                val draft = input.second.copy(plan = result)
                try {
                    withContext(Dispatchers.IO) { store.save(draft) }
                    mutable.value = BoundaryEditorState(true, draft, busy = writePending.get())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutable.value = mutable.value.copy(planning = false, error = true)
                    onFailure("save-plan", error)
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            lock.withLock {
                if (input.first == generation) {
                    mutable.value = mutable.value.copy(planning = false, planningError = true)
                    onFailure("plan revision=${input.second.revision}", error)
                }
            }
        }
    } }
}
