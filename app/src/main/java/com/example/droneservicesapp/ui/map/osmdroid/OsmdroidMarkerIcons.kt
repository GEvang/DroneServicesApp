package com.example.droneservicesapp.ui.map.osmdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.droneservicesapp.R
import com.example.droneservicesapp.ui.map.model.MapLabelStyle
import com.example.droneservicesapp.ui.map.model.MapMarkerIcon
import com.example.droneservicesapp.ui.map.model.MapServiceStopKind
import com.example.droneservicesapp.ui.map.model.MapVertexStyle
import kotlin.math.abs
import kotlin.math.max

/**
 * Renders [MapMarkerIcon] artwork for osmdroid markers. The drawing code is carried over from the
 * former imperative editors so the map keeps its established look. Bitmaps are cached by icon.
 */
internal class OsmdroidMarkerIcons(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val cache = object : LinkedHashMap<MapMarkerIcon, Drawable>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MapMarkerIcon, Drawable>?): Boolean =
            size > MAX_CACHED_ICONS
    }

    /** Returns null for [MapMarkerIcon.Default], which keeps osmdroid's own pin. */
    fun drawableFor(icon: MapMarkerIcon): Drawable? {
        if (icon == MapMarkerIcon.Default) return null
        cache[icon]?.let { return it }
        val created = when (icon) {
            MapMarkerIcon.Default -> return null
            is MapMarkerIcon.Aircraft -> aircraft(icon.highlighted)
            MapMarkerIcon.Home -> ContextCompat.getDrawable(context, R.drawable.ic_home_takeoff_marker) ?: return null
            is MapMarkerIcon.NumberedVertex -> numberedVertex(icon)
            is MapMarkerIcon.PathWaypoint -> pathWaypoint(icon.selected)
            is MapMarkerIcon.TerrainWaypoint -> terrainWaypoint(icon.selected)
            is MapMarkerIcon.DirectionArrow -> directionArrow(icon.withShadow)
            is MapMarkerIcon.ServiceStop -> serviceStop(icon.kind)
            is MapMarkerIcon.TextLabel -> textLabel(icon)
            is MapMarkerIcon.ObstacleLabel -> obstacleLabel(icon.letter)
        }
        cache[icon] = created
        return created
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun bitmapDrawable(bitmap: Bitmap) = BitmapDrawable(context.resources, bitmap)

    private fun aircraft(highlighted: Boolean): Drawable {
        val sizeDp = if (highlighted) 76f else 44f
        val droneSizeDp = if (highlighted) 40f else 36f
        val size = (sizeDp * density).toInt()
        val droneSize = (droneSizeDp * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        if (highlighted) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(42, 41, 230, 218)
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = color(R.color.ds_color_shell_active)
                strokeWidth = 3f * density
                style = Paint.Style.STROKE
            }
            canvas.drawCircle(center, center, center - 5f * density, fill)
            canvas.drawCircle(center, center, center - 5f * density, stroke)
        }
        val drone = ContextCompat.getDrawable(context, R.drawable.drone_marker_36)
        val offset = ((size - droneSize) / 2f).toInt()
        drone?.setBounds(offset, offset, offset + droneSize, offset + droneSize)
        drone?.draw(canvas)
        return bitmapDrawable(bitmap)
    }

    private fun numberedVertex(icon: MapMarkerIcon.NumberedVertex): Drawable {
        val route = icon.style == MapVertexStyle.ROUTE
        val size = ((if (route) 38 else 34) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 24, 32)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                !route -> Color.rgb(80, 200, 255)
                icon.selected -> Color.WHITE
                else -> color(R.color.ds_color_shell_active)
            }
            strokeWidth = (if (route && icon.selected) 4f else 2.5f) * density
            style = Paint.Style.STROKE
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f * density
        }
        canvas.drawCircle(center, center, center - stroke.strokeWidth, fill)
        canvas.drawCircle(center, center, center - stroke.strokeWidth, stroke)
        val baseline = center - (text.descent() + text.ascent()) / 2f
        canvas.drawText(icon.number.toString(), center, baseline, text)
        return bitmapDrawable(bitmap)
    }

    private fun pathWaypoint(selected: Boolean): Drawable {
        val sizeDp = if (selected) 30f else 18f
        val size = (sizeDp * density).toInt().coerceAtLeast(if (selected) 24 else 14)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.ds_color_shell_warning)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selected) Color.WHITE else Color.argb(210, 10, 15, 20)
            strokeWidth = if (selected) 2.2f * density else 1.2f * density
            style = Paint.Style.STROKE
        }
        val radius = center - if (selected) 3f * density else 1.5f * density
        if (selected) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, center - density, shadow)
        }
        canvas.drawCircle(center, center, radius, fill)
        canvas.drawCircle(center, center, radius, stroke)
        return bitmapDrawable(bitmap)
    }

    private fun terrainWaypoint(selected: Boolean): Drawable {
        val size = ((if (selected) 28f else 18f) * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.ds_color_shell_warning)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = (if (selected) 2.5f else 1.5f) * density
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(center, center, center - 2f * density, fill)
        canvas.drawCircle(center, center, center - 2f * density, stroke)
        return bitmapDrawable(bitmap)
    }

    private fun directionArrow(withShadow: Boolean): Drawable {
        val size = (26 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = color(R.color.ds_color_shell_active)
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 1.2f * density
            style = Paint.Style.STROKE
        }
        val path = Path().apply {
            moveTo(size - 3f * density, center)
            lineTo(5f * density, size - 5f * density)
            lineTo(10f * density, center)
            lineTo(5f * density, 5f * density)
            close()
        }
        if (withShadow) {
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(center, center, center - 2f * density, shadow)
        }
        canvas.drawPath(path, arrow)
        canvas.drawPath(path, stroke)
        return bitmapDrawable(bitmap)
    }

    private fun serviceStop(kind: MapServiceStopKind): Drawable {
        val size = (38f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (kind) {
                MapServiceStopKind.BATTERY -> Color.rgb(255, 184, 77)
                MapServiceStopKind.TANK -> Color.rgb(55, 170, 255)
            }
            style = Paint.Style.FILL
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val symbol = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 25, 34)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawCircle(center, center, center - 2f * density, background)
        canvas.drawCircle(center, center, center - 2f * density, border)
        when (kind) {
            MapServiceStopKind.BATTERY -> {
                val left = center - 7f * density
                val top = center - 9f * density
                val right = center + 7f * density
                val bottom = center + 9f * density
                canvas.drawRoundRect(left, top, right, bottom, 2f * density, 2f * density, symbol)
                canvas.drawLine(center - 3f * density, top - 2f * density, center + 3f * density, top - 2f * density, symbol)
                canvas.drawLine(center, center - 5f * density, center, center + 5f * density, symbol)
                canvas.drawLine(center - 4f * density, center, center + 4f * density, center, symbol)
            }
            MapServiceStopKind.TANK -> {
                val left = center - 8f * density
                val top = center - 8f * density
                val right = center + 8f * density
                val bottom = center + 9f * density
                canvas.drawRoundRect(left, top, right, bottom, 3f * density, 3f * density, symbol)
                canvas.drawLine(center - 4f * density, top - 3f * density, center + 4f * density, top - 3f * density, symbol)
                canvas.drawLine(center, top - 3f * density, center, top, symbol)
                val waveY = center + 2f * density
                canvas.drawLine(left + 2f * density, waveY, right - 2f * density, waveY, symbol)
            }
        }
        return bitmapDrawable(bitmap)
    }

    private fun textLabel(icon: MapMarkerIcon.TextLabel): Drawable {
        val label = when (icon.style) {
            MapLabelStyle.MEASUREMENT, MapLabelStyle.AREA -> measurementLabel(icon.text, icon.style == MapLabelStyle.AREA)
            MapLabelStyle.DISTANCE -> distanceLabel(icon.text)
        }
        if (icon.offsetXDp == 0f && icon.offsetYDp == 0f) return bitmapDrawable(label)
        // Bake the screen-space offset into a transparent canvas so a centered anchor still
        // places the label beside its coordinate at every zoom level.
        val offsetX = icon.offsetXDp * density
        val offsetY = icon.offsetYDp * density
        val width = (label.width + 2 * abs(offsetX)).toInt().coerceAtLeast(1)
        val height = (label.height + 2 * abs(offsetY)).toInt().coerceAtLeast(1)
        val padded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(
            label,
            (width - label.width) / 2f + offsetX,
            (height - label.height) / 2f + offsetY,
            null,
        )
        label.recycle()
        return bitmapDrawable(padded)
    }

    private fun measurementLabel(text: String, emphasized: Boolean): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (emphasized) 13f else 12f) * density
            isFakeBoldText = true
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val horizontalPadding = (10f * density).toInt()
        val verticalPadding = (6f * density).toInt()
        val width = max(bounds.width() + horizontalPadding * 2, (60f * density).toInt())
        val height = bounds.height() + verticalPadding * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (emphasized) color(R.color.ds_color_shell_active_dim) else Color.argb(228, 12, 21, 29)
            style = Paint.Style.FILL
        }
        val radius = 10f * density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, background)
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, horizontalPadding.toFloat(), baseline, textPaint)
        return bitmap
    }

    private fun distanceLabel(text: String): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val width = (textPaint.measureText(text) + 14f * density).toInt()
        val height = (24f * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 12, 22, 30) }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 8f * density, 8f * density, background)
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, width / 2f, baseline, textPaint)
        return bitmap
    }

    private fun obstacleLabel(letter: String): Drawable {
        val size = (30f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 38, 38) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 15f * density
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawCircle(center, center, center - 2f * density, fill)
        canvas.drawCircle(center, center, center - 2f * density, stroke)
        val baseline = center - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, center, baseline, text)
        return bitmapDrawable(bitmap)
    }

    private companion object {
        const val MAX_CACHED_ICONS = 256
    }
}
