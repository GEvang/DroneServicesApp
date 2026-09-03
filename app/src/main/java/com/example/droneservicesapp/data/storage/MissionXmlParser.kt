package com.example.droneservicesapp.data.storage

import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class MissionXmlParser(
    private var activity: FragmentActivity? = null,
    private var activityViewModel: MainActivityViewModel? = null
) {
    private var obstacleFallbackId = 0

    fun parseXml(inputStream: InputStream) {
        requireNotNull(activityViewModel).applySavedMission(parse(inputStream))
    }

    fun parse(inputStream: InputStream): SavedMission {
        obstacleFallbackId = 0
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream)
        val root = doc.documentElement
        val schemaVersion = root.getAttribute("schemaVersion").toIntOrNull() ?: 1
        val missionName = root.getAttribute("Name").orEmpty()
        val altitude = childText(root, "altitude")?.toIntOrNull() ?: -1
        val altitudeReferenceMode = childText(root, "altitudeReferenceMode")
            ?.let(AltitudeReferenceMode::fromStorageValue)
            ?: AltitudeReferenceMode.RELATIVE
        val angleDegrees = childText(root, "angleDegrees")?.toIntOrNull() ?: -1
        val lineDistance = childText(root, "lineDistance")?.toIntOrNull() ?: -1
        val sprayerIntensityPercentage = childText(root, "sprayerIntensityPercentage")?.toIntOrNull() ?: -1
        val flightSpeed = childText(root, "flightSpeed")?.toDoubleOrNull() ?: 1.0
        val missionType = parseWorkflow(childText(root, "missionType"))
        val operationMode = parseOperationMode(childText(root, "operationMode"))
        val surveyStripSpacing = childText(root, "surveyStripSpacing")?.toIntOrNull() ?: -1
        val surveyHeightAboveTerrain = childText(root, "surveyHeightAboveTerrain")?.toIntOrNull() ?: -1
        val surveyOverlapPercent = childText(root, "surveyOverlapPercent")?.toIntOrNull() ?: -1
        val surveyGridAngle = childText(root, "surveyGridAngle")?.toIntOrNull() ?: -1
        val surveyTerrainSegment = childText(root, "surveyTerrainSegment")?.toDoubleOrNull() ?: -1.0
        val surveyCanopySmoothing = childText(root, "surveyCanopySmoothing")?.toIntOrNull() ?: -1
        val plannedHomePosition = childElement(root, "HomePosition")?.let(::parseLatLonElement)
        val latLngList = childElement(root, "LatLngList")
            ?.childElements("LatLng")
            ?.map(::parseLatLngElement)
            ?.filter { isValidLatLon(LatLon(it.latitude, it.longitude)) }
            .orEmpty()
        val routeWaypoints = parseRouteWaypoints(root, altitude, flightSpeed, sprayerIntensityPercentage)
        val surveyPathPoints = childElement(root, "SurveyPathList")
            ?.childElements("SurveyPathPoint")
            ?.map(::parseSurveyPathPoint)
            .orEmpty()
        val obstacles = childElement(root, "ObstacleList")
            ?.childElements("Obstacle")
            ?.map(::parseObstacleElement)
            ?.filter { it.isValid() }
            .orEmpty()

        return SavedMission(
            schemaVersion = schemaVersion,
            name = missionName,
            workflow = missionType,
            operationMode = operationMode,
            altitudeMeters = altitude.takeIf { it >= 0 } ?: 0,
            altitudeReferenceMode = altitudeReferenceMode,
            angleDegrees = angleDegrees.takeIf { it >= 0 } ?: 90,
            lineDistanceMeters = lineDistance.takeIf { it >= 0 } ?: 5,
            sprayerIntensityPercent = sprayerIntensityPercentage.takeIf { it >= 0 } ?: 75,
            flightSpeedMetersPerSecond = flightSpeed,
            surveyGridParams = SurveyGridParams(
                stripSpacingMeters = surveyStripSpacing.takeIf { it >= 0 }
                    ?: lineDistance.takeIf { it >= 0 } ?: 70,
                heightAboveTerrainMeters = surveyHeightAboveTerrain.takeIf { it >= 0 }
                    ?: altitude.takeIf { it >= 0 } ?: 50,
                overlapPercent = surveyOverlapPercent.takeIf { it >= 0 } ?: 80,
                gridAngleDegrees = surveyGridAngle.takeIf { it >= 0 }
                    ?: angleDegrees.takeIf { it >= 0 } ?: 90,
                terrainSegmentMeters = surveyTerrainSegment.takeIf { it >= 0.0 } ?: 2.5,
                canopySmoothingMeters = surveyCanopySmoothing.takeIf { it >= 0 } ?: 5
            ),
            polygon = latLngList,
            surveyPath = surveyPathPoints.map { it.position },
            terrainSurveyWaypoints = surveyPathPoints.mapNotNull { it.terrainWaypoint },
            routeWaypoints = routeWaypoints,
            plannedHomePosition = plannedHomePosition?.takeIf(::isValidLatLon),
            obstacles = obstacles
        )
    }

    private fun parseRouteWaypoints(
        root: Element,
        fallbackAltitude: Int,
        fallbackSpeed: Double,
        fallbackSprayerIntensity: Int
    ): List<RouteWaypoint> {
        return childElement(root, "RouteWaypointList")
            ?.childElements("RouteWaypoint")
            ?.mapIndexed { index, waypointElement ->
                val sequence = waypointElement.getAttribute("sequence").toIntOrNull() ?: index + 1
                RouteWaypoint(
                    id = childText(waypointElement, "Id").orEmpty().ifBlank { "route-$sequence" },
                    index = sequence,
                    latitude = childText(waypointElement, "Latitude")?.toDoubleOrNull() ?: -1000.0,
                    longitude = childText(waypointElement, "Longitude")?.toDoubleOrNull() ?: -1000.0,
                    altitudeMeters = childText(waypointElement, "Altitude")?.toDoubleOrNull()
                        ?: fallbackAltitude.takeIf { it >= 0 }?.toDouble()
                        ?: 2.0,
                    speedMetersPerSecond = childText(waypointElement, "Speed")?.toDoubleOrNull()
                        ?: fallbackSpeed,
                    sprayEnabled = childText(waypointElement, "SprayEnabled")?.toBoolean() ?: false,
                    sprayerIntensityPercent = childText(waypointElement, "SprayerIntensity")?.toIntOrNull()
                        ?: fallbackSprayerIntensity.takeIf { it >= 0 }
                        ?: 0
                )
            }
            ?.filter { isValidLatLon(LatLon(it.latitude, it.longitude)) }
            .orEmpty()
    }

    private fun parseObstacleElement(element: Element): MissionObstacle {
        val shape = runCatching {
            MissionObstacleShape.valueOf(element.getAttribute("shape").orEmpty())
        }.getOrDefault(MissionObstacleShape.CIRCLE)
        val vertices = childElement(element, "VertexList")
            ?.childElements("Vertex")
            ?.map(::parseLatLonElement)
            ?.filter(::isValidLatLon)
            .orEmpty()

        return MissionObstacle(
            id = element.getAttribute("id").ifBlank { "obstacle-${obstacleFallbackId++}" },
            shape = shape,
            center = childElement(element, "Center")?.let(::parseLatLonElement)?.takeIf(::isValidLatLon),
            radiusMeters = childText(element, "RadiusMeters")?.toDoubleOrNull() ?: 0.0,
            vertices = vertices
        )
    }

    private fun parseLatLngElement(element: Element): LatLng {
        return LatLng(
            childText(element, "Latitude")?.toDoubleOrNull() ?: -1000.0,
            childText(element, "Longitude")?.toDoubleOrNull() ?: -1000.0
        )
    }

    private fun parseLatLonElement(element: Element): LatLon {
        return LatLon(
            lat = childText(element, "Latitude")?.toDoubleOrNull() ?: -1000.0,
            lon = childText(element, "Longitude")?.toDoubleOrNull() ?: -1000.0
        )
    }

    private fun parseSurveyPathPoint(element: Element): ParsedSurveyPathPoint {
        val latitude = childText(element, "Latitude")?.toDoubleOrNull() ?: -1000.0
        val longitude = childText(element, "Longitude")?.toDoubleOrNull() ?: -1000.0
        val position = LatLng(latitude, longitude)
        val latLon = LatLon(latitude, longitude)
        val displayAltitude = childText(element, "DisplayAltitude")?.toDoubleOrNull()
        val missionAltitude = childText(element, "MissionAltitude")?.toDoubleOrNull()
        val terrainWaypoint = if (isValidLatLon(latLon) && displayAltitude != null && missionAltitude != null) {
            TerrainWaypointSnapshot(
                position = latLon,
                displayAltitudeMeters = displayAltitude,
                missionAltitudeMeters = missionAltitude
            )
        } else {
            null
        }
        return ParsedSurveyPathPoint(position = position, terrainWaypoint = terrainWaypoint)
    }

    private fun childText(parent: Element, tagName: String): String? {
        return childElement(parent, tagName)?.textContent?.trim()
    }

    private fun childElement(parent: Element, tagName: String): Element? {
        return parent.childElements(tagName).firstOrNull()
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val elements = mutableListOf<Element>()
        val children = childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.tagName == tagName) {
                elements += element
            }
        }
        return elements
    }

    private data class ParsedSurveyPathPoint(
        val position: LatLng,
        val terrainWaypoint: TerrainWaypointSnapshot?,
    )

    private fun parseWorkflow(value: String?): PlanningWorkflow {
        return runCatching { PlanningWorkflow.valueOf(value.orEmpty()) }.getOrDefault(PlanningWorkflow.AREA)
    }

    private fun parseOperationMode(value: String?): PlanningOperationMode {
        return runCatching {
            PlanningOperationMode.valueOf(value.orEmpty())
        }.getOrDefault(PlanningOperationMode.SURVEY)
    }

    private fun isValidLatLon(position: LatLon): Boolean {
        return position.lat.isFinite() &&
            position.lon.isFinite() &&
            position.lat in -90.0..90.0 &&
            position.lon in -180.0..180.0
    }
}
