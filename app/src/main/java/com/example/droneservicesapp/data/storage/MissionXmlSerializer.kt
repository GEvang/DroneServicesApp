package com.example.droneservicesapp.data.storage

import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.google.android.gms.maps.model.LatLng
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

object MissionXmlSerializer {
    fun serialize(mission: SavedMission): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = doc.createElement("field")
        root.setAttribute("Title", "Drone Services Area/Mission Parameters")
        root.setAttribute("Name", mission.name.trim())
        root.setAttribute("schemaVersion", mission.schemaVersion.toString())
        doc.appendChild(root)

        root.appendTextElement(doc, "missionType", mission.workflow.name)
        root.appendTextElement(doc, "operationMode", mission.operationMode.name)
        root.appendTextElement(doc, "altitude", mission.altitudeMeters.toString())
        root.appendTextElement(doc, "altitudeReferenceMode", mission.altitudeReferenceMode.name)
        root.appendTextElement(doc, "angleDegrees", mission.angleDegrees.toString())
        root.appendTextElement(doc, "lineDistance", mission.lineDistanceMeters.toString())
        root.appendTextElement(doc, "sprayerIntensityPercentage", mission.sprayerIntensityPercent.toString())
        root.appendTextElement(doc, "flightSpeed", mission.flightSpeedMetersPerSecond.toString())
        root.appendTextElement(doc, "surveyStripSpacing", mission.surveyGridParams.stripSpacingMeters.toString())
        root.appendTextElement(doc, "surveyHeightAboveTerrain", mission.surveyGridParams.heightAboveTerrainMeters.toString())
        root.appendTextElement(doc, "surveyOverlapPercent", mission.surveyGridParams.overlapPercent.toString())
        root.appendTextElement(doc, "surveyGridAngle", mission.surveyGridParams.gridAngleDegrees.toString())
        root.appendTextElement(doc, "surveyTerrainSegment", mission.surveyGridParams.terrainSegmentMeters.toString())
        root.appendTextElement(doc, "surveyCanopySmoothing", mission.surveyGridParams.canopySmoothingMeters.toString())

        mission.plannedHomePosition?.let { home ->
            val homePosition = doc.createElement("HomePosition")
            homePosition.appendLatLon(doc, home)
            root.appendChild(homePosition)
        }

        appendObstacleList(doc, root, mission)
        appendLatLngList(doc, root, "LatLngList", "LatLng", mission.polygon)
        appendRouteWaypointList(doc, root, mission)
        appendSurveyPathList(doc, root, mission)

        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    private fun appendObstacleList(doc: Document, root: Element, mission: SavedMission) {
        val validObstacles = mission.obstacles.filter { it.isValid() }
        val obstacleList = doc.createElement("ObstacleList")
        obstacleList.setAttribute("size", validObstacles.size.toString())
        root.appendChild(obstacleList)

        validObstacles.forEach { obstacle ->
            val obstacleElement = doc.createElement("Obstacle")
            obstacleElement.setAttribute("id", obstacle.id)
            obstacleElement.setAttribute("shape", obstacle.shape.name)

            when (obstacle.shape) {
                MissionObstacleShape.CIRCLE -> {
                    obstacle.center?.let { center ->
                        val centerElement = doc.createElement("Center")
                        centerElement.appendLatLon(doc, center)
                        obstacleElement.appendChild(centerElement)
                    }
                    obstacleElement.appendTextElement(doc, "RadiusMeters", obstacle.radiusMeters.toString())
                }
                MissionObstacleShape.POLYGON -> {
                    val vertexList = doc.createElement("VertexList")
                    vertexList.setAttribute("size", obstacle.vertices.size.toString())
                    obstacle.vertices.forEachIndexed { index, vertex ->
                        val vertexElement = doc.createElement("Vertex")
                        vertexElement.setAttribute("sequence", index.toString())
                        vertexElement.appendLatLon(doc, vertex)
                        vertexList.appendChild(vertexElement)
                    }
                    obstacleElement.appendChild(vertexList)
                }
            }

            obstacleList.appendChild(obstacleElement)
        }
    }

    private fun appendRouteWaypointList(doc: Document, root: Element, mission: SavedMission) {
        val routeList = doc.createElement("RouteWaypointList")
        routeList.setAttribute("size", mission.routeWaypoints.size.toString())
        root.appendChild(routeList)

        mission.routeWaypoints.forEach { waypoint ->
            val waypointElement = doc.createElement("RouteWaypoint")
            waypointElement.setAttribute("sequence", waypoint.index.toString())
            waypointElement.appendTextElement(doc, "Id", waypoint.id)
            waypointElement.appendTextElement(doc, "Latitude", waypoint.latitude.toString())
            waypointElement.appendTextElement(doc, "Longitude", waypoint.longitude.toString())
            waypointElement.appendTextElement(doc, "Altitude", waypoint.altitudeMeters.toString())
            waypointElement.appendTextElement(doc, "Speed", waypoint.speedMetersPerSecond.toString())
            waypointElement.appendTextElement(doc, "SprayEnabled", waypoint.sprayEnabled.toString())
            waypointElement.appendTextElement(doc, "SprayerIntensity", waypoint.sprayerIntensityPercent.toString())
            routeList.appendChild(waypointElement)
        }
    }

    private fun appendSurveyPathList(doc: Document, root: Element, mission: SavedMission) {
        val surveyPathList = doc.createElement("SurveyPathList")
        surveyPathList.setAttribute("size", mission.surveyPath.size.toString())
        root.appendChild(surveyPathList)

        mission.surveyPath.forEachIndexed { index, point ->
            val pointElement = doc.createElement("SurveyPathPoint")
            pointElement.setAttribute("sequence", index.toString())
            pointElement.appendTextElement(doc, "Latitude", point.latitude.toString())
            pointElement.appendTextElement(doc, "Longitude", point.longitude.toString())

            mission.terrainSurveyWaypoints.getOrNull(index)?.let { terrainWaypoint ->
                pointElement.appendTextElement(doc, "DisplayAltitude", terrainWaypoint.displayAltitudeMeters.toString())
                pointElement.appendTextElement(doc, "MissionAltitude", terrainWaypoint.missionAltitudeMeters.toString())
            }

            surveyPathList.appendChild(pointElement)
        }
    }

    private fun appendLatLngList(
        doc: Document,
        root: Element,
        listName: String,
        itemName: String,
        points: List<LatLng>
    ) {
        val list = doc.createElement(listName)
        list.setAttribute("size", points.size.toString())
        root.appendChild(list)

        points.forEachIndexed { index, point ->
            val pointElement = doc.createElement(itemName)
            pointElement.setAttribute("sequence", index.toString())
            pointElement.appendTextElement(doc, "Latitude", point.latitude.toString())
            pointElement.appendTextElement(doc, "Longitude", point.longitude.toString())
            list.appendChild(pointElement)
        }
    }

    private fun Element.appendLatLon(doc: Document, position: LatLon) {
        appendTextElement(doc, "Latitude", position.lat.toString())
        appendTextElement(doc, "Longitude", position.lon.toString())
    }

    private fun Element.appendTextElement(doc: Document, name: String, value: String) {
        val element = doc.createElement(name)
        element.textContent = value
        appendChild(element)
    }
}
