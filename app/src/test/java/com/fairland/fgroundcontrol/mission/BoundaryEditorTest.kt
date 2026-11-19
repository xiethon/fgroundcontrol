package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BoundaryEditorTest {
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
            assertEquals(BoundaryDraft(),BoundaryDraftStore(FoundationStore(root)).load())
        } finally { scope.cancel();root.deleteRecursively() }
    }
}
