package com.example.droneservicesapp.transport

import android.util.Log
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class UdpTransport(
    private val listenPort: Int
) : MavTransport {

    // MAVLink will read from this
    private val rcvPIS = PipedInputStream()
    private val rcvPOS = PipedOutputStream(rcvPIS)

    // MAVLink will write to this
    private val sndPIS = PipedInputStream()
    private val sndPOS = PipedOutputStream(sndPIS)

    override val input = rcvPIS
    override val output = sndPOS

    private var socket: DatagramSocket? = null
    private val running = AtomicBoolean(false)

    private var remoteIP: InetAddress? = null
    private var remotePort: Int = -1

    override fun start() {
        if (running.getAndSet(true)) return

        try {
            socket = DatagramSocket(listenPort)
            socket?.soTimeout = 200  // 200ms timeout to periodically wake up and flush
            Thread({ runLoop() }, "UdpTransport-$listenPort").apply { isDaemon = true }.start()
            Log.i("UdpTransport", "Started UDP listen on $listenPort")
        } catch (e: Exception) {
            running.set(false)
            Log.e("UdpTransport", "Failed to start: ${e.message}", e)
            throw e
        }
    }

    override fun stop() {
        running.set(false)
        try {
            socket?.close() // this will break receive() with SocketException
        } catch (_: Exception) {
        }
        socket = null

        try {
            rcvPOS.close()
        } catch (_: Exception) {
        }
        try {
            sndPIS.close()
        } catch (_: Exception) {
        }
        try {
            rcvPIS.close()
        } catch (_: Exception) {
        }
        try {
            sndPOS.close()
        } catch (_: Exception) {
        }


        Log.i("UdpTransport", "Stopped UDP transport")
    }

    private fun runLoop() {
        val udpSocket = socket ?: return
        val receiveData = ByteArray(4096)
        val outBuffer = ByteArray(4096)

        while (running.get()) {
            try {
                // 1) Receive UDP -> pipe to MAVLink input
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                udpSocket.receive(receivePacket)

                remoteIP = receivePacket.address
                remotePort = receivePacket.port

                rcvPOS.write(receivePacket.data, 0, receivePacket.length)
                rcvPOS.flush()

                // 2) Pipe from MAVLink output -> send back to last sender
                if (sndPIS.available() > 0 && remoteIP != null && remotePort > 0) {
                    val bytesRead = sndPIS.read(outBuffer)
                    val outputPacket = DatagramPacket(outBuffer, bytesRead, remoteIP, remotePort)
                    udpSocket.send(outputPacket)
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Timeout occurred - loop continues, allowing outgoing bytes to flush
                // 2) Pipe from MAVLink output -> send back to last sender (even without new input)
                if (sndPIS.available() > 0 && remoteIP != null && remotePort > 0) {
                    val bytesRead = sndPIS.read(outBuffer)
                    val outputPacket = DatagramPacket(outBuffer, bytesRead, remoteIP, remotePort)
                    udpSocket.send(outputPacket)
                    Log.i("UdpTransport", "SENT ${bytesRead} bytes -> ${remoteIP?.hostAddress}:${remotePort}")
                }
            } catch (e: SocketException) {
                // Happens on stop() because close() breaks receive()
                break
            } catch (e: Exception) {
                Log.e("UdpTransport", "UDP loop error: ${e.message}", e)
            }
        }
    }
}
