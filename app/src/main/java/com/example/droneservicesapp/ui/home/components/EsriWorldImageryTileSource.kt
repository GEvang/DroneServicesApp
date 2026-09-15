package com.example.droneservicesapp.ui.home.components

import android.content.Context
import android.graphics.Color
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.TilesOverlay

private const val ESRI_ATTRIBUTION =
    "© Esri, Vantor, Earthstar Geographics, HERE, Garmin, OpenStreetMap contributors, GIS User Community"

object EsriWorldImageryTileSource : OnlineTileSourceBase(
    "EsriWorldImagery",
    1,
    18,
    256,
    ".jpg",
    arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    ESRI_ATTRIBUTION
) {
    override fun getTileURLString(aMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(aMapTileIndex)
        val x = MapTileIndex.getX(aMapTileIndex)
        val y = MapTileIndex.getY(aMapTileIndex)
        return baseUrl + "$z/$y/$x"
    }
}

object EsriWorldBoundariesAndPlacesTileSource : OnlineTileSourceBase(
    "EsriWorldBoundariesAndPlaces",
    0,
    18,
    256,
    ".png",
    arrayOf(
        "https://services.arcgisonline.com/ArcGIS/rest/services/Reference/" +
            "World_Boundaries_and_Places/MapServer/tile/"
    ),
    ESRI_ATTRIBUTION
) {
    override fun getTileURLString(aMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(aMapTileIndex)
        val x = MapTileIndex.getX(aMapTileIndex)
        val y = MapTileIndex.getY(aMapTileIndex)
        return baseUrl + "$z/$y/$x"
    }
}

/** Installs Esri's transparent place-name reference layer over World Imagery. */
class EsriMapLayers private constructor(
    private val mapView: MapView,
    val labelsTileProvider: MapTileProviderBasic,
    private val labelsOverlay: TilesOverlay,
    private val attributionOverlay: CopyrightOverlay
) {
    val labelsEnabled: Boolean
        get() = labelsOverlay.isEnabled

    fun refreshLabelsEnabled() {
        val enabled = MapDisplayPreferences.areLabelsEnabled(mapView.context)
        if (labelsOverlay.isEnabled != enabled) {
            labelsOverlay.isEnabled = enabled
            mapView.invalidate()
        }
    }

    fun bringAttributionToFront() {
        mapView.overlays.remove(attributionOverlay)
        mapView.overlays.add(attributionOverlay)
    }

    companion object {
        fun install(context: Context, mapView: MapView): EsriMapLayers {
            mapView.setTileSource(EsriWorldImageryTileSource)

            val appContext = context.applicationContext
            val labelsProvider = MapTileProviderBasic(
                appContext,
                EsriWorldBoundariesAndPlacesTileSource
            ).also { provider ->
                provider.tileRequestCompleteHandlers.add(mapView.tileRequestCompleteHandler)
            }
            val labelsOverlay = TilesOverlay(labelsProvider, appContext).apply {
                // Missing/loading reference tiles must remain transparent over the imagery.
                setLoadingBackgroundColor(Color.TRANSPARENT)
                setLoadingLineColor(Color.TRANSPARENT)
                setOptionsMenuEnabled(false)
            }
            mapView.overlays.add(0, labelsOverlay)

            val attribution = CopyrightOverlay(context).apply {
                setCopyrightNotice(ESRI_ATTRIBUTION)
                setTextColor(Color.WHITE)
                setTextSize(8)
                setAlignBottom(true)
                setAlignRight(false)
                setOffset(8, 8)
            }
            mapView.overlays.add(attribution)

            return EsriMapLayers(mapView, labelsProvider, labelsOverlay, attribution).also {
                it.refreshLabelsEnabled()
            }
        }
    }
}
