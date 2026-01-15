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
)
{

    fun saveMissionXML(
        polygon: List<LatLng>,
        lineDist: Int,
        angleDeg: Int,
        alt: Int,
        sprayerIntensPerc: Int,
        fileName: String,
        override: Boolean): Boolean {

        val context = activity.baseContext

        // Get the directory for the user's public documents directory.
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), context.getString(
            R.string.mission_directory))

        // Create the custom directory if it doesn't exist
        if ( !directory.exists() ) {
            directory.mkdirs()
        }

        // Create a new file in the custom directory
        val file = File(directory, fileName.plus(activity.getString(R.string.DroneServicesFilePageSuffix)))

        if( file.exists() )
        {
            if( override ){
                file.delete()
            }
            else
            {
                Toast.makeText(context, context.getString(R.string.file_already_exists), Toast.LENGTH_LONG).show()
                return false
            }
        }

        Log.i("saveMissionXML", "saveMission: Field directory $directory created")

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()

        val root = doc.createElement("field")
        root.setAttribute("Title", "Drone Services Area/Mission Parameters")
        root.setAttribute("Name", fileName)
        doc.appendChild(root)
        Log.i("saveMissionXML", "saveMission: added title")

        val altitude = doc.createElement("altitude")
        altitude.textContent = alt.toString()
        root.appendChild(altitude)
        Log.i("saveMissionXML", "saveMission: added altitude $alt")

        val angleDegrees = doc.createElement("angleDegrees")
        angleDegrees.textContent = angleDeg.toString()
        root.appendChild(angleDegrees)
        Log.i("saveMissionXML", "saveMission: added angle degrees $angleDeg")

        val lineDistance = doc.createElement("lineDistance")
        lineDistance.textContent = lineDist.toString()
        root.appendChild(lineDistance)
        Log.i("saveMissionXML", "saveMission: added line distances $lineDist")

        val sprayerIntensityPercentage = doc.createElement("sprayerIntensityPercentage")
        sprayerIntensityPercentage.textContent = sprayerIntensPerc.toString()
        root.appendChild(sprayerIntensityPercentage)
        Log.i("saveMissionXML", "saveMission: added sprayer intensity percentage $sprayerIntensPerc")

        val latLnglst = doc.createElement("LatLngList")
        latLnglst.setAttribute("size", polygon.size.toString())
        root.appendChild(latLnglst)
        Log.i("saveMissionXML", "saveMission: added latlng list count ${polygon.size}")

        for ( i in polygon.indices) {
            val latLngElement = doc.createElement("LatLng")
            latLngElement.setAttribute("sequence", i.toString())

            val latitudeElement = doc.createElement("Latitude")
            latitudeElement.textContent = polygon[i].latitude.toString()
            latLngElement.appendChild(latitudeElement)

            val longitudeElement = doc.createElement("Longitude")
            longitudeElement.textContent = polygon[i].longitude.toString()
            latLngElement.appendChild(longitudeElement)

            latLnglst.appendChild(latLngElement)
            Log.i(
                "saveMissionXML", "saveMission: added latlng list element $i " +
                    "where latlng ${polygon[i].latitude} ${polygon[i].longitude}")
        }

        // Write the file contents to the file
        try {

            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val source = DOMSource(doc)
            Log.i("saveMissionXML", "saveMission: created DOM $source")

            val result = StreamResult(file)
            Log.i("saveMissionXML", "saveMission: created Stream Result $result")
            transformer.transform(source, result)
            Log.i("saveMissionXML", "saveMission: transformed Source to Result $result")

            Toast.makeText(context, context.getString(R.string.file_successfully_saved), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Handle the exception here.
            Toast.makeText(context, context.getString(R.string.failed_file_creation), Toast.LENGTH_LONG).show()
            Log.i("saveMissionXML", "saveMission: Failed to create the mission file: $e")

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
                        "sprayerIntensityPercentage" -> sprayerIntensityPercentage = parser.nextText().toInt()
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
        // Do something with the extracted data, e.g. print it to the log
        Log.d("XML Parser", "altitude: $altitude")
        Log.d("XML Parser", "angleDegrees: $angleDegrees")
        Log.d("XML Parser", "lineDistance: $lineDistance")
        Log.d("XML Parser", "sprayerIntensityPercentage: $sprayerIntensityPercentage")
        latLngList.forEachIndexed { index, latLng ->
            Log.d("XML Parser", "LatLng $index: lat=${latLng.latitude}, lng=${latLng.longitude}")
        }

        activityViewModel.angleProgress.postValue(angleDegrees.toDouble())
        activityViewModel.flightAltProgress.postValue(altitude.toDouble())

        activityViewModel.lineDistanceProgress.postValue(lineDistance.toDouble())
        activityViewModel.sprayerProgress.postValue(sprayerIntensityPercentage.toDouble())

        activityViewModel.area.value!!.polygon?.remove()
        activityViewModel.area.value!!.polygonEdges = latLngList

        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
    }

}