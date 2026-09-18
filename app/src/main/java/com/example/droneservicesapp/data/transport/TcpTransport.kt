package com.example.droneservicesapp.data.transport

import android.net.Network
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** A reconnecting, bidirectional MAVLink TCP client transport. */
class TcpTransport(
    host: String,
    private val port: Int,
    private val network: Network? = null,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val logInfo: (String) -> Unit = { Log.i(TAG, it) },
    private val logError: (String, Throwable) -> Unit = { message, error -> Log.e(TAG, message, error) },
) : MavTransport {
    private val host = host.trim().also {
        require(it.isNotEmpty()) { "A target host is required for TCP" }
    }

    init {
        require(port in 1..65535) { "TCP target port must be between 1 and 65535" }
        require(connectTimeoutMs > 0) { "TCP connection timeout must be positive" }
    }

    private val receiveInput = PipedInputStream(PIPE_BUFFER_SIZE)
    private val receiveOutput = PipedOutputStream(receiveInput)
    private val sendInput = PipedInputStream(PIPE_BUFFER_SIZE)
    private val sendOutput = PipedOutputStream(sendInput)

    override val input: InputStream = receiveInput
    override val output: OutputStream = sendOutput

    private val running = AtomicBoolean(false)
    private val socketLock = Any()

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var connectionThread: Thread? = null

    @Volatile
    private var outboundThread: Thread? = null

    override fun start() {
        if (running.getAndSet(true)) return

        logInfo("Starting TCP transport target=$host:$port")
        connectionThread = Thread(::runConnectionLoop, "TcpTransportConnection-$port").apply {
            isDaemon = true
            start()
        }
        outboundThread = Thread(::runOutboundLoop, "TcpTransportOutbound-$port").apply {
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        if (!running.getAndSet(false)) return

        closeActiveSocket()
        connectionThread?.interrupt()
        outboundThread?.interrupt()
        closeQuietly(receiveOutput)
        closeQuietly(sendInput)
        closeQuietly(receiveInput)
        closeQuietly(sendOutput)
        joinQuietly(connectionThread)
        joinQuietly(outboundThread)
        connectionThread = null
        outboundThread = null
        logInfo("Stopped TCP transport")
    }

    private fun runConnectionLoop() {
        while (running.get()) {
            var connectingSocket: Socket? = null
            try {
                val newSocket = network?.socketFactory?.createSocket() ?: Socket()
                connectingSocket = newSocket
                newSocket.tcpNoDelay = true
                newSocket.keepAlive = true
                newSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                synchronized(socketLock) {
                    if (!running.get()) {
                        newSocket.close()
                        return
                    }
                    socket = newSocket
                }
                logInfo("Connected TCP transport target=$host:$port")

                val buffer = ByteArray(IO_BUFFER_SIZE)
                val socketInput = newSocket.getInputStream()
                while (running.get()) {
                    val count = socketInput.read(buffer)
                    if (count < 0) break
                    receiveOutput.write(buffer, 0, count)
                    receiveOutput.flush()
                }
            } catch (error: Exception) {
                if (running.get()) {
                    logError("TCP connection failed target=$host:$port; retrying", error)
                }
            } finally {
                connectingSocket?.let(::clearAndCloseSocket)
            }

            if (running.get()) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    private fun runOutboundLoop() {
        val buffer = ByteArray(IO_BUFFER_SIZE)
        while (running.get()) {
            val count = try {
                sendInput.read(buffer)
            } catch (_: Exception) {
                return
            }
            if (count < 0) return

            var sent = false
            while (running.get() && !sent) {
                val activeSocket = socket
                if (activeSocket == null || activeSocket.isClosed) {
                    try {
                        Thread.sleep(OUTBOUND_RETRY_DELAY_MS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                    continue
                }

                try {
                    activeSocket.getOutputStream().write(buffer, 0, count)
                    activeSocket.getOutputStream().flush()
                    sent = true
                } catch (error: Exception) {
                    if (running.get()) {
                        logError("TCP write failed target=$host:$port; reconnecting", error)
                    }
                    clearAndCloseSocket(activeSocket)
                }
            }
        }
    }

    private fun clearAndCloseSocket(candidate: Socket) {
        synchronized(socketLock) {
            if (socket === candidate) socket = null
        }
        closeQuietly(candidate)
    }

    private fun closeActiveSocket() {
        val activeSocket = synchronized(socketLock) {
            val value = socket
            socket = null
            value
        }
        closeQuietly(activeSocket)
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        runCatching { closeable?.close() }
    }

    private fun joinQuietly(thread: Thread?) {
        if (thread == null || thread === Thread.currentThread()) return
        try {
            thread.join(STOP_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val TAG = "TcpTransport"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
        const val PIPE_BUFFER_SIZE = 16 * 1024
        const val IO_BUFFER_SIZE = 4 * 1024
        const val RECONNECT_DELAY_MS = 1_000L
        const val OUTBOUND_RETRY_DELAY_MS = 50L
        const val STOP_JOIN_TIMEOUT_MS = 1_000L
    }
}
