package com.example.droneservicesapp.ui.home.binders

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.example.droneservicesapp.Application
import com.example.droneservicesapp.R
import com.example.droneservicesapp.data.mavlink.MissionBuilder
import com.example.droneservicesapp.mavserver.DroneViewModel
import com.example.droneservicesapp.ui.home.model.MissionParamsUiState
import com.example.droneservicesapp.ui.shell.model.MainActivityViewModel

class MissionParamsController(
    private val context: Context,
    private val rootView: View,
    private val lifecycleOwner: LifecycleOwner,
    private val activityViewModel: MainActivityViewModel,
    private val droneViewModel: DroneViewModel,
) {
    private var isBound = false
    private var missionParamsUiState = MissionParamsUiState(
        angle = 1,
        lineDistance = 1,
        altitude = 2,
        sprayerIntensity = 0,
        flightSpeed = 1,
        estimatedFlightMinutes = 1
    )

    private val minSpeed = 1
    private val maxSpeed = 5

    private val missionParamsSideView: LinearLayoutCompat by lazy {
        rootView.findViewById<LinearLayoutCompat>(R.id.mission_params_side_view)
    }

    fun show() {
        if (isBound) return
        getWindowPreferences()
        missionParamsSideView.visibility = View.VISIBLE
        bindSeekbars()
        bindFlightTimeAndSpeed()
        bindSpeedButtons()
        bindActionButtons()
        bindIconButtons()  // Add this line
        isBound = true
    }

    private fun bindFlightTimeAndSpeed() {
        val flightTimeText = rootView.findViewById<TextView>(R.id.flight_time)
        val flightSpeedView = rootView.findViewById<TextView>(R.id.flight_speed)

        missionParamsUiState = currentMissionParamsUiState()
        renderFlightSummary(flightSpeedView, flightTimeText, missionParamsUiState)

        activityViewModel.flightSpeed.observe(lifecycleOwner) { flightSpeed ->
            missionParamsUiState = missionParamsUiState.copy(flightSpeed = flightSpeed.toInt())
            renderFlightSummary(flightSpeedView, flightTimeText, missionParamsUiState)
        }

        activityViewModel.estimatedFlightMinutes.observe(lifecycleOwner) { minutes ->
            missionParamsUiState = missionParamsUiState.copy(estimatedFlightMinutes = minutes)
            renderFlightSummary(flightSpeedView, flightTimeText, missionParamsUiState)
        }
    }

    private fun renderFlightSummary(
        flightSpeedView: TextView,
        flightTimeText: TextView,
        state: MissionParamsUiState
    ) {
        flightSpeedView.text = state.flightSpeed.toString()
        flightTimeText.text = state.estimatedFlightMinutes.toString()
    }

    private fun currentMissionParamsUiState(): MissionParamsUiState {
        return MissionParamsUiState(
            angle = activityViewModel.angleProgress.value?.toInt() ?: 1,
            lineDistance = activityViewModel.lineDistanceProgress.value?.toInt() ?: 1,
            altitude = activityViewModel.flightAltProgress.value?.toInt() ?: 2,
            sprayerIntensity = activityViewModel.sprayerProgress.value?.toInt() ?: 0,
            flightSpeed = activityViewModel.flightSpeed.value?.toInt() ?: 1,
            estimatedFlightMinutes = activityViewModel.estimatedFlightMinutes.value ?: 1
        )
    }

    private fun bindSeekbars() {
        val angleSeekBarValue = rootView.findViewById<EditText>(R.id.line_angle_value)
        val angleSeekBar = rootView.findViewById<SeekBar>(R.id.line_angle_seekbar)
        initSeekbar(angleSeekBar, angleSeekBarValue, activityViewModel.angleProgress)

        val lineDistanceSeekBarValue = rootView.findViewById<EditText>(R.id.line_distance_value)
        val lineDistanceSeekBar = rootView.findViewById<SeekBar>(R.id.line_distance_seekbar)
        initSeekbar(lineDistanceSeekBar, lineDistanceSeekBarValue, activityViewModel.lineDistanceProgress)

        val altitudeSeekBarValue = rootView.findViewById<EditText>(R.id.altitude_value)
        val altitudeSeekBar = rootView.findViewById<SeekBar>(R.id.altitude_seekbar)
        initSeekbar(altitudeSeekBar, altitudeSeekBarValue, activityViewModel.flightAltProgress)

        val sprayerSeekbarValue = rootView.findViewById<EditText>(R.id.sprayer_seekbar_value)
        val sprayerSeekBar = rootView.findViewById<SeekBar>(R.id.sprayer_seekbar)
        initSeekbar(sprayerSeekBar, sprayerSeekbarValue, activityViewModel.sprayerProgress)
    }

    private fun bindSpeedButtons() {
        val buttonMinus = rootView.findViewById<ImageButton>(R.id.minus_button)
        buttonMinus.setOnClickListener {
            val cur = activityViewModel.flightSpeed.value?.toInt() ?: 1
            if (cur > minSpeed) {
                activityViewModel.flightSpeed.postValue((cur - 1).toDouble())
            }
        }

        val buttonPlus = rootView.findViewById<ImageButton>(R.id.plus_button)
        buttonPlus.setOnClickListener {
            val cur = activityViewModel.flightSpeed.value?.toInt() ?: 1
            if (cur < maxSpeed) {
                activityViewModel.flightSpeed.postValue((cur + 1).toDouble())
            }
        }
    }


    private fun bindActionButtons() {
        val buttonUploadMission = rootView.findViewById<Button>(R.id.uploadMission)
        buttonUploadMission.setOnClickListener {
            // Read values into locals using safe access
            val connected = droneViewModel.conStateLiveData.value == true
            val droneLoc = droneViewModel.droneLocationLiveData.value
            val path = activityViewModel.surveyPath.value
            val alt = activityViewModel.flightAltProgress.value
            val sprayer = activityViewModel.sprayerProgress.value
            val speed = activityViewModel.flightSpeed.value
            val angle = activityViewModel.angleProgress.value

            // Validation in required order
            if (!connected) {
                Toast.makeText(context, context.getString(R.string.no_conn_msg), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (droneLoc == null) {
                Toast.makeText(context, "Drone GPS not available yet", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (path == null || path.isEmpty()) {
                Toast.makeText(
                    context,
                    "No survey path available. Please generate survey first.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (alt == null || sprayer == null || speed == null || angle == null) {
                Toast.makeText(context, "Missing mission parameters", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val missionItems = MissionBuilder.buildSurveyMission(
                waypoints = ArrayList(path),
                currentPos = droneLoc,
                alt = alt.toFloat(),
                sprayerIntensity = sprayer.toInt(),
                flightSpeed = speed.toFloat(),
                angleProgress = angle.toFloat(),
                targetSystemId = droneViewModel.getTargetSystemId(),
                targetComponentId = droneViewModel.getTargetComponentId()
            )

            droneViewModel.uploadMissionNew(missionItems, activityViewModel)
            setWindowPreferences()
        }

        val buttonExit = rootView.findViewById<Button>(R.id.exit)
        buttonExit.setOnClickListener {
            setWindowPreferences()
            activityViewModel.sendAction(MainActivityViewModel.MapAction.ClearKeepDrawing)
        }

        val buttonSaveMission = rootView.findViewById<Button>(R.id.save_mission)
        buttonSaveMission.setOnClickListener {
            activityViewModel.mapState.postValue(MainActivityViewModel.MapState.SaveMissionToFile)
        }
    }

    private fun initSeekbar(
        seekbar: SeekBar,
        seekbarValue: EditText,
        mutable: MutableLiveData<Double>
    ) {
        val initial = mutable.value?.toInt() ?: 0
        seekbar.progress = initial
        seekbarValue.setText("$initial")

        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                seekbarValue.setText("$progress")
                mutable.postValue(progress.toDouble())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        seekbarValue.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                seekbarValue.setSelection(seekbarValue.length())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val progress = s.toString().toInt()
                    seekbar.setProgress(progress, true)
                } catch (_: NumberFormatException) {
                    Log.d("SeekBar Mission Params", "Invalid number")
                }
            }
        })
    }

    private fun getWindowPreferences() {
        getSharedPreferenceById(R.string.survey_angle_pref, activityViewModel.angleProgress, "0")
        getSharedPreferenceById(R.string.survey_line_distance_pref, activityViewModel.lineDistanceProgress, "1")
        getSharedPreferenceById(R.string.survey_altitude_pref, activityViewModel.flightAltProgress, "2")
        getSharedPreferenceById(R.string.sprayer_intensity, activityViewModel.sprayerProgress, "0")
        getSharedPreferenceById(R.string.flight_speed_pref, activityViewModel.flightSpeed, "1")
    }

    private fun setWindowPreferences() {
        setSharedPreferenceById(R.id.survey_angle_pref, activityViewModel.angleProgress.value?.toInt() ?: 0)
        setSharedPreferenceById(R.id.survey_line_dist_pos_pref, activityViewModel.lineDistanceProgress.value?.toInt() ?: 1)
        setSharedPreferenceById(R.id.mission_alt_pref, activityViewModel.flightAltProgress.value?.toInt() ?: 2)
        setSharedPreferenceById(R.id.sprayer_intensity_pref, activityViewModel.sprayerProgress.value?.toInt() ?: 0)
        setSharedPreferenceById(R.id.flight_speed_pref, activityViewModel.flightSpeed.value?.toInt() ?: 1)
    }

    private fun getSharedPreferenceById(
        stringResourceId: Int,
        mutable: MutableLiveData<Double>,
        defValue: String
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        mutable.postValue(prefs.getString(context.getString(stringResourceId), defValue)?.toDouble())
    }

    private fun setSharedPreferenceById(id: Int, value: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val editor = prefs.edit()
        editor.putString(Application.getInstance().applicationContext.getString(id), value.toString())
        editor.apply()
    }

    private fun bindIconButtons() {
        val angleMinusBtn = missionParamsSideView.findViewById<View>(R.id.btn_angle_minus)
        val anglePlusBtn = missionParamsSideView.findViewById<View>(R.id.btn_angle_plus)
        val distanceMinusBtn = missionParamsSideView.findViewById<View>(R.id.btn_distance_minus)
        val distancePlusBtn = missionParamsSideView.findViewById<View>(R.id.btn_distance_plus)
        val altMinusBtn = missionParamsSideView.findViewById<View>(R.id.btn_alt_minus)
        val altPlusBtn = missionParamsSideView.findViewById<View>(R.id.btn_alt_plus)
        val sprayerMinusBtn = missionParamsSideView.findViewById<View>(R.id.btn_sprayer_minus)
        val sprayerPlusBtn = missionParamsSideView.findViewById<View>(R.id.btn_sprayer_plus)

        val angleSeekbar = missionParamsSideView.findViewById<SeekBar>(R.id.line_angle_seekbar)
        val distanceSeekbar = missionParamsSideView.findViewById<SeekBar>(R.id.line_distance_seekbar)
        val altSeekbar = missionParamsSideView.findViewById<SeekBar>(R.id.altitude_seekbar)
        val sprayerSeekbar = missionParamsSideView.findViewById<SeekBar>(R.id.sprayer_seekbar)

        // Mission Angle buttons
        angleMinusBtn.setOnClickListener {
            val newProgress = (angleSeekbar.progress - 1).coerceAtLeast(angleSeekbar.min)
            angleSeekbar.progress = newProgress
        }
        anglePlusBtn.setOnClickListener {
            val newProgress = (angleSeekbar.progress + 1).coerceAtMost(angleSeekbar.max)
            angleSeekbar.progress = newProgress
        }

        // Line Distance buttons
        distanceMinusBtn.setOnClickListener {
            val newProgress = (distanceSeekbar.progress - 1).coerceAtLeast(distanceSeekbar.min)
            distanceSeekbar.progress = newProgress
        }
        distancePlusBtn.setOnClickListener {
            val newProgress = (distanceSeekbar.progress + 1).coerceAtMost(distanceSeekbar.max)
            distanceSeekbar.progress = newProgress
        }

        // Altitude buttons
        altMinusBtn.setOnClickListener {
            val newProgress = (altSeekbar.progress - 1).coerceAtLeast(altSeekbar.min)
            altSeekbar.progress = newProgress
        }
        altPlusBtn.setOnClickListener {
            val newProgress = (altSeekbar.progress + 1).coerceAtMost(altSeekbar.max)
            altSeekbar.progress = newProgress
        }

        // Sprayer Intensity buttons
        sprayerMinusBtn.setOnClickListener {
            val newProgress = (sprayerSeekbar.progress - 1).coerceAtLeast(sprayerSeekbar.min)
            sprayerSeekbar.progress = newProgress
        }
        sprayerPlusBtn.setOnClickListener {
            val newProgress = (sprayerSeekbar.progress + 1).coerceAtMost(sprayerSeekbar.max)
            sprayerSeekbar.progress = newProgress
        }
    }
}
