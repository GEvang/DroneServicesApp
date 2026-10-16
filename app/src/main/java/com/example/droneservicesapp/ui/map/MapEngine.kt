package com.example.droneservicesapp.ui.map

import com.example.droneservicesapp.ui.map.osmdroid.OsmdroidMapEngine

/**
 * The single place that binds engine-independent map services to the selected engine (osmdroid,
 * per the Phase 2 decision). Swapping engines means changing this binding and the map surface.
 */
object MapEngine {
    val tileCache: MapTileCache = OsmdroidMapEngine
}
