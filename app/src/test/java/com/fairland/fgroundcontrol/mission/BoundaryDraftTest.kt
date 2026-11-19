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
}
