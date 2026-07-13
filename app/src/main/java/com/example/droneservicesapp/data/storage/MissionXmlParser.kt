package com.example.droneservicesapp.data.storage

import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

class MissionXmlParser(
    private var activity: FragmentActivity,
    private var activityViewModel: MainActivityViewModel
) {

    fun parseXml(inputStream: InputStream) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var altitude = -1
        var altitudeReferenceMode = AltitudeReferenceMode.RELATIVE
        var angleDegrees = -1
        var lineDistance = -1
        var sprayerIntensityPercentage = -1
        var flightSpeed = 1.0
        var missionType = PlanningWorkflow.AREA
        var operationMode = PlanningOperationMode.SURVEY
        var surveyStripSpacing = -1
        var surveyHeightAboveTerrain = -1
        var surveyOverlapPercent = -1
        var surveyGridAngle = -1
        var surveyTerrainSegment = -1.0
        var surveyCanopySmoothing = -1
        val latLngList = ArrayList<LatLng>()
        val routeWaypoints = ArrayList<RouteWaypoint>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "missionType" ->
                            missionType = parseWorkflow(parser.nextText())
                        "operationMode" ->
                            operationMode = parseOperationMode(parser.nextText())
                        "altitude" -> altitude = parser.nextText().toInt()
                        "altitudeReferenceMode" ->
                            altitudeReferenceMode = AltitudeReferenceMode.fromStorageValue(parser.nextText())
                        "angleDegrees" -> angleDegrees = parser.nextText().toInt()
                        "lineDistance" -> lineDistance = parser.nextText().toInt()
                        "sprayerIntensityPercentage" ->
                            sprayerIntensityPercentage = parser.nextText().toInt()
                        "flightSpeed" -> flightSpeed = parser.nextText().toDouble()
                        "surveyStripSpacing" -> surveyStripSpacing = parser.nextText().toInt()
                        "surveyHeightAboveTerrain" -> surveyHeightAboveTerrain = parser.nextText().toInt()
                        "surveyOverlapPercent" -> surveyOverlapPercent = parser.nextText().toInt()
                        "surveyGridAngle" -> surveyGridAngle = parser.nextText().toInt()
                        "surveyTerrainSegment" -> surveyTerrainSegment = parser.nextText().toDouble()
                        "surveyCanopySmoothing" -> surveyCanopySmoothing = parser.nextText().toInt()

                        "LatLngList" -> {
                            while (parser.next() != XmlPullParser.END_TAG) {
                                when (parser.name) {
                                    "LatLng" -> {
                                        var latitude = -1000.0
                                        var longitude = -1000.0
                                        while (parser.next() != XmlPullParser.END_TAG) {
                                            when (parser.name) {
                                                "Latitude" -> latitude = parser.nextText().toDouble()
                                                "Longitude" -> longitude = parser.nextText().toDouble()
                                            }
                                        }
                                        latLngList.add(LatLng(latitude, longitude))
                                    }
                                }
                            }
                        }
                        "RouteWaypointList" -> {
                            while (parser.next() != XmlPullParser.END_TAG) {
                                when (parser.name) {
                                    "RouteWaypoint" -> {
                                        var id = ""
                                        var latitude = -1000.0
                                        var longitude = -1000.0
                                        var waypointAltitude = altitude.takeIf { it >= 0 }?.toDouble() ?: 2.0
                                        var waypointSpeed = flightSpeed
                                        var sprayEnabled = false
                                        var waypointSprayerIntensity = sprayerIntensityPercentage.takeIf { it >= 0 } ?: 0
                                        val sequence = parser.getAttributeValue(null, "sequence")?.toIntOrNull()

                                        while (parser.next() != XmlPullParser.END_TAG) {
                                            when (parser.name) {
                                                "Id" -> id = parser.nextText()
                                                "Latitude" -> latitude = parser.nextText().toDouble()
                                                "Longitude" -> longitude = parser.nextText().toDouble()
                                                "Altitude" -> waypointAltitude = parser.nextText().toDouble()
                                                "Speed" -> waypointSpeed = parser.nextText().toDouble()
                                                "SprayEnabled" -> sprayEnabled = parser.nextText().toBoolean()
                                                "SprayerIntensity" ->
                                                    waypointSprayerIntensity = parser.nextText().toInt()
                                            }
                                        }

                                        routeWaypoints.add(
                                            RouteWaypoint(
                                                id = id.ifBlank { "route-${sequence ?: routeWaypoints.size + 1}" },
                                                index = sequence ?: routeWaypoints.size + 1,
                                                latitude = latitude,
                                                longitude = longitude,
                                                altitudeMeters = waypointAltitude,
                                                speedMetersPerSecond = waypointSpeed,
                                                sprayEnabled = sprayEnabled,
                                                sprayerIntensityPercent = waypointSprayerIntensity
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (angleDegrees >= 0) activityViewModel.angleProgress.postValue(angleDegrees.toDouble())
        if (altitude >= 0) activityViewModel.flightAltProgress.postValue(altitude.toDouble())
        activityViewModel.setAltitudeReferenceMode(altitudeReferenceMode)
        if (lineDistance >= 0) activityViewModel.lineDistanceProgress.postValue(lineDistance.toDouble())
        if (sprayerIntensityPercentage >= 0) {
            activityViewModel.sprayerProgress.postValue(sprayerIntensityPercentage.toDouble())
        }
        activityViewModel.flightSpeed.postValue(flightSpeed)
        if (surveyStripSpacing >= 0) activityViewModel.updateSurveyStripSpacing(surveyStripSpacing)
        if (surveyHeightAboveTerrain >= 0) activityViewModel.updateSurveyHeightAboveTerrain(surveyHeightAboveTerrain)
        if (surveyOverlapPercent >= 0) activityViewModel.updateSurveyOverlap(surveyOverlapPercent)
        if (surveyGridAngle >= 0) activityViewModel.updateSurveyGridAngle(surveyGridAngle)
        if (surveyTerrainSegment >= 0.0) activityViewModel.updateSurveyTerrainSegment(surveyTerrainSegment)
        if (surveyCanopySmoothing >= 0) activityViewModel.updateSurveyCanopySmoothing(surveyCanopySmoothing)
        activityViewModel.setPlanningOperationMode(operationMode)
        activityViewModel.setPlanningWorkflow(missionType)
        activityViewModel.surveyGridParams.postValue(
            SurveyGridParams(
                stripSpacingMeters = surveyStripSpacing.takeIf { it >= 0 }
                    ?: lineDistance.takeIf { it >= 0 } ?: 8,
                heightAboveTerrainMeters = surveyHeightAboveTerrain.takeIf { it >= 0 }
                    ?: altitude.takeIf { it >= 0 } ?: 5,
                overlapPercent = surveyOverlapPercent.takeIf { it >= 0 } ?: 20,
                gridAngleDegrees = surveyGridAngle.takeIf { it >= 0 }
                    ?: angleDegrees.takeIf { it >= 0 } ?: 0,
                terrainSegmentMeters = surveyTerrainSegment.takeIf { it >= 0.0 } ?: 2.5,
                canopySmoothingMeters = surveyCanopySmoothing.takeIf { it >= 0 } ?: 5
            )
        )
        activityViewModel.setPolygonVertices(latLngList)
        activityViewModel.setRouteWaypoints(routeWaypoints)
        activityViewModel.surveyPath.postValue(emptyList())
        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
    }

    private fun parseWorkflow(value: String?): PlanningWorkflow {
        return runCatching { PlanningWorkflow.valueOf(value.orEmpty()) }.getOrDefault(PlanningWorkflow.AREA)
    }

    private fun parseOperationMode(value: String?): PlanningOperationMode {
        return runCatching {
            PlanningOperationMode.valueOf(value.orEmpty())
        }.getOrDefault(PlanningOperationMode.SURVEY)
    }
}
