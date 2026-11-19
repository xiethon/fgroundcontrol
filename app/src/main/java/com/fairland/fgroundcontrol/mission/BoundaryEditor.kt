package com.fairland.fgroundcontrol.mission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BoundaryEditorState(val loaded: Boolean=false, val draft: BoundaryDraft?=null, val busy: Boolean=false, val error: Boolean=false)
/** 所有修改串行，写盘成功后发布，失败保留上次可靠草稿。 */
class BoundaryEditor(private val createStore: () -> BoundaryDraftStore, private val scope: CoroutineScope) {
    private val store by lazy(createStore)
    private val lock=Mutex()
    private val mutable=MutableStateFlow(BoundaryEditorState())
    val state=mutable.asStateFlow()
    init { scope.launch { lock.withLock {
        try { val draft=withContext(Dispatchers.IO) { store.load() }; mutable.value=BoundaryEditorState(true,draft) }
        catch(e: Exception) { if(e is CancellationException) throw e; mutable.value=BoundaryEditorState(loaded=true,error=true) }
    } } }
    fun retry() { scope.launch { lock.withLock {
        try { val draft=withContext(Dispatchers.IO) { store.load() }; mutable.value=BoundaryEditorState(true,draft) }
        catch(e: Exception) { if(e is CancellationException) throw e; mutable.value=mutable.value.copy(error=true) }
    } } }
    fun save()=change { it ?: error("No draft") }
    fun create()=change { BoundaryDraft() }
    fun append(point: BoundaryPoint)=change { (it ?: error("No draft")).append(point) }
    private fun change(transform: (BoundaryDraft?)->BoundaryDraft) {
        if(!mutable.value.loaded || mutable.value.busy || mutable.value.error) return
        mutable.value=mutable.value.copy(busy=true)
        scope.launch { lock.withLock {
            try {
                val draft=transform(mutable.value.draft)
                withContext(Dispatchers.IO) { store.save(draft) }
                mutable.value=BoundaryEditorState(true,draft)
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                mutable.value=mutable.value.copy(busy=false,error=true)
            }
        } }
    }
}
