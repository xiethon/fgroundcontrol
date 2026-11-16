package com.fairland.fgroundcontrol.data

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class CrashHandlerTest {
    @Test fun uncaughtThreadWritesCrashAndDelegates() {
        val root = Files.createTempDirectory("crash").toFile()
        try {
            val delegated = AtomicBoolean(false)
            val store = LogStore(root)
            val worker = Thread { throw IllegalStateException("uncaught-probe") }
            worker.uncaughtExceptionHandler = CrashHandler(
                { store.append(LogKind.CRASH, "probe", it.stackTraceToString()) },
                Thread.UncaughtExceptionHandler { _, _ -> delegated.set(true) },
                { fail("Unexpected write failure") }
            )
            worker.start(); worker.join(3000)
            assertFalse(worker.isAlive)
            assertTrue(delegated.get())
            assertTrue(root.resolve("CRASH.log").readText().contains("uncaught-probe"))
        } finally { root.deleteRecursively() }
    }
    @Test fun failedWriteStillDelegatesAndReportsDiagnostic() {
        var delegated = false
        var diagnosed = false
        CrashHandler({ throw java.io.IOException("disk failure") }, Thread.UncaughtExceptionHandler { _, _ -> delegated = true }, { diagnosed = true })
            .uncaughtException(Thread.currentThread(), IllegalStateException("probe"))
        assertTrue(delegated); assertTrue(diagnosed)
    }
}
