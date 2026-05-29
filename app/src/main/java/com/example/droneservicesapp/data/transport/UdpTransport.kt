package com.example.droneservicesapp.data.transport

import android.net.Network
import android.util.Log
import java.io.ByteArrayOutputStream
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
    targetHost: String? = null,
    private val network: Network? = null
) : MavTransport {
    private companion object {
        private const val TAG = "UdpTransport"
        private const val OUTBOUND_READ_BUFFER_SIZE = 4096
        private const val MAX_UDP_MAVLINK_DATAGRAM_BYTES = 1200
    }

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
        Log.i(TAG, "connect requested on UDP transport listenPort=$listenPort running=${running.get()}")
        if (running.getAndSet(true)) {
            Log.i(TAG, "start skipped: UDP transport already running")
            return
        }

        try {
            Log.i(
                TAG,
                "creating/binding UDP socket listenPort=$listenPort network=${network?.networkHandle ?: "<default>"}"
            )
            socket = DatagramSocket(listenPort).also { udpSocket ->
                bindSocketToNetwork(udpSocket)
            }
            socket?.soTimeout = 200  // 200ms timeout to periodically wake up and flush
            Log.i(
                TAG,
                "UDP socket created/bound local=${socket?.localAddress?.hostAddress}:${socket?.localPort} configuredTarget=${configuredTargetIP?.hostAddress ?: "<auto>"} network=${network?.networkHandle ?: "<default>"}"
            )
            Thread({ runLoop() }, "UdpTransport-$listenPort").apply { isDaemon = true }.start()
            Log.i(TAG, "Started UDP listen on $listenPort")
        } catch (e: Exception) {
            running.set(false)
            Log.e(TAG, "Failed to start: ${e.message}", e)
            throw e
        }
    }

    override fun stop() {
        Log.i(TAG, "stop requested running=${running.get()} remote=${remoteIP?.hostAddress}:${remotePort}")
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


        Log.i(TAG, "Stopped UDP transport")
    }

    private fun runLoop() {
        val udpSocket = socket ?: return
        val receiveData = ByteArray(4096)
        val outBuffer = ByteArray(OUTBOUND_READ_BUFFER_SIZE)
        var pendingOutbound = ByteArray(0)

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

                pendingOutbound = flushOutboundMavlinkFrames(udpSocket, outBuffer, pendingOutbound)
            } catch (e: SocketTimeoutException) {
                pendingOutbound = flushOutboundMavlinkFrames(udpSocket, outBuffer, pendingOutbound)
            } catch (e: SocketException) {
                // Happens on stop() because close() breaks receive()
                break
            } catch (e: Exception) {
                Log.e(TAG, "UDP loop error: ${e.message}", e)
            }
        }
    }

    private fun bindSocketToNetwork(udpSocket: DatagramSocket) {
        val selectedNetwork = network ?: return
        runCatching {
            selectedNetwork.bindSocket(udpSocket)
            Log.i(TAG, "bound UDP socket to network=${selectedNetwork.networkHandle}")
        }.onFailure { error ->
            Log.w(
                TAG,
                "failed to bind UDP socket to network=${selectedNetwork.networkHandle} type=${error.javaClass.simpleName} message=${error.message}"
            )
        }
    }

    private fun flushOutboundMavlinkFrames(
        udpSocket: DatagramSocket,
        outBuffer: ByteArray,
        pendingOutbound: ByteArray
    ): ByteArray {
        if (!hasOutboundTarget()) return pendingOutbound

        var pending = pendingOutbound
        while (sndPIS.available() > 0) {
            val bytesRead = sndPIS.read(outBuffer, 0, minOf(outBuffer.size, sndPIS.available().coerceAtLeast(1)))
            if (bytesRead <= 0) break
            pending = pending + outBuffer.copyOf(bytesRead)
        }

        if (pending.isEmpty()) return pending
        return sendCompleteMavlinkFrames(udpSocket, pending)
    }

    private fun sendCompleteMavlinkFrames(
        udpSocket: DatagramSocket,
        pending: ByteArray
    ): ByteArray {
        var index = 0
        var skippedBytes = 0
        val datagram = ByteArrayOutputStream(MAX_UDP_MAVLINK_DATAGRAM_BYTES)

        fun flushDatagram() {
            if (datagram.size() <= 0) return
            val bytes = datagram.toByteArray()
            sendToRemoteCandidates(udpSocket, bytes, bytes.size)
            datagram.reset()
        }

        while (index < pending.size) {
            val magic = pending[index].toInt() and 0xFF
            val packetLength = when (magic) {
                0xFE -> mavlink1PacketLength(pending, index, pending.size)
                0xFD -> mavlink2PacketLength(pending, index, pending.size)
                else -> null
            }

            if (magic != 0xFE && magic != 0xFD) {
                index++
                skippedBytes++
                continue
            }

            if (packetLength == null) break

            if (datagram.size() > 0 && datagram.size() + packetLength > MAX_UDP_MAVLINK_DATAGRAM_BYTES) {
                flushDatagram()
            }
            datagram.write(pending, index, packetLength)
            index += packetLength
        }

        flushDatagram()
        if (skippedBytes > 0) {
            Log.w(TAG, "discarded non-MAVLink outbound bytes count=$skippedBytes")
        }

        return pending.copyOfRange(index, pending.size)
    }

    private fun hasOutboundTarget(): Boolean {
        return configuredTargetIP != null || (remoteIP != null && remotePort > 0)
    }

    private fun sendToRemoteCandidates(
        udpSocket: DatagramSocket,
        outBuffer: ByteArray,
        bytesRead: Int
    ) {
        val remoteAddress = remoteIP ?: configuredTargetIP ?: return
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
            Log.i(TAG, "SENT $bytesRead bytes -> ${address.hostAddress}:$port")
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
        Log.i(TAG, "TX MAVLink scan bytes=$bytesRead packets=$summary")
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
        Log.i(TAG, "RX remote endpoint changed -> $endpoint")
    }
}
