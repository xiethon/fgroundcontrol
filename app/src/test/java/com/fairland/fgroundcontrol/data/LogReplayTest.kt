package com.fairland.fgroundcontrol.data
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.zip.ZipFile

class LogReplayTest {
    @Test fun boundedRotationAndExport() {
        val root = Files.createTempDirectory("logs").toFile()
        val zip = Files.createTempFile("export", ".zip").toFile()
        try {
            val store = LogStore(root, 16384, 2)
            LogKind.entries.forEach { kind -> repeat(90) { store.append(kind, "session", "x".repeat(800)) } }
            assertTrue(root.listFiles()!!.size <= 9)
            assertTrue(root.listFiles()!!.all { it.length() <= 16384 })
            store.export(zip)
            ZipFile(zip).use { assertNotNull(it.getEntry("manifest.txt")); assertNotNull(it.getEntry("CRASH.log")) }
        } finally { root.deleteRecursively(); zip.delete() }
    }
    @Test fun expiryRemovesOldLogsButKeepsRecentAndUnrelatedFiles() {
        val root = Files.createTempDirectory("expiry").toFile()
        try {
            val old = root.resolve("APP.1.log").apply { writeText("old"); setLastModified(System.currentTimeMillis() - 8L * 86400000) }
            val unrelated = root.resolve("notes.txt").apply { writeText("keep") }
            val recent = root.resolve("FLIGHT.log").apply { writeText("recent") }
            LogStore(root).prune()
            assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(unrelated.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun storageAndExportFailuresAreVisible() {
        val root = Files.createTempDirectory("failure").toFile()
        try {
            val blocker = root.resolve("file").apply { writeText("blocked") }
            assertThrows(IllegalStateException::class.java) { FoundationStore(blocker) }
            val logs = LogStore(root.resolve("logs"))
            assertThrows(java.io.FileNotFoundException::class.java) { logs.export(blocker.resolve("bad.zip")) }
        } finally { root.deleteRecursively() }
    }
    @Test fun replayRejectsCorruptionAndTimeReversal() {
        val source = ReplaySource()
        assertEquals(2, source.read(sequenceOf("0,30,120", "100,30.1,120.1")).size)
        assertThrows(IllegalArgumentException::class.java) { source.read(sequenceOf("100,30,120", "0,30,120")) }
        assertThrows(IllegalArgumentException::class.java) { source.read(sequenceOf("0,NaN,120")) }
    }
}
