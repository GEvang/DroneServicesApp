package com.example.droneservicesapp.data.transport

import java.io.InputStream
import java.io.OutputStream

interface MavTransport {
    /** Stream that MAVLink reads from */
    val input: InputStream

    /** Stream that MAVLink writes to */
    val output: OutputStream

    /** Start the underlying transport (bind socket / connect, etc) */
    fun start()

    /** Stop transport and release resources */
    fun stop()
}
