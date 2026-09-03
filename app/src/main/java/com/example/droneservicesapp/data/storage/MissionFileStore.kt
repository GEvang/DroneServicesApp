package com.example.droneservicesapp.data.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.LatLon
import com.example.droneservicesapp.domain.model.MissionObstacle
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.example.droneservicesapp.domain.terrain.TerrainWaypoint
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class MissionFileStore(
    private val context: Context
) {
    
    private val baseDir: File
        get() {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val dir = File(base, context.getString(R.string.mission_directory))
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private fun modeDir(mode: PlanningOperationMode): File {
        val dir = File(baseDir, mode.name.lowercase(Locale.ROOT))
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * List all mission files in the mission directory.
     */
    fun listMissionFiles(mode: PlanningOperationMode? = null): List<File> {
        val suffix = context.getString(R.string.DroneServicesFilePageSuffix)
        val waypointSuffix = context.getString(R.string.waypoints)
        val scopedFiles = buildList {
            if (mode == null) {
                addAll(baseDir.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(suffix) || it.name.endsWith(waypointSuffix)) }
                    .orEmpty())
                PlanningOperationMode.values().forEach { operationMode ->
                    addAll(modeDir(operationMode).listFiles()
                        ?.filter { it.isFile && (it.name.endsWith(suffix) || it.name.endsWith(waypointSuffix)) }
                        .orEmpty())
                }
            } else {
                addAll(modeDir(mode).listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(suffix) || it.name.endsWith(waypointSuffix)) }
                    .orEmpty())
                addAll(baseDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(suffix) && readOperationMode(it) == mode }
                    .orEmpty())
                addAll(baseDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(waypointSuffix) }
                    .orEmpty())
            }
        }
        return scopedFiles.distinctBy { it.absolutePath }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    
    /**
     * Save mission XML file with the given parameters.
     * 
     * @param polygon List of LatLng coordinates
     * @param lineDist Line distance in meters
     * @param angleDeg Spray angle in degrees
     * @param alt Flight altitude in meters
     * @param altitudeReferenceMode MAVLink altitude reference mode
     * @param sprayerPct Sprayer intensity percentage
     * @param fileName Name of the file (without extension)
     * @param overwrite If true, overwrites existing file; if false, returns false if file exists
     * @return true if file was successfully saved, false otherwise
     */
    fun saveMissionXml(
        polygon: List<LatLng>,
        lineDist: Int,
        angleDeg: Int,
        alt: Int,
        altitudeReferenceMode: AltitudeReferenceMode = AltitudeReferenceMode.RELATIVE,
        sprayerPct: Int,
        flightSpeed: Double,
        planningWorkflow: PlanningWorkflow = PlanningWorkflow.AREA,
        planningOperationMode: PlanningOperationMode = PlanningOperationMode.SURVEY,
        surveyGridParams: SurveyGridParams? = null,
        surveyPath: List<LatLng> = emptyList(),
        terrainSurveyWaypoints: List<TerrainWaypoint> = emptyList(),
        routeWaypoints: List<RouteWaypoint> = emptyList(),
        plannedHomePosition: LatLon? = null,
        obstacles: List<MissionObstacle> = emptyList(),
        fileName: String,
        overwrite: Boolean
    ): Boolean {
        val normalizedName = normalizeMissionBaseName(fileName)

        if (normalizedName.isBlank() || !isValidFileName(normalizedName)) {
            Log.e("MissionFileStore", "Invalid filename: $fileName (contains path separators)")
            return false
        }
        
        val dir = modeDir(planningOperationMode)
        val suffix = context.getString(R.string.DroneServicesFilePageSuffix)
        val file = File(dir, normalizedName.plus(suffix))
        
        // Check if file exists
        if (file.exists()) {
            if (!overwrite) {
                Log.w("MissionFileStore", "File already exists: ${file.absolutePath}")
                return false
            } else {
                file.delete()
            }
        }
        
        return try {
            val mission = SavedMission(
                name = normalizedName,
                workflow = planningWorkflow,
                operationMode = planningOperationMode,
                altitudeMeters = alt,
                altitudeReferenceMode = altitudeReferenceMode,
                angleDegrees = angleDeg,
                lineDistanceMeters = lineDist,
                sprayerIntensityPercent = sprayerPct,
                flightSpeedMetersPerSecond = flightSpeed,
                surveyGridParams = surveyGridParams ?: SurveyGridParams(),
                polygon = polygon,
                surveyPath = surveyPath,
                terrainSurveyWaypoints = terrainSurveyWaypoints.map {
                    TerrainWaypointSnapshot(
                        position = it.latLon,
                        displayAltitudeMeters = it.displayAltitudeMeters,
                        missionAltitudeMeters = it.missionAltitudeMeters
                    )
                },
                routeWaypoints = routeWaypoints,
                plannedHomePosition = plannedHomePosition,
                obstacles = obstacles
            )
            file.writeText(MissionXmlSerializer.serialize(mission))
            Log.i("MissionFileStore", "Mission file saved successfully: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("MissionFileStore", "Failed to save mission file: ${e.message}", e)
            false
        }
    }

    fun saveWaypointsFile(
        waypoints: List<LatLng>,
        altitudesMeters: List<Float>?,
        fallbackAltitudeMeters: Float,
        planningOperationMode: PlanningOperationMode,
        fileName: String,
        overwrite: Boolean
    ): Boolean {
        val normalizedName = normalizeMissionBaseName(fileName)

        if (normalizedName.isBlank() || !isValidFileName(normalizedName)) {
            Log.e("MissionFileStore", "Invalid filename: $fileName (contains path separators)")
            return false
        }
        if (waypoints.isEmpty()) {
            return false
        }

        val dir = modeDir(planningOperationMode)
        val suffix = context.getString(R.string.waypoints)
        val file = File(dir, normalizedName.plus(suffix))
        if (file.exists()) {
            if (!overwrite) {
                Log.w("MissionFileStore", "Waypoint file already exists: ${file.absolutePath}")
                return false
            }
            file.delete()
        }

        return try {
            val lines = buildList {
                add("QGC WPL 110")
                waypoints.forEachIndexed { index, point ->
                    val altitude = altitudesMeters?.getOrNull(index) ?: fallbackAltitudeMeters
                    add(
                        listOf(
                            index,
                            0,
                            3,
                            16,
                            0,
                            0,
                            0,
                            0,
                            String.format(Locale.US, "%.7f", point.latitude),
                            String.format(Locale.US, "%.7f", point.longitude),
                            String.format(Locale.US, "%.3f", altitude),
                            1
                        ).joinToString("\t")
                    )
                }
            }
            file.writeText(lines.joinToString(separator = System.lineSeparator()) + System.lineSeparator())
            Log.i("MissionFileStore", "Waypoint file saved successfully: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("MissionFileStore", "Failed to save waypoint file: ${e.message}", e)
            false
        }
    }

    fun parseWaypointsFile(inputStream: InputStream): List<WaypointFileItem> {
        return inputStream.bufferedReader().useLines { lines ->
            lines
                .drop(1)
                .mapNotNull { line -> parseWaypointLine(line) }
                .toList()
        }
    }
    
    /**
     * Open an input stream for reading a mission file.
     * 
     * @param file The mission file to read
     * @return InputStream for the file
     */
    fun openMissionInputStream(file: File): InputStream {
        return file.inputStream()
    }

    fun readOperationMode(file: File): PlanningOperationMode? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            val nodes = doc.getElementsByTagName("operationMode")
            if (nodes.length == 0) {
                null
            } else {
                PlanningOperationMode.valueOf(nodes.item(0).textContent)
            }
        }.getOrNull()
    }
    
    /**
     * Validate filename for path separators.
     * 
     * @param fileName The filename to validate
     * @return true if valid, false if contains path separators
     */
    private fun isValidFileName(fileName: String): Boolean {
        return !fileName.contains("/") && !fileName.contains("\\")
    }

    private fun normalizeMissionBaseName(fileName: String): String {
        var normalizedName = fileName.trim()
        val waypointSuffix = context.getString(R.string.waypoints)
        val missionSuffix = context.getString(R.string.DroneServicesFilePageSuffix)

        if (normalizedName.endsWith(waypointSuffix)) {
            normalizedName = normalizedName.removeSuffix(waypointSuffix)
        }
        if (normalizedName.endsWith(missionSuffix)) {
            normalizedName = normalizedName.removeSuffix(missionSuffix)
        }

        return normalizedName.trim()
    }

    private fun parseWaypointLine(line: String): WaypointFileItem? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 12) return null
        val command = parts[3].toIntOrNull() ?: return null
        if (command != 16) return null
        val latitude = parts[8].toDoubleOrNull() ?: return null
        val longitude = parts[9].toDoubleOrNull() ?: return null
        val altitude = parts[10].toDoubleOrNull() ?: return null
        return WaypointFileItem(latitude, longitude, altitude)
    }

    data class WaypointFileItem(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double
    )
}
