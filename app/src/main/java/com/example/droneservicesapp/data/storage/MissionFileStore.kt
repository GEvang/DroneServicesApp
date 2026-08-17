package com.example.droneservicesapp.data.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.droneservicesapp.R
import com.example.droneservicesapp.domain.model.AltitudeReferenceMode
import com.example.droneservicesapp.domain.model.PlanningOperationMode
import com.example.droneservicesapp.domain.model.PlanningWorkflow
import com.example.droneservicesapp.domain.model.RouteWaypoint
import com.example.droneservicesapp.domain.model.SurveyGridParams
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

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
        routeWaypoints: List<RouteWaypoint> = emptyList(),
        fileName: String,
        overwrite: Boolean
    ): Boolean {
        // Validate filename
        if (!isValidFileName(fileName)) {
            Log.e("MissionFileStore", "Invalid filename: $fileName (contains path separators)")
            return false
        }
        
        val dir = modeDir(planningOperationMode)
        val suffix = context.getString(R.string.DroneServicesFilePageSuffix)
        val normalizedName = fileName.trim()
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
            // Create XML document
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.newDocument()
            
            // Root element
            val root = doc.createElement("field")
            root.setAttribute("Title", "Drone Services Area/Mission Parameters")
            root.setAttribute("Name", normalizedName)
            doc.appendChild(root)

            val missionType = doc.createElement("missionType")
            missionType.textContent = planningWorkflow.name
            root.appendChild(missionType)

            val operationMode = doc.createElement("operationMode")
            operationMode.textContent = planningOperationMode.name
            root.appendChild(operationMode)
            
            // Altitude
            val altitude = doc.createElement("altitude")
            altitude.textContent = alt.toString()
            root.appendChild(altitude)

            val altitudeReference = doc.createElement("altitudeReferenceMode")
            altitudeReference.textContent = altitudeReferenceMode.name
            root.appendChild(altitudeReference)
            
            // Angle degrees
            val angleDegrees = doc.createElement("angleDegrees")
            angleDegrees.textContent = angleDeg.toString()
            root.appendChild(angleDegrees)
            
            // Line distance
            val lineDistance = doc.createElement("lineDistance")
            lineDistance.textContent = lineDist.toString()
            root.appendChild(lineDistance)
            
            // Sprayer intensity percentage
            val sprayerIntensityPercentage = doc.createElement("sprayerIntensityPercentage")
            sprayerIntensityPercentage.textContent = sprayerPct.toString()
            root.appendChild(sprayerIntensityPercentage)

            val missionSpeed = doc.createElement("flightSpeed")
            missionSpeed.textContent = flightSpeed.toString()
            root.appendChild(missionSpeed)

            surveyGridParams?.let { survey ->
                val stripSpacing = doc.createElement("surveyStripSpacing")
                stripSpacing.textContent = survey.stripSpacingMeters.toString()
                root.appendChild(stripSpacing)

                val heightAboveTerrain = doc.createElement("surveyHeightAboveTerrain")
                heightAboveTerrain.textContent = survey.heightAboveTerrainMeters.toString()
                root.appendChild(heightAboveTerrain)

                val overlap = doc.createElement("surveyOverlapPercent")
                overlap.textContent = survey.overlapPercent.toString()
                root.appendChild(overlap)

                val gridAngle = doc.createElement("surveyGridAngle")
                gridAngle.textContent = survey.gridAngleDegrees.toString()
                root.appendChild(gridAngle)

                val terrainSegment = doc.createElement("surveyTerrainSegment")
                terrainSegment.textContent = survey.terrainSegmentMeters.toString()
                root.appendChild(terrainSegment)

                val canopySmoothing = doc.createElement("surveyCanopySmoothing")
                canopySmoothing.textContent = survey.canopySmoothingMeters.toString()
                root.appendChild(canopySmoothing)
            }
            
            // LatLng list
            val latLnglst = doc.createElement("LatLngList")
            latLnglst.setAttribute("size", polygon.size.toString())
            root.appendChild(latLnglst)
            
            for (i in polygon.indices) {
                val latLngElement = doc.createElement("LatLng")
                latLngElement.setAttribute("sequence", i.toString())
                
                val latitudeElement = doc.createElement("Latitude")
                latitudeElement.textContent = polygon[i].latitude.toString()
                latLngElement.appendChild(latitudeElement)
                
                val longitudeElement = doc.createElement("Longitude")
                longitudeElement.textContent = polygon[i].longitude.toString()
                latLngElement.appendChild(longitudeElement)
                
                latLnglst.appendChild(latLngElement)
            }

            val routeList = doc.createElement("RouteWaypointList")
            routeList.setAttribute("size", routeWaypoints.size.toString())
            root.appendChild(routeList)

            routeWaypoints.forEach { waypoint ->
                val waypointElement = doc.createElement("RouteWaypoint")
                waypointElement.setAttribute("sequence", waypoint.index.toString())

                val idElement = doc.createElement("Id")
                idElement.textContent = waypoint.id
                waypointElement.appendChild(idElement)

                val latitudeElement = doc.createElement("Latitude")
                latitudeElement.textContent = waypoint.latitude.toString()
                waypointElement.appendChild(latitudeElement)

                val longitudeElement = doc.createElement("Longitude")
                longitudeElement.textContent = waypoint.longitude.toString()
                waypointElement.appendChild(longitudeElement)

                val altitudeElement = doc.createElement("Altitude")
                altitudeElement.textContent = waypoint.altitudeMeters.toString()
                waypointElement.appendChild(altitudeElement)

                val speedElement = doc.createElement("Speed")
                speedElement.textContent = waypoint.speedMetersPerSecond.toString()
                waypointElement.appendChild(speedElement)

                val sprayEnabledElement = doc.createElement("SprayEnabled")
                sprayEnabledElement.textContent = waypoint.sprayEnabled.toString()
                waypointElement.appendChild(sprayEnabledElement)

                val sprayerIntensityElement = doc.createElement("SprayerIntensity")
                sprayerIntensityElement.textContent = waypoint.sprayerIntensityPercent.toString()
                waypointElement.appendChild(sprayerIntensityElement)

                routeList.appendChild(waypointElement)
            }
            
            // Write to file
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(doc)
            val result = StreamResult(file)
            transformer.transform(source, result)
            
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
        if (!isValidFileName(fileName)) {
            Log.e("MissionFileStore", "Invalid filename: $fileName (contains path separators)")
            return false
        }
        if (waypoints.isEmpty()) {
            return false
        }

        val dir = modeDir(planningOperationMode)
        val suffix = context.getString(R.string.waypoints)
        val normalizedName = fileName.trim().removeSuffix(suffix)
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
