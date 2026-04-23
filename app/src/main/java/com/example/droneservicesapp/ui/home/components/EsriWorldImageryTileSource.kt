package com.example.droneservicesapp.ui.home.components

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object EsriWorldImageryTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    1,
    18,
    256,
    ".jpg",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri"
) {
    override fun getTileURLString(aMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(aMapTileIndex)
        val x = MapTileIndex.getX(aMapTileIndex)
        val y = MapTileIndex.getY(aMapTileIndex)
        return baseUrl + "$z/$y/$x"
    }
}
