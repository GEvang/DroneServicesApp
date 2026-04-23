package com.example.droneservicesapp.data.storage

import androidx.fragment.app.FragmentActivity
import com.example.droneservicesapp.ui.home.model.MainActivityViewModel
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
        activityViewModel.missionArea.value?.apply {
            vertices.clear()
            vertices.addAll(latLngList)
            clearSurveyPath()
        }

        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
    }
}
