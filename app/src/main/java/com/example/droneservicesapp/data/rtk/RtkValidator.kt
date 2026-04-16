package com.example.droneservicesapp.data.rtk

object RtkValidator {

    fun isValidHost(value: String): Boolean {
        return value.trim().isNotEmpty()
    }

    fun isValidPort(value: Int): Boolean {
        return value in 1..65535
    }

    fun isValidMountpoint(value: String): Boolean {
        return value.trim().isNotEmpty()
    }

    fun isValidUsername(value: String): Boolean {
        return value.trim().isNotEmpty()
    }

    fun isValidPassword(value: String): Boolean {
        return value.isNotEmpty()
    }

    fun isValidConfig(config: RtkConfig): Boolean {
        return isValidHost(config.host) &&
            isValidPort(config.port) &&
            isValidMountpoint(config.mountpoint) &&
            isValidUsername(config.username) &&
            isValidPassword(config.password)
    }
}
