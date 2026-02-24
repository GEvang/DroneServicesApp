package com.example.droneservicesapp

import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MissionFileHandler(
    private var activity: FragmentActivity,
    private var activityViewModel: MainActivityViewModel
) {

    fun saveMissionXML(
        polygon: List<LatLng>,
        lineDist: Int,
        angleDeg: Int,
        alt: Int,
        sprayerIntensPerc: Int,
        fileName: String,
        override: Boolean
    ): Boolean {

        val context = activity.baseContext

        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getString(R.string.mission_directory)
        )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file =
            File(directory, fileName.plus(activity.getString(R.string.DroneServicesFilePageSuffix)))

        if (file.exists()) {
            if (override) {
                file.delete()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.file_already_exists),
                    Toast.LENGTH_LONG
                ).show()
                return false
            }
        }

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()

        val root = doc.createElement("field")
        root.setAttribute("Title", "Drone Services Area/Mission Parameters")
        root.setAttribute("Name", fileName)
        doc.appendChild(root)

        val altitude = doc.createElement("altitude")
        altitude.textContent = alt.toString()
        root.appendChild(altitude)

        val angleDegrees = doc.createElement("angleDegrees")
        angleDegrees.textContent = angleDeg.toString()
        root.appendChild(angleDegrees)

        val lineDistance = doc.createElement("lineDistance")
        lineDistance.textContent = lineDist.toString()
        root.appendChild(lineDistance)

        val sprayerIntensityPercentage = doc.createElement("sprayerIntensityPercentage")
        sprayerIntensityPercentage.textContent = sprayerIntensPerc.toString()
        root.appendChild(sprayerIntensityPercentage)

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

        try {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(doc)
            val result = StreamResult(file)
            transformer.transform(source, result)

            Toast.makeText(
                context,
                context.getString(R.string.file_successfully_saved),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.failed_file_creation),
                Toast.LENGTH_LONG
            ).show()
            Log.i("saveMissionXML", "Failed to create the mission file: $e")
            return false
        }

        return true
    }

    fun parseXml(inputStream: InputStream) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var altitude = -1
        var angleDegrees = -1
        var lineDistance = -1
        var sprayerIntensityPercentage = -1
        val latLngList = ArrayList<LatLng>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "altitude" -> altitude = parser.nextText().toInt()
                        "angleDegrees" -> angleDegrees = parser.nextText().toInt()
                        "lineDistance" -> lineDistance = parser.nextText().toInt()
                        "sprayerIntensityPercentage" -> sprayerIntensityPercentage =
                            parser.nextText().toInt()

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
                    }
                }
            }
            eventType = parser.next()
        }

        activityViewModel.angleProgress.postValue(angleDegrees.toDouble())
        activityViewModel.flightAltProgress.postValue(altitude.toDouble())
        activityViewModel.lineDistanceProgress.postValue(lineDistance.toDouble())
        activityViewModel.sprayerProgress.postValue(sprayerIntensityPercentage.toDouble())

        // ✅ Write into pure model
        activityViewModel.area.value?.apply {
            vertices.clear()
            vertices.addAll(latLngList)
            clearSurveyPath()
        }

        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
    }
}