package com.fairland.fgroundcontrol.maps

import com.fairland.fgroundcontrol.data.FoundationStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

data class MapConfiguration(val provider: String = "tianditu", val style: String? = null, val remoteStyle: Boolean = false, val status: String = "unconfigured", val attribution: String = "© 天地图", val bounds: OfflineArchive? = null, val follow: Boolean = true, val revision: Long = System.nanoTime())
class MapRepository(private val root: File, private val scope: CoroutineScope, private val defaultTiandituKey: String = "", private val defaultMaptilerKey: String = "") {
    private val taskAddresses = TaskAddressResolver()
    suspend fun taskAddress(point: com.fairland.fgroundcontrol.mission.BoundaryPoint): String? {
        val key=withContext(Dispatchers.IO) { lock.lock();try { loadKeys();tiandituKey } finally { lock.unlock() } }
        return taskAddresses.resolve(point,key)
    }
    private val mutableState = MutableStateFlow(MapConfiguration())
    val state = mutableState.asStateFlow()
    private var tiandituKey = defaultTiandituKey
    private var maptilerKey = defaultMaptilerKey
    private val lock = kotlinx.coroutines.sync.Mutex()
    private var offlineFile = "imagery.pmtiles"
    init { scope.launch { serialized {
        val store = FoundationStore(root)
        val selection = store.read("map-selection")?.let(::JSONObject)
        offlineFile = selection?.getString("offlineFile") ?: store.read("offline-file") ?: "imagery.pmtiles"
        mutableState.value = configure(selection?.getString("provider") ?: store.read("map-provider") ?: "tianditu")
    } } }
    private fun persistSelection(provider: String, archive: String = offlineFile) {
        FoundationStore(root).write("map-selection", JSONObject().put("provider", provider).put("offlineFile", archive).toString())
    }
    private suspend fun serialized(action: () -> Unit) {
        lock.lock()
        try { action() } catch (e: Exception) { if (e is CancellationException) throw e; mutableState.value = mutableState.value.copy(status = "error") }
        finally { lock.unlock() }
    }
    private fun loadKeys() {
        FoundationStore(root).read("map-keys")?.let { text ->
            val keys = JSONObject(text)
            tiandituKey = keys.optString("tiandituKey").ifEmpty { defaultTiandituKey }; maptilerKey = keys.optString("maptilerKey").ifEmpty { defaultMaptilerKey }
            require(MapStyles.validKey(tiandituKey) && MapStyles.validKey(maptilerKey))
        }
    }
    private fun configure(provider: String, archive: String = offlineFile): MapConfiguration {
        require(provider in setOf("tianditu", "maptiler", "offline"))
        if (provider != "offline") loadKeys()
        val follow = mutableState.value.follow
        return when (provider) {
            "tianditu" -> MapConfiguration(provider, tiandituKey.takeIf { it.isNotEmpty() }?.let(MapStyles::tianditu), status = if (tiandituKey.isEmpty()) "unconfigured" else "loading", follow = follow)
            "maptiler" -> MapConfiguration(provider, maptilerKey.takeIf { it.isNotEmpty() }?.let(MapStyles::maptiler), remoteStyle = true, status = if (maptilerKey.isEmpty()) "unconfigured" else "loading", attribution = "© MapTiler © OpenStreetMap contributors", follow = follow)
            else -> {
                val name = archive
                require(name == "imagery.pmtiles" || name.matches(Regex("imagery-[a-f0-9-]{36}\\.pmtiles")))
                val file = File(root, name)
                if (!file.exists()) MapConfiguration(provider, status = "no_archive", attribution = "", follow = follow)
                else {
                    val bounds = OfflineArchive.inspect(file)
                    val attribution = readAttribution(file)
                    val source = JSONObject().put("type", "raster").put("url", "pmtiles://file://${file.absolutePath}").put("tileSize", 256).put("attribution", attribution)
                    val style = JSONObject().put("version", 8).put("sources", JSONObject().put("offline", source)).put("layers", org.json.JSONArray().put(JSONObject().put("id", "offline").put("type", "raster").put("source", "offline")))
                    MapConfiguration(provider, style.toString(), status = "loading", attribution = attribution, bounds = bounds, follow = follow)
                }
            }
        }
    }
    fun select(provider: String) { scope.launch { serialized {
        val next = configure(provider)
        persistSelection(provider)
        mutableState.value = next
    } } }
    fun follow(enabled: Boolean) { scope.launch { serialized { mutableState.value = mutableState.value.copy(follow = enabled) } } }
    fun loaded(style: String?, revision: Long, success: Boolean) = scope.launch { serialized {
        if (mutableState.value.style == style && mutableState.value.revision == revision) mutableState.value = mutableState.value.copy(status = if (success) "ready" else "error")
    } }
    fun saveKeys(tianditu: String, maptiler: String) { scope.launch { serialized {
        val china = tianditu.trim(); val overseas = maptiler.trim()
        require(MapStyles.validKey(china) && MapStyles.validKey(overseas))
        require(china.isNotEmpty() || overseas.isNotEmpty())
        loadKeys()
        val text = JSONObject().put("tiandituKey", china.ifEmpty { tiandituKey })
            .put("maptilerKey", overseas.ifEmpty { maptilerKey }).toString()
        FoundationStore(root).write("map-keys", text)
        mutableState.value = configure(mutableState.value.provider)
    } } }
    fun importArchive(open: () -> InputStream?) { scope.launch { serialized {
        check(root.mkdirs() || root.isDirectory)
        val temp = File(root, "import.pmtiles.tmp")
        mutableState.value = mutableState.value.copy(status = "importing")
        try {
            checkNotNull(open()).use { input -> FileOutputStream(temp).use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 512L * 1024 * 1024) { "Archive exceeds 512MiB" }; output.write(buffer, 0, count) }
                output.fd.sync()
            } }
            OfflineArchive.inspect(temp); readAttribution(temp)
            val name = "imagery-${java.util.UUID.randomUUID()}.pmtiles"
            val destination = File(root, name)
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            try {
                val next = configure("offline", name)
                // 包名和选中源在同一个原子记录中提交，失败不改变当前配置。
                persistSelection("offline", name)
                offlineFile = name
                mutableState.value = next
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
            // 保留前包供已有渲染请求读取；清理失败不否定已提交的新选择。
            root.listFiles()?.filter { it.name.startsWith("imagery-") && it.extension == "pmtiles" }
                ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }

        } finally { temp.delete() }
    } } }
    private fun readAttribution(file: File): String {
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(127); input.readFully(header)
            val data = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val offset = data.getLong(24); val length = data.getLong(32)
            require(length in 1..65536)
            input.seek(offset); val bytes = ByteArray(length.toInt()); input.readFully(bytes)
            val stream = when (header[97].toInt()) { 1 -> ByteArrayInputStream(bytes); 2 -> GZIPInputStream(ByteArrayInputStream(bytes)); else -> error("Unsupported metadata compression") }
            val text = stream.bufferedReader().use { reader ->
                val buffer = CharArray(65537); var count = 0
                while (count < buffer.size) { val read = reader.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                require(count <= 65536); String(buffer, 0, count)
            }
            return JSONObject(text).optString("attribution").replace(Regex("<[^>]*>"), "").also { require(it.isNotBlank() && it.length <= 1024) { "Archive attribution required" } }
        }
    }
}
