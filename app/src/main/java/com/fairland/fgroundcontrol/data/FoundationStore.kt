package com.fairland.fgroundcontrol.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 单实例串行写入，替换前落盘；残留临时文件不作为有效数据读取。 */
class FoundationStore(private val root: File) {
    init { check(root.mkdirs() || root.isDirectory) { "Storage directory unavailable" } }
    private fun file(key: String): File {
        require(key.matches(Regex("[a-zA-Z0-9_-]{1,64}")))
        return File(root, "$key.txt")
    }
    @Synchronized fun write(key: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
        val target = file(key)
        val temp = File(root, "${target.name}.tmp")
        try {
            FileOutputStream(temp).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    @Synchronized fun read(key: String): String? {
        val target = file(key)
        if (!target.exists()) return null
        target.inputStream().use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 1024 * 1024) { "Stored record too large" }
                output.write(buffer, 0, count)
            }
            val bytes = output.toByteArray()
            require(bytes.size <= 1024 * 1024) { "Stored record too large" }
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
