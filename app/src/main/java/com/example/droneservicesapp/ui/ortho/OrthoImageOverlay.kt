package com.example.droneservicesapp.ui.ortho

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import com.example.droneservicesapp.data.ortho.OrthoBounds
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class OrthoImageOverlay(
    private val bitmap: Bitmap,
    private val bounds: OrthoBounds
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = DEFAULT_ALPHA
    }
    private val northWest = GeoPoint(bounds.maxLat, bounds.minLon)
    private val southEast = GeoPoint(bounds.minLat, bounds.maxLon)
    private val topLeftPoint = Point()
    private val bottomRightPoint = Point()

    var opacity: Float = 0.85f
        set(value) {
            field = value.coerceIn(0f, 1f)
            paint.alpha = (field * 255).toInt()
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        projection.toPixels(northWest, topLeftPoint)
        projection.toPixels(southEast, bottomRightPoint)

        val rect = RectF(
            minOf(topLeftPoint.x, bottomRightPoint.x).toFloat(),
            minOf(topLeftPoint.y, bottomRightPoint.y).toFloat(),
            maxOf(topLeftPoint.x, bottomRightPoint.x).toFloat(),
            maxOf(topLeftPoint.y, bottomRightPoint.y).toFloat()
        )
        if (rect.width() <= 1f || rect.height() <= 1f) return
        canvas.drawBitmap(bitmap, null, rect, paint)
    }

    companion object {
        private const val DEFAULT_ALPHA = 217
    }
}
