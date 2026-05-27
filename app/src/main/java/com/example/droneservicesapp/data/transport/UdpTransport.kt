package com.example.droneservicesapp.data.transport

import android.util.Log
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class UdpTransport(
    private val listenPort: Int,
    targetHost: String? = null
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
    private val configuredTargetIP: InetAddress? = targetHost
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { InetAddress.getByName(it) }
    private var lastLoggedRemoteEndpoint: String? = null

    override fun start() {
        Log.i("UdpTransport", "connect requested on UDP transport listenPort=$listenPort running=${running.get()}")
        if (running.getAndSet(true)) {
            Log.i("UdpTransport", "start skipped: UDP transport already running")
            return
        }

        try {
            Log.i("UdpTransport", "creating/binding UDP socket listenPort=$listenPort")
            socket = DatagramSocket(listenPort)
            socket?.soTimeout = 200  // 200ms timeout to periodically wake up and flush
            Log.i(
                "UdpTransport",
                "UDP socket created/bound local=${socket?.localAddress?.hostAddress}:${socket?.localPort} configuredTarget=${configuredTargetIP?.hostAddress ?: "<auto>"}"
            )
            Thread({ runLoop() }, "UdpTransport-$listenPort").apply { isDaemon = true }.start()
            Log.i("UdpTransport", "Started UDP listen on $listenPort")
        } catch (e: Exception) {
            running.set(false)
            Log.e("UdpTransport", "Failed to start: ${e.message}", e)
            throw e
        }
    }

    override fun stop() {
        Log.i("UdpTransport", "stop requested running=${running.get()} remote=${remoteIP?.hostAddress}:${remotePort}")
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
                logRemoteEndpointIfChanged(receivePacket.address, receivePacket.port)

                rcvPOS.write(receivePacket.data, 0, receivePacket.length)
                rcvPOS.flush()

                // 2) Pipe from MAVLink output -> send back to last sender
                if (sndPIS.available() > 0 && remoteIP != null && remotePort > 0) {
                    val bytesRead = sndPIS.read(outBuffer)
                    if (bytesRead <= 0) {
                        // Stream closed or no data, skip sending
                    } else {
                        sendToRemoteCandidates(udpSocket, outBuffer, bytesRead)
                    }
                }
            } catch (e: SocketTimeoutException) {
                // Timeout occurred - loop continues, allowing outgoing bytes to flush
                // 2) Pipe from MAVLink output -> send back to last sender (even without new input)
                if (sndPIS.available() > 0 && remoteIP != null && remotePort > 0) {
                    val bytesRead = sndPIS.read(outBuffer)
                    sendToRemoteCandidates(udpSocket, outBuffer, bytesRead)
                }
            } catch (e: SocketException) {
                // Happens on stop() because close() breaks receive()
                break
            } catch (e: Exception) {
                Log.e("UdpTransport", "UDP loop error: ${e.message}", e)
            }
        }
    }

    private fun sendToRemoteCandidates(
        udpSocket: DatagramSocket,
        outBuffer: ByteArray,
        bytesRead: Int
    ) {
        val remoteAddress = remoteIP ?: return
        logMavlinkPacketVersion(outBuffer, bytesRead)
        val candidateEndpoints = buildList {
            configuredTargetIP?.let { targetAddress ->
                add(targetAddress to listenPort)
            } ?: run {
                add(remoteAddress to remotePort)
                add(remoteAddress to listenPort)
            }
        }.filter { (_, port) -> port > 0 }.distinct()

        candidateEndpoints.forEach { (address, port) ->
            val outputPacket = DatagramPacket(outBuffer, bytesRead, address, port)
            udpSocket.send(outputPacket)
            Log.i("UdpTransport", "SENT $bytesRead bytes -> ${address.hostAddress}:$port")
        }
    }

    private fun logMavlinkPacketVersion(outBuffer: ByteArray, bytesRead: Int) {
        if (bytesRead <= 0) return
        val packets = mutableListOf<String>()
        var index = 0
        while (index < bytesRead) {
            val magic = outBuffer[index].toInt() and 0xFF
            when (magic) {
                0xFE -> {
                    packets.add("MAVLink1@$index")
                    index += mavlink1PacketLength(outBuffer, index, bytesRead) ?: 1
                }
                0xFD -> {
                    packets.add("MAVLink2@$index")
                    index += mavlink2PacketLength(outBuffer, index, bytesRead) ?: 1
                }
                else -> index += 1
            }
        }

        val summary = if (packets.isEmpty()) {
            val magic = outBuffer[0].toInt() and 0xFF
            "unknown firstMagic=0x${magic.toString(16)}"
        } else {
            packets.take(8).joinToString(", ") +
                if (packets.size > 8) ", +${packets.size - 8} more" else ""
        }
        Log.i("UdpTransport", "TX MAVLink scan bytes=$bytesRead packets=$summary")
    }

    private fun mavlink1PacketLength(buffer: ByteArray, start: Int, limit: Int): Int? {
        if (start + 2 > limit) return null
        val payloadLength = buffer[start + 1].toInt() and 0xFF
        val totalLength = payloadLength + 8
        return totalLength.takeIf { start + it <= limit }
    }

    private fun mavlink2PacketLength(buffer: ByteArray, start: Int, limit: Int): Int? {
        if (start + 3 > limit) return null
        val payloadLength = buffer[start + 1].toInt() and 0xFF
        val incompatFlags = buffer[start + 2].toInt() and 0xFF
        val hasSignature = (incompatFlags and 0x01) != 0
        val totalLength = payloadLength + 12 + if (hasSignature) 13 else 0
        return totalLength.takeIf { start + it <= limit }
    }

    private fun logRemoteEndpointIfChanged(address: InetAddress, port: Int) {
        val endpoint = "${address.hostAddress}:$port"
        if (endpoint == lastLoggedRemoteEndpoint) return
        lastLoggedRemoteEndpoint = endpoint
        Log.i("UdpTransport", "RX remote endpoint changed -> $endpoint")
    }
}
