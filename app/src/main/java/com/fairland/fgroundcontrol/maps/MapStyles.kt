package com.fairland.fgroundcontrol.maps

object MapStyles {
    fun validKey(value: String) = value.isEmpty() || value.matches(Regex("[A-Za-z0-9_-]{8,256}"))
    fun tianditu(key: String): String {
        require(key.isNotEmpty() && validKey(key))
        fun source(layer: String) = """{"type":"raster","tiles":["https://t0.tianditu.gov.cn/${layer}_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=$layer&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=$key"],"tileSize":256,"minzoom":1,"maxzoom":18,"attribution":"© 天地图"}"""
        return """{"version":8,"sources":{"imagery":${source("img")},"labels":${source("cia")}},"layers":[{"id":"imagery","type":"raster","source":"imagery"},{"id":"labels","type":"raster","source":"labels"}]}"""
    }
    fun maptiler(key: String): String {
        require(key.isNotEmpty() && validKey(key))
        return "https://api.maptiler.com/maps/hybrid/style.json?key=$key"
    }
}
