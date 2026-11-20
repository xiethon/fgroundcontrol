package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BoundaryEditorTest {
    @Test fun discardingDraftRemovesRestartCacheButKeepsSavedTasks() = runBlocking {
        val root=Files.createTempDirectory("discard-draft").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root))
            val draft=BoundaryDraft().append(BoundaryPoint(22.0,113.0))
            store.save(draft);store.saveNamed(draft.copy(name="saved"))
            val editor=BoundaryEditor({store},scope)
            withTimeout(5000) {editor.state.first {it.loaded}}
            assertTrue(editor.discardDraft())
            withTimeout(5000) {editor.state.first {!it.busy && it.draft?.points?.isEmpty()==true}}
            assertNull(store.load())
            assertEquals(1,store.listNamed().size)
        } finally {scope.cancel();root.deleteRecursively()}
    }
    @Test fun writeFailureRetainsPublishedDraftAndRetryReloadsDisk() = runBlocking {
        val root=Files.createTempDirectory("boundary-editor").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val editor=BoundaryEditor({ BoundaryDraftStore(FoundationStore(root)) },scope)
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.create()
            withTimeout(5000) { editor.state.first { !it.busy && it.draft!=null } }
            val point=BoundaryPoint(22.0,113.0)
            editor.append(point)
            val saved=withTimeout(5000) { editor.state.first { !it.busy && it.draft?.points?.size==1 } }.draft
            // 原子替换目标变为目录，模拟存储故障，不能发布未保存新点。
            val target=root.resolve("field-boundary-draft.txt")
            assertTrue(target.delete());assertTrue(target.mkdir())
            editor.append(BoundaryPoint(23.0,114.0))
            withTimeout(5000) { editor.state.first { it.error } }
            assertEquals(saved,editor.state.value.draft)
            assertTrue(target.delete())
            BoundaryDraftStore(FoundationStore(root)).save(saved!!)
            editor.retry()
            val restored=withTimeout(5000) { editor.state.first { !it.error && it.loaded } }
            assertEquals(saved,restored.draft)
            editor.create()
            val fresh=withTimeout(5000) { editor.state.first { !it.busy && it.draft?.points?.isEmpty()==true } }
            assertEquals(BoundaryDraft(),fresh.draft)
            assertNull(BoundaryDraftStore(FoundationStore(root)).load())
        } finally { scope.cancel();root.deleteRecursively() }
    }
    @Test fun selectingAndDeletingSavedTaskNeverReplacesEditingDraft() = runBlocking<Unit> {
        val root=Files.createTempDirectory("task-selection").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root))
            val current=BoundaryDraft().append(BoundaryPoint(24.0,114.0))
            store.save(current)
            store.saveNamed(BoundaryDraft().append(BoundaryPoint(22.0,113.0)).copy(name="A"))
            val editor=BoundaryEditor({store},scope)
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.refreshTasks()
            val task=withTimeout(5000) { editor.tasks.first { it.size==1 } }.single()
            editor.selectTask(task)
            assertEquals(task,editor.selectedTask.value)
            assertEquals(current,editor.state.value.draft)
            editor.renameTask(task,"B")
            withTimeout(5000) { editor.tasks.first { it.singleOrNull()?.draft?.name=="B" } }
            assertEquals("B",editor.selectedTask.value?.draft?.name)
            editor.deleteTask(task)
            withTimeout(5000) { editor.tasks.first { it.isEmpty() } }
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertNull(editor.selectedTask.value)
            assertEquals(current,store.load())
            assertEquals(current,editor.state.value.draft)
        } finally { scope.cancel();root.deleteRecursively() }
    }

    @Test fun saveResolvesAddressAndRefreshOnlyFillsMissingWithoutClobberingRename() = runBlocking<Unit> {
        val root=Files.createTempDirectory("task-address-save").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        try {
            val store=BoundaryDraftStore(FoundationStore(root))
            store.save(BoundaryDraft().append(BoundaryPoint(22.0,113.0)))
            val editor=BoundaryEditor({store},scope,resolveAddress={calls.incrementAndGet();"已解析地址"})
            withTimeout(5000) { editor.state.first { it.loaded } }
            editor.save("任务")
            withTimeout(5000) { editor.state.first { !it.busy && it.draft?.name=="任务" } }
            assertEquals("已解析地址",store.listNamed().single().draft.address)
            assertEquals("已解析地址",store.load()!!.address)
            editor.refreshTasks()
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(1,calls.get())
            store.saveNamed(BoundaryDraft().append(BoundaryPoint(23.0,114.0)).copy(name="缺地址"))
            val missingId=store.listNamed().first {it.draft.name=="缺地址"}.id
            FoundationStore(root).restoreModified(missingId,1234567890L)
            editor.refreshTasks()
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(1234567890L,store.listNamed().first {it.id==missingId}.modifiedMs)
            assertEquals(2,calls.get())
            assertTrue(store.listNamed().all { it.draft.address=="已解析地址" })
            val missing=store.listNamed().first {it.draft.name=="缺地址"}
            store.renameNamed(missing.id,"改名")
            store.updateNamedAddress(missing.id,BoundaryPoint(23.0,114.0),"迟到地址")
            assertEquals("改名",store.load(missing.id)!!.name)
            assertEquals("已解析地址",store.load(missing.id)!!.address)
        } finally {scope.cancel();root.deleteRecursively()}
    }
    @Test fun addressFailureDoesNotPreventNamedSave() = runBlocking<Unit> {
        val root=Files.createTempDirectory("task-address-failure").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root));store.save(BoundaryDraft().append(BoundaryPoint(22.0,113.0)))
            val editor=BoundaryEditor({store},scope,resolveAddress={throw java.io.IOException("fixture")})
            withTimeout(5000) {editor.state.first {it.loaded}}
            editor.save("离线任务")
            val saved=withTimeout(5000) {editor.state.first {!it.busy && it.draft?.name=="离线任务"}}
            assertFalse(saved.error);assertNull(store.listNamed().single().draft.address)
        } finally {scope.cancel();root.deleteRecursively()}
    }

    @Test fun failedDraftWriteCannotPublishNamedTaskOrSaveCompletion() = runBlocking<Unit> {
        val root=Files.createTempDirectory("named-save-failure").toFile()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val store=BoundaryDraftStore(FoundationStore(root));val draft=BoundaryDraft().append(BoundaryPoint(22.0,113.0)).copy(name="同名")
            store.save(draft)
            val editor=BoundaryEditor({store},scope)
            withTimeout(5000) {editor.state.first {it.loaded}}
            val target=root.resolve("field-boundary-draft.txt");check(target.delete());check(target.mkdir())
            assertTrue(editor.save("同名"))
            withTimeout(5000) {editor.state.first {it.error}}
            assertEquals(0L,editor.saveVersion.value)
            assertTrue(store.listNamed().isEmpty())
            check(target.delete());store.save(draft);editor.retry()
            withTimeout(5000) {editor.state.first {!it.error}}
            assertTrue(editor.save("同名"))
            withTimeout(5000) {editor.saveVersion.first {it==1L}}
            assertEquals(1,store.listNamed().size)
        } finally {scope.cancel();root.deleteRecursively()}
    }

}
