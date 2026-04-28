package com.example.droneservicesapp.data.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.droneservicesapp.R
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
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
    
    /**
     * List all mission files in the mission directory.
     */
    fun listMissionFiles(): List<File> {
        val dir = baseDir
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(context.getString(R.string.DroneServicesFilePageSuffix)) }
            ?.toList()
            ?: emptyList()
    }
    
    /**
     * Save mission XML file with the given parameters.
     * 
     * @param polygon List of LatLng coordinates
     * @param lineDist Line distance in meters
     * @param angleDeg Spray angle in degrees
     * @param alt Flight altitude in meters
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
        sprayerPct: Int,
        fileName: String,
        overwrite: Boolean
    ): Boolean {
        // Validate filename
        if (!isValidFileName(fileName)) {
            Log.e("MissionFileStore", "Invalid filename: $fileName (contains path separators)")
            return false
        }
        
        val dir = baseDir
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
            
            // Altitude
            val altitude = doc.createElement("altitude")
            altitude.textContent = alt.toString()
            root.appendChild(altitude)
            
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
    
    /**
     * Open an input stream for reading a mission file.
     * 
     * @param file The mission file to read
     * @return InputStream for the file
     */
    fun openMissionInputStream(file: File): InputStream {
        return file.inputStream()
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
}
