package com.fairland.fgroundcontrol.data
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class FoundationStoreTest {
    @Test fun replacementSurvivesReopenAndIgnoresPartialWrite() {
        val root = Files.createTempDirectory("store").toFile()
        try {
            FoundationStore(root).write("task", "旧记录")
            FoundationStore(root).write("task", "新记录")
            root.resolve("task.txt.tmp").writeText("partial")
            assertEquals("新记录", FoundationStore(root).read("task"))
            assertNull(FoundationStore(root).read("missing"))
        } finally { root.deleteRecursively() }
    }
    @Test fun oversizedStoredRecordIsRejectedOnRead() {
        val root = Files.createTempDirectory("oversize").toFile()
        try {
            root.resolve("task.txt").writeBytes(ByteArray(1048577))
            assertThrows(IllegalArgumentException::class.java) { FoundationStore(root).read("task") }
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidKeyAndOversizeDoNotReplaceRecord() {
        val root = Files.createTempDirectory("store").toFile()
        try {
            val store = FoundationStore(root)
            store.write("task", "good")
            assertThrows(IllegalArgumentException::class.java) { store.write("../escape", "bad") }
            assertThrows(IllegalArgumentException::class.java) { store.write("task", "a".repeat(1048577)) }
            assertEquals("good", store.read("task"))
        } finally { root.deleteRecursively() }
    }
}
