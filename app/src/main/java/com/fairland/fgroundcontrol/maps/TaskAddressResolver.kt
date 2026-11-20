package com.fairland.fgroundcontrol.maps

import com.fairland.fgroundcontrol.mission.BoundaryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** 天地图首点逆地理编码；成功缓存有界，失败不缓存。 */
class TaskAddressResolver(private val fetch: (String)->String = ::fetchAddressResponse) {
    private val permits=Semaphore(2)
    private val cache=object : LinkedHashMap<String,String>(128,0.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,String>?)=size>128
    }
    suspend fun resolve(point: BoundaryPoint,key: String): String? = withContext(Dispatchers.IO) {
        if(key.isBlank()) return@withContext null
        val cacheKey="$key:${point.latitude}:${point.longitude}"
        permits.withPermit {
            synchronized(cache) {cache[cacheKey]}?.let {return@withPermit it}
            coroutineContext.ensureActive()
            try {
                val request=JSONObject().put("lon",point.longitude).put("lat",point.latitude).put("ver",1)
                val url="https://api.tianditu.gov.cn/geocoder?postStr=${URLEncoder.encode(request.toString(),"UTF-8")}&type=geocode&tk=${URLEncoder.encode(key,"UTF-8")}"
                val response=fetch(url)
                coroutineContext.ensureActive()
                val json=JSONObject(response)
                if(json.optInt("status",-1)!=0) return@withPermit null
                val address=(json.optJSONObject("result")?.opt("formatted_address") as? String)?.trim()?.takeIf {it.isNotEmpty() && it.length<=1024}
                if(address!=null) synchronized(cache) {cache[cacheKey]=address}
                address
            } catch(error: Exception) {
                if(error is CancellationException) throw error
                null // 失败回退坐标，不输出含密钥URL。
            }
        }
    }
}

private fun fetchAddressResponse(url: String): String {
    val connection=URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=false
        check(connection.responseCode==200) { "Address service unavailable" }
        return connection.inputStream.use { input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(4096)
            val deadline=System.nanoTime()+5_000_000_000L
            while(true) {
                check(System.nanoTime()<deadline) { "Address response timeout" }
                val count=input.read(buffer)
                if(count<0) break
                require(output.size()+count<=65536) { "Address response too large" }
                output.write(buffer,0,count)
            }
            output.toString("UTF-8")
        }
    } finally {connection.disconnect()}
}
