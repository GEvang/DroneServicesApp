package com.example.droneservicesapp.data.rtk

object RtkValidator {

    fun isValidIp(value: String): Boolean {
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

    fun isValidBaseConfig(config: RtkConfig): Boolean {
        return isValidIp(config.ip) &&
            isValidPort(config.port) &&
            isValidUsername(config.username) &&
            isValidPassword(config.password)
    }

    fun isValidConfig(config: RtkConfig): Boolean {
        return isValidBaseConfig(config) && isValidMountpoint(config.mountpoint)
    }
}
