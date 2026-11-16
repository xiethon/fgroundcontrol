package com.fairland.fgroundcontrol.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class LogKind { APP, FLIGHT, CRASH }

/** 由后台调用方串行使用；每类独立轮转，总容量固定。 */
class LogStore(private val root: File, private val limit: Long = 5 * 1024 * 1024, private val archives: Int = 3, private val now: () -> Long = { System.currentTimeMillis() }) {
    init { require(limit >= 16384 && archives in 1..10); check(root.mkdirs() || root.isDirectory) }
    @Synchronized fun append(kind: LogKind, session: String, message: String) {
        prune()
        require(session.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        val text = message.replace("\n", " ").replace("\r", " ").take(2048)
        val bytes = "${now()}|${System.nanoTime()}|$session|$text\n".toByteArray(Charsets.UTF_8)
        val file = File(root, "${kind.name}.log")
        if (file.length() + bytes.size > limit) {
            File(root, "${kind.name}.$archives.log").let {
                if (it.exists()) check(it.delete()) { "Log archive deletion failed" }
            }
            for (i in archives - 1 downTo 1) move(File(root, "${kind.name}.$i.log"), File(root, "${kind.name}.${i + 1}.log"))
            move(file, File(root, "${kind.name}.1.log"))
        }
        FileOutputStream(file, true).use { it.write(bytes); it.fd.sync() }
    }
    private val logName = Regex("""(APP|FLIGHT|CRASH)(\.[0-9]+)?\.log""")
    @Synchronized fun prune() {
        root.listFiles()?.filter { logName.matches(it.name) }?.forEach {
            val oldest = it.inputStream().use { input ->
                val prefix = ByteArray(64)
                val count = input.read(prefix)
                if (count > 0) String(prefix, 0, count, Charsets.UTF_8).substringBefore('|').toLongOrNull() else null
            } ?: it.lastModified()
            if (now() - oldest > 7L * 24 * 60 * 60 * 1000) check(it.delete()) { "Log expiry cleanup failed" }
        }
    }
    private fun move(from: File, to: File) { if (from.exists()) check(from.renameTo(to)) { "Log rotation failed" } }
    @Synchronized fun export(destination: File) {
        prune()
        require(destination.canonicalFile.parentFile != root.canonicalFile)
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write("schema=1\nkinds=APP,FLIGHT,CRASH\nULog=false\n".toByteArray())
            zip.closeEntry()
            root.listFiles()?.filter { logName.matches(it.name) }?.sortedBy { it.name }?.forEach {
                zip.putNextEntry(ZipEntry(it.name)); it.inputStream().use { input -> input.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
}
