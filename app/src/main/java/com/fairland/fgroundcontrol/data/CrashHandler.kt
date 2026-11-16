package com.fairland.fgroundcontrol.data

/** 崩溃记录只作尽力同步写入，失败也必须交还系统原处理器。 */
class CrashHandler(
    private val record: (Throwable) -> Unit,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val diagnose: () -> Unit
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try { record(error) }
        catch (_: Exception) { diagnose() }
        finally { previous?.uncaughtException(thread, error) }
    }
}
