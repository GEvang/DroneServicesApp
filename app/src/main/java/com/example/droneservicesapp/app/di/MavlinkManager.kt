package com.example.droneservicesapp.app.di

import android.util.Log
import com.example.droneservicesapp.data.mavlink.MavlinkConfig
import com.example.droneservicesapp.data.mavlink.MavlinkRepository

object MavlinkManager {

    private const val TAG = "MavlinkManager"

    val repository: MavlinkRepository = MavlinkRepository()

    fun start(config: MavlinkConfig) {
        Log.i(TAG, "Starting MavlinkManager with config: $config")
        repository.start(config)
    }

    fun stop() {
        Log.i(TAG, "Stopping MavlinkManager")
        repository.stop()
    }

    fun restart(config: MavlinkConfig) {
        Log.i(TAG, "Restarting MavlinkManager with config: $config")
        repository.restart(config)
    }
}