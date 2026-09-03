package com.example.droneservicesapp.data.storage

import com.example.droneservicesapp.domain.model.MissionObstacleShape
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.google.android.gms.maps.model.LatLng
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissionXmlParserTest {

    @Test
    fun `parse supports legacy mission without schema version`() {
        val xml = """
            <field Title="Drone Services Area/Mission Parameters" Name="legacy">
                <missionType>AREA</missionType>
                <operationMode>SURVEY</operationMode>
                <altitude>50</altitude>
                <angleDegrees>90</angleDegrees>
                <lineDistance>70</lineDistance>
                <sprayerIntensityPercentage>75</sprayerIntensityPercentage>
                <flightSpeed>5.0</flightSpeed>
                <LatLngList size="3">
                    <LatLng sequence="0"><Latitude>35.0</Latitude><Longitude>24.0</Longitude></LatLng>
                    <LatLng sequence="1"><Latitude>35.1</Latitude><Longitude>24.0</Longitude></LatLng>
                    <LatLng sequence="2"><Latitude>35.1</Latitude><Longitude>24.1</Longitude></LatLng>
                </LatLngList>
            </field>
        """.trimIndent()

        val mission = MissionXmlParser().parse(xml.byteInputStream())

        assertEquals(1, mission.schemaVersion)
        assertEquals("legacy", mission.name)
        assertEquals(PlanningWorkflow.AREA, mission.workflow)
        assertEquals(PlanningOperationMode.SURVEY, mission.operationMode)
        assertEquals(3, mission.polygon.size)
        assertNull(mission.plannedHomePosition)
        assertEquals(0, mission.obstacles.size)
    }

    @Test
    fun `parse reads version two planning state`() {
        val xml = """
            <field Title="Drone Services Area/Mission Parameters" Name="planned" schemaVersion="2">
                <missionType>POINTS</missionType>
                <operationMode>SPRAY</operationMode>
                <altitude>12</altitude>
                <altitudeReferenceMode>TERRAIN</altitudeReferenceMode>
                <angleDegrees>45</angleDegrees>
                <lineDistance>6</lineDistance>
                <sprayerIntensityPercentage>80</sprayerIntensityPercentage>
                <flightSpeed>4.5</flightSpeed>
                <surveyStripSpacing>30</surveyStripSpacing>
                <surveyHeightAboveTerrain>20</surveyHeightAboveTerrain>
                <surveyOverlapPercent>85</surveyOverlapPercent>
                <surveyGridAngle>10</surveyGridAngle>
                <surveyTerrainSegment>2.5</surveyTerrainSegment>
                <surveyCanopySmoothing>7</surveyCanopySmoothing>
                <HomePosition><Latitude>35.4</Latitude><Longitude>24.4</Longitude></HomePosition>
                <ObstacleList size="2">
                    <Obstacle id="circle-1" shape="CIRCLE">
                        <Center><Latitude>35.41</Latitude><Longitude>24.41</Longitude></Center>
                        <RadiusMeters>15.0</RadiusMeters>
                    </Obstacle>
                    <Obstacle id="poly-1" shape="POLYGON">
                        <VertexList size="3">
                            <Vertex sequence="0"><Latitude>35.0</Latitude><Longitude>24.0</Longitude></Vertex>
                            <Vertex sequence="1"><Latitude>35.1</Latitude><Longitude>24.0</Longitude></Vertex>
                            <Vertex sequence="2"><Latitude>35.1</Latitude><Longitude>24.1</Longitude></Vertex>
                        </VertexList>
                    </Obstacle>
                </ObstacleList>
                <RouteWaypointList size="2">
                    <RouteWaypoint sequence="1">
                        <Id>route-a</Id>
                        <Latitude>35.5</Latitude>
                        <Longitude>24.5</Longitude>
                        <Altitude>12.0</Altitude>
                        <Speed>4.5</Speed>
                        <SprayEnabled>true</SprayEnabled>
                        <SprayerIntensity>80</SprayerIntensity>
                    </RouteWaypoint>
                    <RouteWaypoint sequence="2">
                        <Id>route-b</Id>
                        <Latitude>35.6</Latitude>
                        <Longitude>24.6</Longitude>
                        <Altitude>13.0</Altitude>
                        <Speed>4.5</Speed>
                        <SprayEnabled>false</SprayEnabled>
                        <SprayerIntensity>0</SprayerIntensity>
                    </RouteWaypoint>
                </RouteWaypointList>
                <SurveyPathList size="2">
                    <SurveyPathPoint sequence="0">
                        <Latitude>35.7</Latitude>
                        <Longitude>24.7</Longitude>
                        <DisplayAltitude>21.0</DisplayAltitude>
                        <MissionAltitude>121.0</MissionAltitude>
                    </SurveyPathPoint>
                    <SurveyPathPoint sequence="1">
                        <Latitude>35.8</Latitude>
                        <Longitude>24.8</Longitude>
                        <DisplayAltitude>22.0</DisplayAltitude>
                        <MissionAltitude>122.0</MissionAltitude>
                    </SurveyPathPoint>
                </SurveyPathList>
            </field>
        """.trimIndent()

        val mission = MissionXmlParser().parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(2, mission.schemaVersion)
        assertEquals("planned", mission.name)
        assertEquals(PlanningWorkflow.POINTS, mission.workflow)
        assertEquals(PlanningOperationMode.SPRAY, mission.operationMode)
        assertEquals(35.4, mission.plannedHomePosition?.lat ?: 0.0, 0.0)
        assertEquals(24.4, mission.plannedHomePosition?.lon ?: 0.0, 0.0)
        assertEquals(2, mission.obstacles.size)
        assertEquals(MissionObstacleShape.CIRCLE, mission.obstacles[0].shape)
        assertEquals(15.0, mission.obstacles[0].radiusMeters, 0.0)
        assertEquals(MissionObstacleShape.POLYGON, mission.obstacles[1].shape)
        assertEquals(3, mission.obstacles[1].vertices.size)
        assertEquals(2, mission.routeWaypoints.size)
        assertEquals("route-a", mission.routeWaypoints[0].id)
        assertEquals(2, mission.surveyPath.size)
        assertEquals(2, mission.terrainSurveyWaypoints.size)
        assertEquals(121.0, mission.terrainSurveyWaypoints[0].missionAltitudeMeters, 0.0)
    }

    @Test
    fun `serialize and parse round trips version two mission state`() {
        val original = SavedMission(
            name = "round-trip",
            workflow = PlanningWorkflow.POINTS,
            operationMode = PlanningOperationMode.SPRAY,
            altitudeMeters = 18,
            angleDegrees = 32,
            lineDistanceMeters = 7,
            sprayerIntensityPercent = 65,
            flightSpeedMetersPerSecond = 3.5,
            surveyGridParams = SurveyGridParams(
                stripSpacingMeters = 31,
                heightAboveTerrainMeters = 22,
                overlapPercent = 82,
                gridAngleDegrees = 14,
                terrainSegmentMeters = 3.0,
                canopySmoothingMeters = 6
            ),
            polygon = listOf(
                LatLng(35.0, 24.0),
                LatLng(35.1, 24.0),
                LatLng(35.1, 24.1)
            ),
            surveyPath = listOf(
                LatLng(35.2, 24.2),
                LatLng(35.3, 24.3)
            ),
            terrainSurveyWaypoints = listOf(
                TerrainWaypointSnapshot(
                    position = LatLon(35.2, 24.2),
                    displayAltitudeMeters = 25.0,
                    missionAltitudeMeters = 125.0
                ),
                TerrainWaypointSnapshot(
                    position = LatLon(35.3, 24.3),
                    displayAltitudeMeters = 26.0,
                    missionAltitudeMeters = 126.0
                )
            ),
            routeWaypoints = listOf(
                RouteWaypoint(
                    id = "route-1",
                    index = 1,
                    latitude = 35.4,
                    longitude = 24.4,
                    altitudeMeters = 18.0,
                    speedMetersPerSecond = 3.5,
                    sprayEnabled = true,
                    sprayerIntensityPercent = 65
                )
            ),
            plannedHomePosition = LatLon(35.9, 24.9),
            obstacles = listOf(
                MissionObstacle(
                    id = "circle-1",
                    shape = MissionObstacleShape.CIRCLE,
                    center = LatLon(35.5, 24.5),
                    radiusMeters = 12.0
                )
            )
        )

        val parsed = MissionXmlParser().parse(
            MissionXmlSerializer.serialize(original).byteInputStream()
        )

        assertEquals(SavedMission.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
        assertEquals(original.name, parsed.name)
        assertEquals(original.workflow, parsed.workflow)
        assertEquals(original.operationMode, parsed.operationMode)
        assertEquals(original.plannedHomePosition, parsed.plannedHomePosition)
        assertEquals(original.polygon.size, parsed.polygon.size)
        assertEquals(original.surveyPath.size, parsed.surveyPath.size)
        assertEquals(original.terrainSurveyWaypoints.size, parsed.terrainSurveyWaypoints.size)
        assertEquals(original.routeWaypoints.single().id, parsed.routeWaypoints.single().id)
        assertEquals(original.obstacles.single().center, parsed.obstacles.single().center)
        assertEquals(original.obstacles.single().radiusMeters, parsed.obstacles.single().radiusMeters, 0.0)
    }
}
