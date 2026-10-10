package com.example.droneservicesapp.ui.home.model

import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.terrain.PointCloudCoverage
import com.example.droneservicesapp.mavserver.TerrainProvisioningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionParamsPresenterTest {
    @Test
    fun disconnectedTerrainSurveyOffersDownloadAndBlocksUploadWhilePreparing() {
        val presentation = MissionParamsPresenter.present(
            state = defaultState(),
            workflow = PlanningWorkflow.AREA,
            pointCloudCoverage = PointCloudCoverage.NONE,
            terrainState = TerrainProvisioningState.PreparingSource(1, 4, "tile", 25),
            droneConnected = false,
            sourceReadyMatchesMission = false,
        )

        assertTrue(presentation.terrainDownloadVisible)
        assertFalse(presentation.terrainDownloadEnabled)
        assertFalse(presentation.missionUploadEnabled)
        assertEquals(25, presentation.terrainProgressPercent)
    }

    @Test
    fun threeDimensionalSprayRequiresRelativeAltitudeAndTerrainProfileFields() {
        val presentation = MissionParamsPresenter.present(
            state = defaultState().copy(operationMode = PlanningOperationMode.SPRAY),
            workflow = PlanningWorkflow.AREA,
            pointCloudCoverage = PointCloudCoverage.COMPLETE,
            terrainState = TerrainProvisioningState.Idle,
            droneConnected = true,
            sourceReadyMatchesMission = false,
        )

        assertTrue(presentation.showSprayFields)
        assertTrue(presentation.showTerrainProfileFields)
        assertEquals(AltitudeReferenceMode.RELATIVE, presentation.requiredAltitudeReference)
    }

    private fun defaultState() = MissionParamsUiState(
        operationMode = PlanningOperationMode.SURVEY,
        angle = 90,
        lineDistance = 5,
        altitude = 0,
        takeoffHeight = 5,
        sprayerIntensity = 75,
        surveyStripSpacing = 70,
        surveyHeightAboveTerrain = 50,
        surveyOverlap = 80,
        surveyGridAngle = 90,
        surveyTerrainSegment = 2.5,
        surveyCanopySmoothing = 5,
        flightSpeed = 5.0,
        estimatedFlightMinutes = 1,
        altitudeReferenceMode = AltitudeReferenceMode.TERRAIN,
    )
}
