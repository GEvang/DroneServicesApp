package com.example.droneservicesapp.mavserver

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.droneservicesapp.R
import com.example.droneservicesapp.activities.MainActivityViewModel
import com.google.android.gms.maps.model.LatLng
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.DistanceSensor
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.MavCmd
import io.dronefleet.mavlink.common.MavFrame
import io.dronefleet.mavlink.common.MavMissionResult
import io.dronefleet.mavlink.common.MavMissionType
import io.dronefleet.mavlink.common.MavSensorOrientation
import io.dronefleet.mavlink.common.MissionAck
import io.dronefleet.mavlink.common.MissionClearAll
import io.dronefleet.mavlink.common.MissionCount
import io.dronefleet.mavlink.common.MissionCurrent
import io.dronefleet.mavlink.common.MissionItemInt
import io.dronefleet.mavlink.common.MissionRequest
import io.dronefleet.mavlink.common.MissionRequestInt
import io.dronefleet.mavlink.common.MissionRequestList
import io.dronefleet.mavlink.common.RcChannels
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavState
import io.dronefleet.mavlink.minimal.MavType
import io.dronefleet.mavlink.util.EnumValue
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Integer.max
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.pow


class MavLinkComm(private var activity: FragmentActivity?) {
    private val COM_MISSION_TYPE_CLEAR_ALL = 0

    private var missionUploadCountInt = 0
    private var expectedDownloadItemInt = -1

    private lateinit var droneViewModel: DroneViewModel
    private lateinit var activityViewModel: MainActivityViewModel

    private val disposables: MutableList<Disposable?> = ArrayList()

    private lateinit var mavCon: MavlinkConnection
    private lateinit var mavDownCon: MavlinkConnection
    private lateinit var mavUpCon: MavlinkConnection
    private lateinit var mavHrtbtCon: MavlinkConnection

    private var targetComponentId: Int = 0
    private var targetSystemId: Int = 0
    private var componentId: Int = 99
    private var systemId: Int = 254

    private var listenPort: Int = 14550

    @Volatile
    private var lastNonHeartbeatMs: Long = 0L


    // Create PipedInputStream and PipedOutputStream for input and output streams respectively
    private val mavRcvPIS = PipedInputStream()
    private val mavRcvPOS = PipedOutputStream(mavRcvPIS)
    private val mavSndPIS = PipedInputStream()
    private val mavSndPOS = PipedOutputStream(mavSndPIS)
    private val mavMisDwnPIS = PipedInputStream()
    private val mavMisDwnPOS = PipedOutputStream(mavMisDwnPIS)
    private val mavMisUpPIS = PipedInputStream()
    private val mavMisUpPOS = PipedOutputStream(mavMisUpPIS)
    private val mavHrtbtPIS = PipedInputStream()
    private val mavHrtbtPOS = PipedOutputStream(mavHrtbtPIS)


    fun setActivity(activity: FragmentActivity?) {
        this.activity = activity
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun startConn(conn: String, port: Int) {
        droneViewModel = ViewModelProvider(activity!!)[DroneViewModel::class.java]
        activityViewModel = ViewModelProvider(activity!!)[MainActivityViewModel::class.java]

        Log.i("MavlinkConnection", "Restarted Connection...")

        // After establishing a connection, we proceed to building a MavlinkConnection instance.
        mavCon = MavlinkConnection.create(mavRcvPIS, mavSndPOS)
        mavDownCon = MavlinkConnection.create(mavMisDwnPIS, mavMisDwnPOS)
        mavUpCon = MavlinkConnection.create(mavMisUpPIS, mavMisUpPOS)
        mavHrtbtCon = MavlinkConnection.create(mavHrtbtPIS, mavHrtbtPOS)
        listenPort = port

        subscribeUdpConnection()
        subscribeMavlinkMsgReader()
        subscribeConnectionState()
        subscribeGcsHeartbeat()
//        subscribeLiquidLevel()
    }

    fun stopConn() {
        for (disposable in disposables) {
            disposable?.dispose()
        }
        disposables.clear()

        Log.i("MavlinkConnection", "Stopped Connection...")
    }


    private fun subscribeUdpConnection() {
        disposables.add(
            // Subscribe to the Observable and update the LiveData value
            Observable.create<Boolean> { emitter ->

                var remoteIP: InetAddress? = null
                var remotePort: Int = -1
                val outBuffer = ByteArray(4096)

                try {
                    // Create a DatagramSocket for receiving data
                    val udpSocket = DatagramSocket(listenPort)
                    val receiveData = ByteArray(4096)
                    while (!emitter.isDisposed) {

                        if (udpSocket.receiveBufferSize > 0) {
                            Log.i("udpThread", "emitter.isDisposed ${emitter.isDisposed}")

                            // Receive data from UDP port
                            val receivePacket = DatagramPacket(receiveData, receiveData.size)
                            udpSocket.receive(receivePacket)

                            // Write the received data to PipedOutputStream
                            Log.i("UDP_RAW_LEN", "len=" + receivePacket.length)
                            mavRcvPOS.write(receivePacket.data, 0, receivePacket.length)
                            Log.i(
                                "UDP_RAW_HEX", receivePacket.data.take(receivePacket.length)
                                    .joinToString(" ") { "%02X".format(it) })

                            mavRcvPOS.flush()
                            Log.i("udpThread", "receivePacket.data ${receivePacket.data}")

                            remoteIP = receivePacket.address
                            remotePort = receivePacket.port
                        }

                        Log.i("udpThread", "mavSenderPIS.available() ${mavSndPIS.available()}")
                        if (mavSndPIS.available() > 0 && remoteIP != null) {
                            val bytesRead = mavSndPIS.read(outBuffer)
                            Log.i("udpThread", "bytesRead $bytesRead")

                            val outputPacket = DatagramPacket(
                                outBuffer, bytesRead,
                                remoteIP, remotePort
                            )

                            udpSocket.send(outputPacket)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
                .distinctUntilChanged() // Only emit when the value changes
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { conState ->
                        // Update the LiveData value

                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("UDPconnection", "Error: ${error.message}")
                        // Handle the error, e.g. show error message, retry, etc.
                    }
                )
        )
    }


    private fun subscribeMavlinkMsgReader() {
        disposables.add(
            Observable.create<MavlinkMessage<*>> { emitter ->

                try {
                    val handler = Handler(Looper.getMainLooper())

                    // Now we are ready to read and send messages.
                    var message: MavlinkMessage<*>
                    while (mavCon.next().also { message = it } != null) {

                        emitter.onNext(message)

                        when (message.payload) {

                            is Heartbeat -> run heartbeat@{

                                val heartbeatMessage = message as MavlinkMessage<Heartbeat>
                                val hb = heartbeatMessage.payload

                                val isGcs = hb.type().entry() == MavType.MAV_TYPE_GCS
                                val hasAutopilot =
                                    hb.autopilot()
                                        .entry() != io.dronefleet.mavlink.minimal.MavAutopilot.MAV_AUTOPILOT_INVALID

                                if (isGcs || !hasAutopilot) return@heartbeat

                                Log.i(
                                    "HB_SRC",
                                    "sys=${message.originSystemId} comp=${message.originComponentId} baseMode=0x${
                                        (message as MavlinkMessage<Heartbeat>).payload.baseMode()
                                            .value().toString(16)
                                    }"
                                )


                                // ---- ARMED STATE (CORRECT) ----
                                val baseMode = hb.baseMode().value()
                                val isArmed =
                                    (baseMode and 0x80) != 0   // MAV_MODE_FLAG_SAFETY_ARMED

                                handler.post {
                                    droneViewModel.armedState.postValue(isArmed)
                                }


                                targetComponentId = heartbeatMessage.originComponentId
                                targetSystemId = heartbeatMessage.originSystemId

                                handler.post {
                                    droneViewModel.droneFlightMode.postValue(
                                        heartbeatMessage.payload.customMode().toInt()
                                    )
                                }
                                Log.i(
                                    "MavlinkHeartbeat",
                                    "Heartbeat flightMode: ${heartbeatMessage.payload.customMode()}"
                                )


                                mavHrtbtCon.send2(
                                    heartbeatMessage.originSystemId,
                                    heartbeatMessage.originComponentId,
                                    heartbeatMessage.payload
                                )
                                mavHrtbtPOS.flush()
                            }

                            is BatteryStatus -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val batteryMessage = message as MavlinkMessage<BatteryStatus>

                                Log.i(
                                    "MavlinkBattery",
                                    "BatteryStatus voltage percentage: ${batteryMessage.payload.batteryRemaining()}"
                                )
                                Log.i(
                                    "MavlinkBattery",
                                    "Battery1Status voltage: ${batteryMessage.payload.voltages()[0].toFloat()}"
                                )

                                handler.post {
                                    droneViewModel.droneBatteryVoltage.postValue(
                                        batteryMessage.payload.voltages()[0].toFloat() * 10.0F.pow(
                                            -3
                                        )
                                    )
                                    droneViewModel.droneBatteryPercentage.postValue(
                                        batteryMessage.payload.batteryRemaining()
                                            .toFloat() * 10.0F.pow(-2)
                                    )
                                }

                                Log.i(
                                    "MavlinkBattery2",
                                    "Battery2Status voltage: ${batteryMessage.payload.voltages()[1].toFloat()}"
                                )
                                handler.post {
                                    droneViewModel.liquidLevel.postValue(batteryMessage.payload.voltages()[1].toFloat())
                                }
                            }

                            is GlobalPositionInt -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val positionMessage = message as MavlinkMessage<GlobalPositionInt>

                                Log.i(
                                    "MavlinkLocation",
                                    "Location   lat: ${
                                        positionMessage.payload.lat().toFloat() * 10.0.pow(-7)
                                    }"
                                )
                                Log.i(
                                    "MavlinkLocation",
                                    "Location   lon: ${
                                        positionMessage.payload.lon().toFloat() * 10.0.pow(-7)
                                    }"
                                )
                                Log.i(
                                    "MavlinkLocation",
                                    "Location   relativeAlt: ${
                                        positionMessage.payload.relativeAlt() * 10.0.pow(-3)
                                    }"
                                )
                                Log.i(
                                    "MavlinkLocation",
                                    "Location   heading: ${positionMessage.payload.hdg()}"
                                )

                                val loc = Location("")
                                loc.latitude =
                                    positionMessage.payload.lat().toFloat() * 10.0.pow(-7)
                                loc.longitude =
                                    positionMessage.payload.lon().toFloat() * 10.0.pow(-7)
                                loc.altitude = positionMessage.payload.relativeAlt() * 10.0.pow(-3)

                                handler.post {
                                    droneViewModel.droneHeading.postValue(
                                        positionMessage.payload.hdg().toDouble()
                                    )
                                    droneViewModel.droneLocationLiveData.postValue(loc)
                                }
                            }

                            is RcChannels -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val positionMessage = message as MavlinkMessage<RcChannels>

                                Log.i(
                                    "MavlinkRcChannels",
                                    "RcChannels   rssi: ${positionMessage.payload.rssi()}"
                                )

                                handler.post {
                                    droneViewModel.rcRSSI.postValue(positionMessage.payload.rssi() * 100.0F / 255.0F)
                                }
                            }

                            is MissionAck -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionAckMsg = message as MavlinkMessage<MissionAck>
                                Log.i("MavlinkAck", "missionAckMsg ${missionAckMsg.payload.type()}")

                                if (missionAckMsg.payload.targetSystem() == systemId &&
                                    missionAckMsg.payload.targetComponent() == componentId
                                ) {

                                    mavUpCon.send2(
                                        missionAckMsg.originSystemId,
                                        missionAckMsg.originComponentId,
                                        missionAckMsg.payload
                                    )
                                    mavMisUpPOS.flush()
                                }
                            }

                            is MissionCurrent -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionCurrentMsg = message as MavlinkMessage<MissionCurrent>
                                Log.i(
                                    "MissionCurrent",
                                    "missionCurrentMsg ${missionCurrentMsg.payload}"
                                )
                            }

                            is MissionRequestInt -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionReqIntMsg = message as MavlinkMessage<MissionRequestInt>
                                Log.i(
                                    "MissionRequestInt",
                                    "Received MissionRequestInt ${missionReqIntMsg.payload}"
                                )

                                if (missionReqIntMsg.payload.targetSystem() == systemId &&
                                    missionReqIntMsg.payload.targetComponent() == componentId
                                ) {
                                    missionUploadCountInt = missionReqIntMsg.payload.seq()

                                    mavUpCon.send2(
                                        missionReqIntMsg.originSystemId,
                                        missionReqIntMsg.originComponentId,
                                        missionReqIntMsg.payload
                                    )
                                    mavMisUpPOS.flush()
                                }
                            }

                            is MissionRequest -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionReqMsg = message as MavlinkMessage<MissionRequest>
                                Log.i(
                                    "MissionRequest",
                                    "Received missionReqMsg ${missionReqMsg.payload}"
                                )

                                if (missionReqMsg.payload.targetSystem() == systemId &&
                                    missionReqMsg.payload.targetComponent() == componentId
                                ) {
                                    missionUploadCountInt = missionReqMsg.payload.seq()

                                    mavUpCon.send2(
                                        missionReqMsg.originSystemId,
                                        missionReqMsg.originComponentId,
                                        missionReqMsg.payload
                                    )
                                    mavMisUpPOS.flush()
                                }
                            }

                            is MissionCount -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionCountMsg = message as MavlinkMessage<MissionCount>
                                Log.i(
                                    "MissionCount",
                                    "Received missionReqMsg ${missionCountMsg.payload}"
                                )

                                if (missionCountMsg.payload.targetSystem() == systemId &&
                                    missionCountMsg.payload.targetComponent() == componentId
                                ) {
                                    mavDownCon.send2(
                                        missionCountMsg.originSystemId,
                                        missionCountMsg.originComponentId,
                                        missionCountMsg.payload
                                    )
                                    mavMisDwnPOS.flush()
                                }
                            }

                            is MissionItemInt -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val missionItemIntMsg = message as MavlinkMessage<MissionItemInt>
                                Log.i(
                                    "MissionItemInt",
                                    "Received MissionItemInt ${missionItemIntMsg.payload}"
                                )

                                if (missionItemIntMsg.payload.targetSystem() == systemId &&
                                    missionItemIntMsg.payload.targetComponent() == componentId
                                ) {
                                    mavDownCon.send2(
                                        missionItemIntMsg.originSystemId,
                                        missionItemIntMsg.originComponentId,
                                        missionItemIntMsg.payload
                                    )
                                    mavMisDwnPOS.flush()
                                }
                            }

                            is DistanceSensor -> {

                                lastNonHeartbeatMs = System.currentTimeMillis()

                                val distanceSensorMsg = message as MavlinkMessage<DistanceSensor>
                                Log.i(
                                    "DistanceSensor",
                                    "Received DistanceSensor ${distanceSensorMsg.payload}"
                                )

                                if (distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_NONE ||
                                    distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_45 ||
                                    distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_315
                                ) {
                                    Log.i(
                                        "DistanceSensor",
                                        "Received Front ${distanceSensorMsg.payload.currentDistance()} cm"
                                    )
                                    droneViewModel.droneFrontDistance.postValue(distanceSensorMsg.payload.currentDistance() / 100)
                                } else if (distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_180 ||
                                    distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_135 ||
                                    distanceSensorMsg.payload.orientation()
                                        .entry() == MavSensorOrientation.MAV_SENSOR_ROTATION_YAW_225
                                ) {
                                    Log.i(
                                        "DistanceSensor",
                                        "Received Back ${distanceSensorMsg.payload.currentDistance()} cm"
                                    )
                                    droneViewModel.droneBackDistance.postValue(distanceSensorMsg.payload.currentDistance() / 100)
                                }
                            }
                        }

                        if (emitter.isDisposed) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { msg ->
                        Log.i("subscribeMavlinkMsgReader", "Message $msg")
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("subscribeMavlinkMsgReader", "Error: ${error.message}")
                    }
                )
        )
    }

    private fun subscribeGcsHeartbeat() {
        disposables.add(
            Observable.create<Boolean> { emitter ->

                while (!emitter.isDisposed) {
                    val heartbeatMsg = Heartbeat.builder()
                        .mavlinkVersion(3)
                        .systemStatus(MavState.MAV_STATE_ACTIVE)
                        .type(MavType.MAV_TYPE_GCS)
                        .build()

                    mavCon.send2(systemId, componentId, heartbeatMsg)
                    mavSndPOS.flush()

                    Log.i("GcsHeartbeat", "Message sent: $heartbeatMsg")

                    Thread.sleep(1000)
                }

            }
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { conState ->
                        Log.i("subscribeGcsHeartbeat", "conState $conState")
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("subscribeGcsHeartbeat", "Error: ${error.message}")
                    }
                )
        )
    }

    private fun subscribeConnectionState() {
        disposables.add(
            Observable.create<Boolean> { emitter ->

                var lastHeartbeat: Long = 0

                try {
                    while (!emitter.isDisposed) {

                        if (mavHrtbtPIS.available() > 0) {
                            val message = mavHrtbtCon.next()
                            lastHeartbeat = System.currentTimeMillis()
                            Log.i(
                                "subscribeConnectionState",
                                "Received $message  ${System.currentTimeMillis()}"
                            )
                        }

                        val telemetryAlive =
                            (System.currentTimeMillis() - lastNonHeartbeatMs) < 2500
                        droneViewModel.telemetryAliveLiveData.postValue(telemetryAlive)


                        emitter.onNext(System.currentTimeMillis() - lastHeartbeat < 5500)
                        Log.i(
                            "subscribeConnectionState",
                            "Loop ${System.currentTimeMillis() - lastHeartbeat}"
                        )
                        Thread.sleep(500)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { conState ->
                        Log.i("subscribeConnectionState", "Message $conState")

                        droneViewModel.conStateLiveData.postValue(conState)
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("subscribeConnectionState", "Error: ${error.message}")
                    }
                )
        )
    }


    fun downloadMission() {
        Log.i("downloadMission", "downloadMission CALL")

        disposables.add(
            // Subscribe to the Observable
            Single.create<ArrayList<MissionItemInt>> { emitter ->

                if (!droneViewModel.conStateLiveData.value!!) {
                    emitter.onSuccess(ArrayList<MissionItemInt>())
                    return@create
                }

                var missionItems = ArrayList<MissionItemInt>()
                var countItems = -1
                var lastSeq = -1
                var requestListNumOfRetries = 0
                var requestIntNumOfRetries = 0


                endlessLoop@ while (true) {
                    val msg = timeoutMav(mavMisDwnPIS, mavDownCon)

                    if (msg != null) {
                        Log.i("downloadMission", "Message received: ${msg.payload}")

                        when (msg.payload) {
                            is MissionCount -> {
                                val missionCountMsg = msg as MavlinkMessage<MissionCount>

                                if (missionCountMsg.payload.targetSystem() == systemId &&
                                    missionCountMsg.payload.targetComponent() == componentId
                                ) {
                                    countItems = missionCountMsg.payload.count()

                                    if (countItems == 0) {
                                        emitter.onSuccess(ArrayList<MissionItemInt>())
                                        break@endlessLoop
                                    } else
                                        missionItems = ArrayList<MissionItemInt>(countItems)
                                }

                            }

                            is MissionItemInt -> {
                                val missionItemMsg = msg as MavlinkMessage<MissionItemInt>

                                if (missionItemMsg.payload.targetSystem() == systemId &&
                                    missionItemMsg.payload.targetComponent() == componentId
                                ) {
                                    requestIntNumOfRetries = 0

                                    val seq = missionItemMsg.payload.seq()
                                    lastSeq = seq

                                    missionItems.add(seq, (msg.payload as MissionItemInt))
                                }
                            }
                        }
                    }



                    if (countItems == -1) {
                        if (requestListNumOfRetries == 5)
                            emitter.onError(IllegalThreadStateException("Exceeded num of retries on MissionRequestList"))

                        val missionRequestListMsg = MissionRequestList.builder()
                            .targetSystem(targetSystemId)
                            .targetComponent(targetComponentId)
                            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                            .build()

                        mavCon.send2(systemId, componentId, missionRequestListMsg)
                        mavSndPOS.flush()

                        Log.i("downloadMission", "Message sent: $missionRequestListMsg")

                        requestListNumOfRetries += 1
                    } else if (lastSeq == countItems - 1) {
                        val missionAckMsg = MissionAck.builder()
                            .targetSystem(targetSystemId)
                            .targetComponent(targetComponentId)
                            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                            .type(
                                EnumValue.create(
                                    MavMissionResult.MAV_MISSION_ACCEPTED,
                                    MavMissionResult.valueOf("MAV_MISSION_ACCEPTED")
                                )
                            )
                            .build()

                        mavCon.send2(systemId, componentId, missionAckMsg)
                        mavSndPOS.flush()

                        Log.i("downloadMission", "Message sent: $missionAckMsg")

                        emitter.onSuccess(missionItems)
                        break@endlessLoop
                    } else if (countItems > 0) {
                        if (requestIntNumOfRetries == 5)
                            emitter.onError(IllegalThreadStateException("Exceeded num of retries on MissionRequestInt"))

                        val missionRequestIntMsg = MissionRequestInt.builder()
                            .targetSystem(targetSystemId)
                            .targetComponent(targetComponentId)
                            .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                            .seq(missionItems.size)
                            .build()

                        mavCon.send2(systemId, componentId, missionRequestIntMsg)
                        mavSndPOS.flush()

                        Log.i("downloadMission", "Message sent: $missionRequestIntMsg")

                        requestIntNumOfRetries += 1
                    }
                }
            }
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { missionItems ->
                        Log.i("downloadMission", "downloadMissionResult $missionItems")
                        Toast.makeText(
                            activity!!.baseContext,
                            activity?.getString(R.string.download_mission_succeded),
                            Toast.LENGTH_LONG
                        ).show()

                        if (missionItems.size > 0) {
                            droneViewModel.missionItems.postValue(missionItems as ArrayList<MissionItemInt>)
                        }
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("downloadMission", "Error: ${error.message}")
                    }
                )
        )
    }


    fun uploadMission(missionItems: ArrayList<MissionItemInt>) {
        Log.i("uploadMission", "uploadMission CALL")

        disposables.add(
            // Subscribe to the Observable
            Single.create<MavlinkMessage<MissionAck>> { emitter ->

                var missionAckRetries = 0
                var missionRequestMsg: MavlinkMessage<MissionRequest>? = null

                val missionCountMsg = MissionCount.builder()
                    .targetSystem(targetSystemId)
                    .targetComponent(targetComponentId)
                    .count(missionItems.size)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .build()

                mavCon.send2(systemId, componentId, missionCountMsg)
                mavSndPOS.flush()

                Log.i("uploadMission", "Message sent: $missionCountMsg")
                Log.i("uploadMission", "Mission Items Size: ${missionItems.size}")


                var lastSeq = -1
                var seq = -1
                endlessLoop@ while (true) {
                    val msg = timeoutMav(mavMisUpPIS, mavUpCon)
                    Log.i("uploadMission", "Message received: $msg")

                    if (msg != null) {
                        when (msg.payload) {

                            is MissionRequest -> {
                                missionRequestMsg = msg as MavlinkMessage<MissionRequest>

                                seq = missionRequestMsg.payload?.seq()!!

                                lastSeq = max(lastSeq, seq)

                                mavCon.send2(systemId, componentId, missionItems[seq])
                                mavSndPOS.flush()

                                Log.i("uploadMission", "Message sent ${missionItems[seq]}")

                                if (seq == missionItems.size - 1) {
                                    Log.i("uploadMission", "Last Item located $seq")
                                }
                            }

                            is MissionAck -> {
                                val missionAckMsg = msg as MavlinkMessage<MissionAck>

                                if (seq == missionItems.size - 1) {

                                    missionAckRetries += 1

                                    if (missionAckMsg.payload.type().value() == 0 &&
                                        missionAckMsg.payload.type()
                                            .entry() == MavMissionResult.MAV_MISSION_ACCEPTED
                                    ) {
                                        Log.i(
                                            "uploadMission",
                                            "Message missionAckMsg received $missionAckMsg"
                                        )

                                        emitter.onSuccess(missionAckMsg)
                                        break@endlessLoop
                                    }

                                    if (missionAckRetries == 5)
                                        break@endlessLoop
                                }
                            }
                        }
                    }
                }


                val msg = timeoutMav(mavMisUpPIS, mavUpCon)
                Log.i("uploadMission", "Message received: $msg")

                if (msg != null && msg.payload is MissionAck) {

                }

            }
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { missionUploadResult ->
                        Log.i("uploadMission", "missionUploadResult $missionUploadResult")
                        Toast.makeText(
                            activity?.baseContext,
                            activity?.getString(R.string.mission_upload_success),
                            Toast.LENGTH_LONG
                        ).show()
                        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.Idle)
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("uploadMission", "Error: ${error.message}")
                        Toast.makeText(
                            activity?.baseContext,
                            activity?.getString(R.string.mission_upload_failure),
                            Toast.LENGTH_LONG
                        ).show()
                        activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SetFlightParams)
                    }
                )
        )
    }


    fun clearMission() {

        disposables.add(
            Single.create<Boolean> { emitter ->

                var missionAckMsg: MavlinkMessage<MissionAck>? = null

                for (i in 1..5) {
                    val missionClearAll = MissionClearAll.builder()
                        .targetSystem(targetSystemId)
                        .targetComponent(targetComponentId)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .build()

                    mavCon.send2(systemId, componentId, missionClearAll)
                    mavSndPOS.flush()

                    Log.i("clearMission", "Message sent $missionClearAll")

                    missionAckMsg = timeoutMav(mavMisUpPIS, mavUpCon) as MavlinkMessage<MissionAck>?

                    if (missionAckMsg != null) {
                        emitter.onSuccess(
                            missionAckMsg.payload.type().value() == 0
                                    && missionAckMsg.payload.type()
                                .entry() == MavMissionResult.MAV_MISSION_ACCEPTED
                        )
                        break
                    }
                }
            }
                .subscribeOn(Schedulers.io()) // Specify the scheduler for the operation
                .observeOn(AndroidSchedulers.mainThread()) // Specify the scheduler for the result handling
                .subscribe(
                    { missionClearResult ->
                        Log.i("missionClear", "missionClearResult $missionClearResult")
                    },
                    { error ->
                        // Handle any errors that may occur during the operation
                        Log.e("missionClear", "Error: ${error.message}")
                    }
                )
        )
    }


    fun setupMission(
        waypoints: ArrayList<LatLng>,
        currentPos: Location,
        alt: Float,
        sprayerIntensity: Int,
        context: Context
    ): ArrayList<MissionItemInt> {
        val min = 1000.0F
        val max = 2000.0F
        val sprayerIntensityPWM = ((max - min) * (sprayerIntensity / 100.0F)) + min

        val missionItems: ArrayList<MissionItemInt> = ArrayList()
        var seq = 0


        missionItems.add(
            MissionItemInt.builder()
                .seq(seq++)
                .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT).command(MavCmd.MAV_CMD_NAV_TAKEOFF)
                .current(1).autocontinue(1)
                .param1(0.0f).param2(0.0f).param3(0.0f).param4(Float.NaN)
                .x(0).y(0).z(alt)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
        )

        missionItems.add(
            MissionItemInt.builder()
                .seq(seq++)
                .frame(MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT).command(MavCmd.MAV_CMD_NAV_TAKEOFF)
                .current(0).autocontinue(1)
                .param1(0.0f).param2(0.0f).param3(0.0f).param4(Float.NaN)
                .x((currentPos.latitude * 10.0F.pow(7)).toInt())
                .y((currentPos.longitude * 10.0F.pow(7)).toInt())
                .z(alt)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
        )

        for (i in waypoints.indices) {
            // Before the first waypoint is added
            if (i == 0) {
                // Set user preferable speed in m/s
                missionItems.add(
                    MissionItemInt.builder()
                        .seq(seq++)
                        .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                        .command(MavCmd.MAV_CMD_DO_CHANGE_SPEED)
                        .current(0).autocontinue(1)
                        .param1(1.0f).param2(activityViewModel.flightSpeed.value!!.toFloat())
                        .param3(0.0f).param4(0.0f)
                        .x(0).y(0).z(0.0F)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
                )
            }

            //Set Steady Heading for each mission waypoint
            missionItems.add(
                MissionItemInt.builder()
                    .seq(seq++)
                    .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                    .command(MavCmd.MAV_CMD_CONDITION_YAW)
                    .current(0).autocontinue(1)
                    .param1(90.0F - activityViewModel.angleProgress.value!!.toFloat()).param2(0.0f)
                    .param3(0.0f).param4(0.0f)
                    .x(0).y(0).z(0.0F)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
            )


            // Add Relative Waypoint to Mission
            missionItems.add(
                MissionItemInt.builder()
                    .seq(seq++)
                    .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                    .command(MavCmd.MAV_CMD_NAV_WAYPOINT)
                    .current(0).autocontinue(1)
                    .param1(0.0f).param2(0.0f).param3(0.0f).param4(Float.NaN)
                    .x((waypoints[i].latitude * 10.0F.pow(7)).toInt())
                    .y((waypoints[i].longitude * 10.0F.pow(7)).toInt())
                    .z(alt)
                    .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                    .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
            )

            // After First Mission waypoint added
            if (i == 0) {
                // Set Sprayer Enable ON
                missionItems.add(
                    MissionItemInt.builder()
                        .seq(seq++)
                        .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                        .command(MavCmd.MAV_CMD_DO_SPRAYER)
                        .current(0).autocontinue(1)
                        .param1(1.0f).param2(0.0f).param3(0.0f).param4(0.0f)
                        .x(0).y(0).z(0.0f)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
                )

                // Set Servo ON
                missionItems.add(
                    MissionItemInt.builder()
                        .seq(seq++)
                        .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                        .command(MavCmd.MAV_CMD_DO_SET_SERVO)
                        .current(0).autocontinue(1)
                        .param1(5.0f).param2(sprayerIntensityPWM).param3(0.0f).param4(0.0f)
                        .x(0).y(0).z(0.0f)
                        .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                        .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
                )
            }
        }

        // Set Sprayer Enable OFF
        missionItems.add(
            MissionItemInt.builder()
                .seq(seq++)
                .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT).command(MavCmd.MAV_CMD_DO_SPRAYER)
                .current(0).autocontinue(1)
                .param1(0.0f).param2(0.0f).param3(0.0f).param4(0.0f)
                .x(0).y(0).z(0.0f)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
        )


        // Set Servo OFF
        missionItems.add(
            MissionItemInt.builder()
                .seq(seq++)
                .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT).command(MavCmd.MAV_CMD_DO_SET_SERVO)
                .current(0).autocontinue(1)
                .param1(5.0f).param2(1000.0f).param3(0.0f).param4(0.0f)
                .x(0).y(0).z(0.0f)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
        )

        // RTL - Return To Land
        missionItems.add(
            MissionItemInt.builder()
                .seq(seq)
                .frame(MavFrame.MAV_FRAME_GLOBAL_TERRAIN_ALT)
                .command(MavCmd.MAV_CMD_NAV_RETURN_TO_LAUNCH)
                .current(0).autocontinue(1)
                .param1(0.0f).param2(0.0f).param3(0.0f).param4(0.0f)
                .x(0).y(0).z(0.0f)
                .missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .targetSystem(targetSystemId).targetComponent(targetComponentId).build()
        )


        for (item in missionItems) {
            Log.i(
                "setupMission", "seq: ${item.seq()}  " +
                        "frame: ${item.frame()}  " +
                        "command: ${item.command()}  " +
                        "current: ${item.current()}  " +
                        "auto continue: ${item.autocontinue()}  " +
                        "param1: ${item.param1()}  " +
                        "param2: ${item.param2()}  " +
                        "param3: ${item.param3()}  " +
                        "param4: ${item.param4()}  " +
                        "x: ${item.x()}  " +
                        "y: ${item.y()}  " +
                        "z: ${item.z()}  " +
                        "missionType: ${item.missionType()}  "
            )
        }

        return missionItems
    }


//
//    private fun subscribeFlightMode()
//    {
//        disposables.add(
//            drone!!.telemetry.flightMode.distinctUntilChanged()
//                .onErrorReturnItem(Telemetry.FlightMode.UNKNOWN)
//                .subscribe(
//                    {flightMode : Telemetry.FlightMode ->
//                        //Log.i(Log.DEBUG.toString(), "flight mode: $flightMode")
//                    },
//                    {error ->
//                        Log.i(Log.DEBUG.toString(), "flight mode: " + error.localizedMessage)}
//                )
//        )
//    }
//
//
//    private fun subscribeArmState()
//    {
//        disposables.add(
//            drone!!.telemetry.armed.distinctUntilChanged()
//                .onErrorReturnItem(false)
//                .subscribe(
//                    { armed: Boolean ->
//                        droneViewModel.armedState.postValue(armed)
//                        Log.i(Log.DEBUG.toString(), "armed: $armed")
//                    },
//                    {error ->
//                        Log.i(Log.DEBUG.toString(), "armed: " + error.localizedMessage)}
//                )
//        )
//    }
//
//
//    private fun subscribeFrontSonar()
//    {
//
//
//        disposables.add(
//            drone!!.telemetry.distanceSensor.distinctUntilChanged()
//                .onErrorReturnItem(Telemetry.DistanceSensor(20.0F, 10000.0F, 30.0F))
//                .subscribe(
//                    {
//                        droneViewModel.droneFrontDistance.postValue(it.currentDistanceM)
//                    },
//                    {error ->
//                        Log.i("subscribeFrontSonar", "error: " + error.localizedMessage)}
//                )
//        )
//
//
//    }
//
//
//    private fun subscribeDroneLocation()
//    {
//        disposables.add(
//            drone!!.telemetry.position.distinctUntilChanged()
//            .onErrorReturnItem(Telemetry.Position(0.0, 0.0, 0F, 0F))
//            .subscribe (
//                { position: Telemetry.Position ->
//                    val loc = Location("")
//                    loc.longitude = position.longitudeDeg
//                    loc.latitude = position.latitudeDeg
//                    loc.altitude = position.relativeAltitudeM.toDouble()
//
//                    droneViewModel.droneLocationLiveData.postValue(loc)
//                },
//                {error ->
//                    Log.i(Log.DEBUG.toString(), "position: " + error.localizedMessage)}
//            )
//        )
//    }


//    private fun subscribeHeading()
//    {
//        disposables.add(
//            drone?.telemetry?.heading?.distinctUntilChanged()
//                ?.onErrorReturn { null }
//                ?.subscribe(
//                    {heading: Telemetry.Heading ->
//                        GlobalScope.launch {
//                            withContext(Dispatchers.Main){
//                                droneViewModel.droneHeading.postValue(heading.headingDeg)
//                            }
//                        }
//                    },
//                    {error ->
//                        Log.e(Log.ERROR.toString(), "heading: " + error.localizedMessage)
//                    }
//                )
//        )
//    }
//
//
//    private fun subscribeBatteryLevel()
//    {
//        disposables.add(
//            drone?.telemetry?.battery?.distinctUntilChanged()
//                ?.onErrorReturn { null }
//                ?.subscribe(
//                    {battery: Telemetry.Battery ->
//                        if(battery.id == 0)
//                        {
//                            GlobalScope.launch {
//                                withContext(Dispatchers.Main){
//                                    if(battery.remainingPercent != -1.0F)
//                                        droneViewModel.droneBatteryPercentage.postValue(battery.remainingPercent)
//                                    else
//                                        droneViewModel.droneBatteryPercentage.postValue(0.0F)
//
//                                    if(battery.remainingPercent != -1.0F)
//                                        droneViewModel.droneBatteryVoltage.postValue(battery.voltageV)
//                                    else
//                                        droneViewModel.droneBatteryVoltage.postValue(0.0F)
//                                }
//                            }
//                        }
//
//                        if(battery.id == 1)
//                        {
//                            if(battery.voltageV != -1.0F)
//                                droneViewModel.liquidLevel.postValue(battery.voltageV)
//                            else
//                                droneViewModel.liquidLevel.postValue(0.0F)
//                        }
//                    },
//                    {error ->
//                        Log.e(Log.ERROR.toString(), "battery level: " + error.localizedMessage)
//                    }
//                )
//        )
//    }


//    private fun subscribeDroneRCStrength()
//    {
//        disposables.add(
//            drone?.telemetry?.rcStatus?.distinctUntilChanged()?.
//                subscribe { telemetryStatus ->
//                    droneViewModel.rcStatus.postValue(telemetryStatus)
//            }
//        )
//    }


    private fun timeoutMav(pis: PipedInputStream, conn: MavlinkConnection): MavlinkMessage<*>? {
        var mavMsg: MavlinkMessage<*>? = null

        val timeout = System.currentTimeMillis() + 1500
        while (pis.available() <= 0 && System.currentTimeMillis() <= timeout)
            Thread.sleep(20)

        if (pis.available() > 0) {
            mavMsg = conn.next() as MavlinkMessage<*>
            Log.i("timeoutMav", "mavMsg: $mavMsg")
        } else {
            Log.i("timeoutMav", "missionCountMsg: TIMEOUT")
        }

        return mavMsg
    }
}

