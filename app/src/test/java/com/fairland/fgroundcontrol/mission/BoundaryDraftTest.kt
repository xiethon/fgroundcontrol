package com.fairland.fgroundcontrol.mission

import com.fairland.fgroundcontrol.data.FoundationStore
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class BoundaryDraftTest {
    @Test fun reopenRestoresOrderAndRevision() {
        val root = Files.createTempDirectory("boundary").toFile()
        try {
            val draft = BoundaryDraft().append(BoundaryPoint(22.0,113.0)).append(BoundaryPoint(23.0,114.0))
            BoundaryDraftStore(FoundationStore(root)).save(draft)
            assertEquals(draft,BoundaryDraftStore(FoundationStore(root)).load())
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidAndDuplicateAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BoundaryPoint(Double.NaN,0.0) }
        assertThrows(IllegalArgumentException::class.java) { BoundaryPoint(0.0,181.0) }
        val point=BoundaryPoint(0.0,0.0)
        assertThrows(IllegalArgumentException::class.java) { BoundaryDraft().append(point).append(point) }
    }
    @Test fun corruptRecordDoesNotBecomeEmptyDraft() {
        val root=Files.createTempDirectory("boundary").toFile()
        try {
            val store=FoundationStore(root)
            assertNull(BoundaryDraftStore(store).load())
            store.write("field-boundary-draft","boundary-v1\n1\n22.0,113.0")
            assertThrows(IllegalArgumentException::class.java) { BoundaryDraftStore(store).load() }
        } finally { root.deleteRecursively() }
    }
    @Test fun emptyDraftDoesNotPersistEvenWithDefaultParametersAndOldEmptyRecordsAreCleared() {
        val root=java.nio.file.Files.createTempDirectory("empty-boundary").toFile()
        try {
            val foundation=com.fairland.fgroundcontrol.data.FoundationStore(root)
            val store=BoundaryDraftStore(foundation)
            store.save(BoundaryDraft(parameters=FieldPlanningParameters(4.0,5.0,5.0,1.0)))
            assertNull(store.load())
            assertFalse(root.resolve("field-boundary-draft.txt").exists())
            val partial=BoundaryDraft().append(BoundaryPoint(22.0,113.0))
            store.save(partial);assertEquals(partial,store.load())
            store.save(BoundaryDraft());assertNull(store.load())
            for(text in listOf("boundary-v1\n0\n","boundary-v2\n1\n4.0,5.0,5.0,1.0,1.0\n0\n-\n")) {
                foundation.write("field-boundary-draft",text)
                assertNull(store.load());assertFalse(root.resolve("field-boundary-draft.txt").exists())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun namedTaskPersistsSeparatelyFromNewDraft() {
        val root=java.nio.file.Files.createTempDirectory("named-task").toFile()
        try {
            val foundation=com.fairland.fgroundcontrol.data.FoundationStore(root)
            val store=BoundaryDraftStore(foundation)
            val draft=BoundaryDraft().append(BoundaryPoint(22.0,113.0)).copy(name="地块A")
            store.saveNamed(draft);store.save(draft)
            assertEquals(draft,store.load())
            store.save(BoundaryDraft())
            assertNull(store.load())
            assertEquals(1,root.listFiles()!!.count { it.name.startsWith("field-task-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun namedTaskListRenameAndDeleteKeepDraftAndOtherTasks() {
        val root=java.nio.file.Files.createTempDirectory("task-list").toFile()
        try {
            val store=BoundaryDraftStore(com.fairland.fgroundcontrol.data.FoundationStore(root))
            val draft=BoundaryDraft().append(BoundaryPoint(22.0,113.0))
            store.save(draft)
            store.saveNamed(draft.copy(name="A"));store.saveNamed(draft.copy(name="B"))
            val tasks=store.listNamed();assertEquals(2,tasks.size)
            val aTask=tasks.first { it.draft.name=="A" }
            store.renameNamed(aTask.id,"新名称")
            assertEquals("新名称",store.listNamed().first { it.id==aTask.id }.draft.name)
            store.deleteNamed(aTask.id)
            assertEquals(listOf("B"),store.listNamed().map { it.draft.name })
            assertEquals(draft,store.load())
            assertThrows(IllegalArgumentException::class.java) { store.deleteNamed("field-boundary-draft") }
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptNamedRecordDoesNotHideValidTasksOrDeleteEvidence() {
        val root=java.nio.file.Files.createTempDirectory("task-corrupt").toFile()
        try {
            val foundation=com.fairland.fgroundcontrol.data.FoundationStore(root)
            val store=BoundaryDraftStore(foundation)
            store.saveNamed(BoundaryDraft().append(BoundaryPoint(22.0,113.0)).copy(name="正常"))
            foundation.write("field-task-corrupt","broken")
            val scan=store.scanNamed()
            assertEquals(listOf("正常"),scan.tasks.map {it.draft.name})
            assertEquals(listOf("field-task-corrupt"),scan.invalidIds)
            assertEquals("broken",foundation.read("field-task-corrupt"))
        } finally {root.deleteRecursively()}
    }

}
