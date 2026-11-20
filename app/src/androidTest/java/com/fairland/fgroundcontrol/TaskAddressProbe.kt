package com.fairland.fgroundcontrol

import com.fairland.fgroundcontrol.maps.TaskAddressResolver
import com.fairland.fgroundcontrol.mission.BoundaryPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 本地传输夹具，不向外部服务发送坐标/密钥，不启动Activity。 */
internal object TaskAddressProbe {
    fun verify() = runBlocking {
        val point=BoundaryPoint(22.0,113.0)
        var calls=0
        val resolver=TaskAddressResolver { url ->
            calls++
            val uri=URI(url)
            check(uri.scheme=="https" && uri.host=="api.tianditu.gov.cn")
            val query=uri.rawQuery.split('&').associate { part -> val p=part.split('=',limit=2);p[0] to URLDecoder.decode(p[1],"UTF-8") }
            check(query["type"]=="geocode" && query["tk"]=="fixture-key")
            val body=JSONObject(query.getValue("postStr"))
            check(body.getDouble("lon")==113.0 && body.getDouble("lat")==22.0)
            """{"status":"0","result":{"formatted_address":" 测试地址 "}}"""
        }
        check(resolver.resolve(point,"")==null && calls==0)
        check(resolver.resolve(point,"fixture-key")=="测试地址")
        check(resolver.resolve(point,"fixture-key")=="测试地址" && calls==1)
        for(response in listOf("{\"status\":0,\"result\":{\"formatted_address\":null}}","bad json","{}","{\"status\":1}","{\"status\":0,\"result\":{\"formatted_address\":\"\"}}")) {
            check(TaskAddressResolver {response}.resolve(point,"fixture-key")==null)
        }
        var attempts=0
        val retry=TaskAddressResolver { if(++attempts==1) throw java.net.SocketTimeoutException();"""{"status":0,"result":{"formatted_address":"恢复"}}""" }
        check(retry.resolve(point,"fixture-key")==null)
        check(retry.resolve(point,"fixture-key")=="恢复")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val slow=TaskAddressResolver { entered.countDown();check(release.await(5,TimeUnit.SECONDS));"""{"status":0,"result":{"formatted_address":"迟到"}}""" }
        val job=launch { slow.resolve(point,"fixture-key");error("cancelled result published") }
        check(withContext(Dispatchers.IO) { entered.await(5,TimeUnit.SECONDS) })
        job.cancel();release.countDown();job.join();check(job.isCancelled)
    }
}
