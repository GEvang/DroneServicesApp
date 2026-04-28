package com.example.droneservicesapp.data.geoawareness

import android.util.Log
import com.example.droneservicesapp.domain.geoawareness.GeoZone
import com.example.droneservicesapp.domain.geoawareness.GeoZoneApplicability
import com.example.droneservicesapp.domain.geoawareness.GeoZoneAuthority
import com.example.droneservicesapp.domain.geoawareness.GeoZoneGeometry
import com.example.droneservicesapp.domain.geoawareness.GeoZoneRestriction
import com.example.droneservicesapp.domain.model.LatLon
import org.json.JSONArray
import org.json.JSONObject

class GeoZoneJsonParser {

    fun parse(rawJson: String): List<GeoZone> {
        val features = try {
            JSONObject(rawJson).optJSONArray("features") ?: return emptyList()
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse geozone root JSON", error)
            return emptyList()
        }

        val zones = mutableListOf<GeoZone>()
        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index)
            if (feature == null) {
                Log.w(TAG, "Skipping malformed feature at index $index: not an object")
                continue
            }

            try {
                val zone = parseFeature(feature, index)
                if (zone != null) {
                    zones += zone
                }
            } catch (error: Exception) {
                Log.w(TAG, "Skipping malformed feature at index $index", error)
            }
        }

        return zones
    }

    private fun parseFeature(feature: JSONObject, index: Int): GeoZone? {
        val id = optStringOrNull(feature, "identifier") ?: "UNKNOWN-$index"
        val country = optStringOrNull(feature, "country").orEmpty()
        val name = optStringOrNull(feature, "name") ?: id
        val type = optStringOrNull(feature, "type")
        val restriction = GeoZoneRestriction.fromRaw(optStringOrNull(feature, "restriction"))
        val reason = parseStringList(feature.optJSONArray("reason"))
        val otherReasonInfo = optStringOrNull(feature, "otherReasonInfo")
        val message = optStringOrNull(feature, "message")
        val applicability = parseApplicability(feature.optJSONArray("applicability"))
        val authorities = parseAuthorities(feature.optJSONArray("zoneAuthority"))
        val geometries = parseGeometries(feature.optJSONArray("geometry"))
        val extendedProperties = feature.optJSONObject("extendedProperties")
        val colorHex = optStringOrNull(extendedProperties, "color")
        val arc = optStringOrNull(extendedProperties, "arc")

        return GeoZone(
            id = id,
            country = country,
            name = name,
            type = type,
            restriction = restriction,
            reason = reason,
            otherReasonInfo = otherReasonInfo,
            message = message,
            applicability = applicability,
            authorities = authorities,
            geometries = geometries,
            colorHex = colorHex,
            arc = arc,
            isDummy = arc == "DUMMY" || id.contains("DUMMY")
        )
    }

    private fun optStringOrNull(jsonObject: JSONObject?, key: String): String? {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }

        return jsonObject.optString(key).trim().takeIf { it.isNotEmpty() }
    }

    private fun parseStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) {
            return emptyList()
        }

        val values = mutableListOf<String>()
        for (index in 0 until jsonArray.length()) {
            val value = jsonArray.optString(index).trim()
            if (value.isNotEmpty()) {
                values += value
            }
        }
        return values
    }

    private fun parseApplicability(jsonArray: JSONArray?): List<GeoZoneApplicability> {
        if (jsonArray == null) {
            return emptyList()
        }

        val applicability = mutableListOf<GeoZoneApplicability>()
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            applicability += GeoZoneApplicability(
                startDateTime = optStringOrNull(item, "startDateTime"),
                endDateTime = optStringOrNull(item, "endDateTime"),
                permanent = parseBooleanYesNo(item.opt("permanent"))
            )
        }
        return applicability
    }

    private fun parseAuthorities(jsonArray: JSONArray?): List<GeoZoneAuthority> {
        if (jsonArray == null) {
            return emptyList()
        }

        val authorities = mutableListOf<GeoZoneAuthority>()
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            authorities += GeoZoneAuthority(
                name = optStringOrNull(item, "name"),
                service = optStringOrNull(item, "service"),
                contactName = optStringOrNull(item, "contactName"),
                siteUrl = optStringOrNull(item, "siteURL"),
                email = optStringOrNull(item, "email"),
                phone = optStringOrNull(item, "phone"),
                purpose = optStringOrNull(item, "purpose")
            )
        }
        return authorities
    }

    private fun parseGeometries(jsonArray: JSONArray?): List<GeoZoneGeometry> {
        if (jsonArray == null) {
            return emptyList()
        }

        val geometries = mutableListOf<GeoZoneGeometry>()
        for (index in 0 until jsonArray.length()) {
            val geometry = jsonArray.optJSONObject(index)
            if (geometry == null) {
                Log.w(TAG, "Skipping malformed geometry at index $index: not an object")
                continue
            }

            val horizontalProjection = geometry.optJSONObject("horizontalProjection")
            if (horizontalProjection == null) {
                Log.w(TAG, "Skipping geometry at index $index: missing horizontalProjection")
                continue
            }

            val lowerLimit = geometry.optDoubleOrNull("lowerLimit")
            val upperLimit = geometry.optDoubleOrNull("upperLimit")
            when (optStringOrNull(horizontalProjection, "type")) {
                "Circle" -> {
                    val parsed = parseCircle(horizontalProjection, lowerLimit, upperLimit)
                    if (parsed != null) {
                        geometries += parsed
                    } else {
                        Log.w(TAG, "Skipping malformed circle geometry at index $index")
                    }
                }
                "Polygon" -> {
                    val parsed = parsePolygon(horizontalProjection, lowerLimit, upperLimit)
                    if (parsed != null) {
                        geometries += parsed
                    } else {
                        Log.w(TAG, "Skipping malformed polygon geometry at index $index")
                    }
                }
                else -> Log.w(TAG, "Ignoring unsupported geometry type at index $index")
            }
        }
        return geometries
    }

    private fun parseCircle(
        horizontalProjection: JSONObject,
        lowerLimitMeters: Double?,
        upperLimitMeters: Double?
    ): GeoZoneGeometry.Circle? {
        val centerArray = horizontalProjection.optJSONArray("center") ?: return null
        if (centerArray.length() < 2) {
            return null
        }

        val lon = centerArray.optDoubleOrNull(0) ?: return null
        val lat = centerArray.optDoubleOrNull(1) ?: return null
        val radius = horizontalProjection.optDoubleOrNull("radius") ?: return null
        if (radius <= 0.0) {
            return null
        }

        return GeoZoneGeometry.Circle(
            center = LatLon(lat = lat, lon = lon),
            radiusMeters = radius,
            lowerLimitMeters = lowerLimitMeters,
            upperLimitMeters = upperLimitMeters
        )
    }

    private fun parsePolygon(
        horizontalProjection: JSONObject,
        lowerLimitMeters: Double?,
        upperLimitMeters: Double?
    ): GeoZoneGeometry.Polygon? {
        val coordinates = horizontalProjection.optJSONArray("coordinates") ?: return null
        val rings = mutableListOf<List<LatLon>>()

        for (ringIndex in 0 until coordinates.length()) {
            val ringArray = coordinates.optJSONArray(ringIndex) ?: continue
            val ring = mutableListOf<LatLon>()

            for (pointIndex in 0 until ringArray.length()) {
                val pointArray = ringArray.optJSONArray(pointIndex) ?: continue
                if (pointArray.length() < 2) {
                    continue
                }

                val lon = pointArray.optDoubleOrNull(0) ?: continue
                val lat = pointArray.optDoubleOrNull(1) ?: continue
                ring += LatLon(lat = lat, lon = lon)
            }

            if (ring.isNotEmpty()) {
                rings += ring
            }
        }

        if (rings.isEmpty()) {
            return null
        }

        return GeoZoneGeometry.Polygon(
            rings = rings,
            lowerLimitMeters = lowerLimitMeters,
            upperLimitMeters = upperLimitMeters
        )
    }

    private fun parseBooleanYesNo(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("YES", ignoreCase = true)
            else -> false
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONArray.optDoubleOrNull(index: Int): Double? {
        if (index < 0 || index >= length()) {
            return null
        }

        return when (val value = opt(index)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    companion object {
        private const val TAG = "GeoZoneParser"
    }
}
