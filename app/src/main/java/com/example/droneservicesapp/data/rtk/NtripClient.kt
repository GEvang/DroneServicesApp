package com.example.droneservicesapp.data.rtk

import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.SocketFactory

class NtripClient {

    companion object {
        private const val TAG = "NtripClient"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 10000
        private const val STREAM_READ_TIMEOUT_MS = 5000
        private const val STREAM_STALL_TIMEOUT_MS = 25000L
        private const val STREAM_BUFFER_SIZE = 1024
        private const val MAX_HANDSHAKE_BYTES = 8192
        private const val PROGRESS_LOG_BYTES = 16 * 1024L
        private const val PROGRESS_LOG_INTERVAL_MS = 5000L
        private const val GGA_INTERVAL_MS = 10000L
    }

    suspend fun fetchSourceTable(
        config: RtkConfig,
        socketFactory: SocketFactory? = null
    ): NtripResult = withContext(Dispatchers.IO) {
        if (!RtkValidator.isValidBaseConfig(config)) {
            return@withContext NtripResult.InvalidConfig("Base RTK settings are incomplete.")
        }

        val response = executeRequest(path = "", config = config, readToEnd = true, socketFactory = socketFactory)
        mapSourceTableResponse(response)
    }

    suspend fun testConnection(
        config: RtkConfig,
        socketFactory: SocketFactory? = null
    ): NtripResult = withContext(Dispatchers.IO) {
        if (!RtkValidator.isValidConfig(config)) {
            return@withContext NtripResult.InvalidConfig("RTK settings are incomplete.")
        }

        val response = executeRequest(
            path = config.mountpoint.trim().trimStart('/'),
            config = config,
            readToEnd = false,
            socketFactory = socketFactory
        )
        mapConnectionResponse(response)
    }

    suspend fun streamCorrections(
        config: RtkConfig,
        attemptNumber: Int = 1,
        ggaLocationProvider: () -> Location? = { null },
        onStreamStarted: () -> Unit = {},
        socketFactory: SocketFactory? = null,
        onBytesReceived: (ByteArray) -> Unit
    ): NtripResult = withContext(Dispatchers.IO) {
        coroutineScope {
            Log.i(
                TAG,
                "stream open requested attempt=$attemptNumber host=${config.ip.trim()} port=${config.port} mountpoint=${config.mountpoint.trim()} usernamePresent=${config.username.isNotBlank()} passwordPresent=${config.password.isNotBlank()}"
            )
            if (!RtkValidator.isValidConfig(config)) {
                Log.w(TAG, "streamCorrections blocked: invalid config")
                return@coroutineScope NtripResult.InvalidConfig("RTK settings are incomplete.")
            }

            val socket = (socketFactory?.createSocket() as? Socket) ?: Socket()
            val coroutineContext = currentCoroutineContext()
            var totalBytesReceived = 0L
            var lastProgressBytes = 0L
            var lastProgressLogMs = 0L
            val startedAtMs = System.currentTimeMillis()
            var firstBytesLogged = false
            var ggaJob: kotlinx.coroutines.Job? = null
            var input: java.io.InputStream? = null
            var output: java.io.OutputStream? = null
            var lastRtcmByteAtMs = startedAtMs

            try {
                val host = config.ip.trim()
                Log.i(TAG, "ntrip: dns resolve start host=$host attempt=$attemptNumber")
                val addresses = InetAddress.getAllByName(host)
                Log.i(TAG, "ntrip: dns resolved host=$host addresses=${addresses.joinToString(prefix = "[", postfix = "]") { it.hostAddress.orEmpty() }}")
                Log.i(TAG, "ntrip: socket create attempt=$attemptNumber")
                Log.i(TAG, "ntrip: socket connect start host=$host port=${config.port} timeoutConnectMs=$CONNECT_TIMEOUT_MS")
                socket.connect(InetSocketAddress(addresses.first(), config.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = STREAM_READ_TIMEOUT_MS
                Log.i(TAG, "ntrip: socket connected timeoutReadMs=$STREAM_READ_TIMEOUT_MS")

                val requestPath = "/${config.mountpoint.trim().trimStart('/')}"
                val request = buildRequest(requestPath, config)

                val outputStream = socket.getOutputStream()
                output = outputStream
                Log.i(TAG, "ntrip: request write start path=$requestPath")
                outputStream.write(request.toByteArray(StandardCharsets.ISO_8859_1))
                outputStream.flush()
                Log.i(TAG, "ntrip: request write success path=$requestPath")

                val inputStream = socket.getInputStream()
                input = inputStream
                val handshake = readStreamingHandshake(inputStream)
                val firstLine = handshake.headerText
                    .replace("\r\n", "\n")
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .trim()
                Log.i(TAG, "ntrip: first response line=$firstLine")
                if (handshake.headerText.isNotBlank()) {
                    Log.i(
                        TAG,
                        "ntrip: response headers=${handshake.headerText.replace("\r\n", " | ").take(512)}"
                    )
                }
                val handshakeResult = mapStreamingHandshake(handshake)
                if (handshakeResult != null) {
                    Log.w(TAG, "stream handshake failed: ${handshakeResult.javaClass.simpleName}")
                    return@coroutineScope handshakeResult
                }

                Log.i(TAG, "ntrip: stream accepted mountpoint=${config.mountpoint.trim()} attempt=$attemptNumber")
                onStreamStarted()

                val initialGgaLocation = ggaLocationProvider()
                if (initialGgaLocation != null) {
                    Log.i(TAG, "ntrip: sending initial GGA")
                    outputStream.write(NmeaGgaBuilder.build(initialGgaLocation).toByteArray(StandardCharsets.US_ASCII))
                    outputStream.flush()
                    ggaJob = launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(GGA_INTERVAL_MS)
                            val periodicLocation = ggaLocationProvider()
                            if (periodicLocation != null) {
                                outputStream.write(NmeaGgaBuilder.build(periodicLocation).toByteArray(StandardCharsets.US_ASCII))
                                outputStream.flush()
                                Log.i(TAG, "ntrip: periodic GGA sent")
                            } else {
                                Log.w(TAG, "ntrip: periodic GGA skipped locationAvailable=false")
                            }
                        }
                    }
                } else {
                    Log.i(TAG, "ntrip: GGA not sent locationAvailable=false")
                }

                if (handshake.remainingBody.isNotEmpty()) {
                    totalBytesReceived += handshake.remainingBody.size
                    lastRtcmByteAtMs = System.currentTimeMillis()
                    if (!firstBytesLogged) {
                        firstBytesLogged = true
                        Log.i(TAG, "rtcm: first bytes received size=${handshake.remainingBody.size} uptimeMs=${System.currentTimeMillis() - startedAtMs}")
                    }
                    onBytesReceived(handshake.remainingBody)
                }

                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (coroutineContext.isActive) {
                    coroutineContext.ensureActive()
                    val count = try {
                        inputStream.read(buffer)
                    } catch (e: SocketTimeoutException) {
                        val lastRtcmAgeMs = System.currentTimeMillis() - lastRtcmByteAtMs
                        Log.w(TAG, "ntrip: read timeout lastRtcmAgeMs=$lastRtcmAgeMs")
                        if (lastRtcmAgeMs >= STREAM_STALL_TIMEOUT_MS) {
                            Log.w(TAG, "ntrip: stream stalled lastRtcmAgeMs=$lastRtcmAgeMs")
                            return@coroutineScope NtripResult.NetworkFailure(
                                "Stream stalled: no RTCM bytes for ${lastRtcmAgeMs / 1000L}s."
                            )
                        }
                        continue
                    }
                    if (count == -1) {
                        Log.w(TAG, "stream ended: caster closed stream")
                        return@coroutineScope NtripResult.NetworkFailure("Caster closed the correction stream.")
                    }
                    if (count > 0) {
                        lastRtcmByteAtMs = System.currentTimeMillis()
                        totalBytesReceived += count
                        if (!firstBytesLogged) {
                            firstBytesLogged = true
                            Log.i(TAG, "rtcm: first bytes received size=$count uptimeMs=${System.currentTimeMillis() - startedAtMs}")
                        }
                        val now = System.currentTimeMillis()
                        if (totalBytesReceived - lastProgressBytes >= PROGRESS_LOG_BYTES ||
                            now - lastProgressLogMs >= PROGRESS_LOG_INTERVAL_MS
                        ) {
                            lastProgressBytes = totalBytesReceived
                            lastProgressLogMs = now
                            Log.i(
                                TAG,
                                "rtcm: totalBytesReceived=$totalBytesReceived lastChunkSize=$count uptimeMs=${now - startedAtMs}"
                            )
                        }
                        onBytesReceived(buffer.copyOf(count))
                    }
                }

                Log.w(TAG, "ntrip: stop requested attempt=$attemptNumber")
                return@coroutineScope NtripResult.ProtocolFailure("Streaming ended unexpectedly.")
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "ntrip: timeout exception type=${e.javaClass.simpleName} message=${sanitizeMessage(e.message)}")
                return@coroutineScope NtripResult.NetworkFailure("Connection timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                coroutineContext.ensureActive()
                Log.w(
                    TAG,
                    "ntrip: network exception type=${e.javaClass.simpleName} message=${sanitizeMessage(e.message)}"
                )
                return@coroutineScope NtripResult.NetworkFailure(sanitizeMessage(e.message))
            } finally {
                ggaJob?.cancel()
                runCatching { ggaJob?.join() }
                runCatching { input?.close() }
                runCatching { output?.close() }
                runCatching { socket.close() }
                Log.i(TAG, "ntrip: stream closed attempt=$attemptNumber totalBytesReceived=$totalBytesReceived")
            }
        }
    }

    private fun executeRequest(
        path: String,
        config: RtkConfig,
        readToEnd: Boolean,
        socketFactory: SocketFactory? = null
    ): RawResponse {
        val socket = (socketFactory?.createSocket() as? Socket) ?: Socket()
        return try {
            Log.i(
                TAG,
                "request open host=${config.ip.trim()} port=${config.port} path=/${path.trimStart('/')} usernamePresent=${config.username.isNotBlank()} passwordPresent=${config.password.isNotBlank()}"
            )
            socket.connect(InetSocketAddress(config.ip.trim(), config.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val requestPath = if (path.isBlank()) "/" else "/$path"
            val request = buildRequest(requestPath, config)

            val output = socket.getOutputStream()
            output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()

            val bodyBytes = ByteArrayOutputStream()
            socket.getInputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    bodyBytes.write(buffer, 0, count)
                    if (!readToEnd && hasEnoughForConnectionCheck(bodyBytes)) {
                        break
                    }
                }
            }

            parseResponse(bodyBytes.toString(StandardCharsets.ISO_8859_1.name()))
        } catch (_: SocketTimeoutException) {
            RawResponse(statusLine = "", body = "", errorMessage = "Connection timed out.")
        } catch (e: Exception) {
            RawResponse(statusLine = "", body = "", errorMessage = sanitizeMessage(e.message))
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun buildRequest(path: String, config: RtkConfig): String {
        val authToken = Base64.getEncoder()
            .encodeToString("${config.username}:${config.password}".toByteArray(StandardCharsets.UTF_8))

        return buildString {
            append("GET $path HTTP/1.0\r\n")
            append("Host: ${config.ip.trim()}:${config.port}\r\n")
            append("User-Agent: NTRIP DroneServicesApp/1.0\r\n")
            append("Ntrip-Version: Ntrip/2.0\r\n")
            append("Accept: */*\r\n")
            append("Connection: close\r\n")
            append("Authorization: Basic $authToken\r\n")
            append("\r\n")
        }
    }

    private fun readStreamingHandshake(input: java.io.InputStream): StreamingHandshake {
        val handshakeBytes = ByteArrayOutputStream()
        val buffer = ByteArray(STREAM_BUFFER_SIZE)

        while (handshakeBytes.size() < MAX_HANDSHAKE_BYTES) {
            val count = input.read(buffer)
            if (count == -1) break
            handshakeBytes.write(buffer, 0, count)

            val bytes = handshakeBytes.toByteArray()
            val delimiterIndex = findHeaderDelimiter(bytes)
            if (delimiterIndex >= 0) {
                val headerBytes = bytes.copyOfRange(0, delimiterIndex)
                val remainingBody = bytes.copyOfRange(delimiterIndex, bytes.size)
                return StreamingHandshake(
                    headerText = headerBytes.toString(StandardCharsets.ISO_8859_1),
                    remainingBody = remainingBody
                )
            }

            val firstLineEnd = findFirstLineEnd(bytes)
            if (firstLineEnd >= 0) {
                val firstLine = bytes.copyOfRange(0, firstLineEnd)
                    .toString(StandardCharsets.ISO_8859_1)
                    .trim()
                if (firstLine.uppercase().startsWith("ICY 200 OK") && bytes.size > firstLineEnd) {
                    return StreamingHandshake(
                        headerText = firstLine,
                        remainingBody = bytes.copyOfRange(firstLineEnd, bytes.size)
                    )
                }
            }
        }

        val text = handshakeBytes.toString(StandardCharsets.ISO_8859_1.name())
        return StreamingHandshake(headerText = text, remainingBody = ByteArray(0))
    }

    private fun findHeaderDelimiter(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 3) {
            if (bytes[index] == '\r'.code.toByte() &&
                bytes[index + 1] == '\n'.code.toByte() &&
                bytes[index + 2] == '\r'.code.toByte() &&
                bytes[index + 3] == '\n'.code.toByte()
            ) {
                return index + 4
            }
        }
        for (index in 0 until bytes.size - 1) {
            if (bytes[index] == '\n'.code.toByte() && bytes[index + 1] == '\n'.code.toByte()) {
                return index + 2
            }
        }
        return -1
    }

    private fun findFirstLineEnd(bytes: ByteArray): Int {
        for (index in bytes.indices) {
            if (bytes[index] == '\n'.code.toByte()) {
                return if (index > 0 && bytes[index - 1] == '\r'.code.toByte()) index + 1 else index + 1
            }
        }
        return -1
    }

    private fun mapStreamingHandshake(handshake: StreamingHandshake): NtripResult? {
        val response = parseResponse(handshake.headerText)
        val failure = mapCommonFailures(response)
        if (failure != null) return failure

        return if (response.isSuccess()) {
            null
        } else {
            NtripResult.ProtocolFailure(response.errorMessage ?: "Unexpected streaming response.")
        }
    }

    private fun parseResponse(responseText: String): RawResponse {
        if (responseText.isBlank()) {
            return RawResponse(statusLine = "", body = "", errorMessage = "Empty response.")
        }

        val normalized = responseText.replace("\r\n", "\n")
        val separatorIndex = normalized.indexOf("\n\n")
        val lines = normalized.lines()
        val statusLine = lines.firstOrNull().orEmpty().trim()
        val body = if (separatorIndex >= 0) {
            normalized.substring(separatorIndex + 2)
        } else {
            lines.drop(1).joinToString("\n")
        }

        return RawResponse(statusLine = statusLine, body = body)
    }

    private fun hasEnoughForConnectionCheck(bytes: ByteArrayOutputStream): Boolean {
        val text = bytes.toString(StandardCharsets.ISO_8859_1.name()).replace("\r\n", "\n")
        return text.contains("\n") && (text.contains("\n\n") || text.length >= 64)
    }

    private fun mapSourceTableResponse(response: RawResponse): NtripResult {
        val failure = mapCommonFailures(response)
        if (failure != null) return failure

        if (!response.isSuccess()) {
            return NtripResult.ProtocolFailure(
                response.errorMessage ?: "Unexpected sourcetable response."
            )
        }

        val mountpoints = response.body
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("STR;") }
            .mapNotNull { line ->
                val fields = line.split(';')
                fields.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .toList()

        return NtripResult.SourceTableSuccess(mountpoints)
    }

    private fun mapConnectionResponse(response: RawResponse): NtripResult {
        val failure = mapCommonFailures(response)
        if (failure != null) return failure

        if (response.isSuccess()) {
            val bodyUpper = response.body.uppercase()
            if (bodyUpper.contains("ENDSOURCETABLE") || bodyUpper.contains("\nSTR;")) {
                return NtripResult.MountpointNotFound
            }
            return NtripResult.ConnectionSuccess
        }

        val upperStatus = response.statusLine.uppercase()
        return when {
            upperStatus.contains("404") -> NtripResult.MountpointNotFound
            upperStatus.contains("SOURCETABLE") -> NtripResult.MountpointNotFound
            else -> NtripResult.ProtocolFailure(
                response.errorMessage ?: "Unexpected mountpoint response."
            )
        }
    }

    private fun mapCommonFailures(response: RawResponse): NtripResult? {
        if (!response.errorMessage.isNullOrBlank() && response.statusLine.isBlank()) {
            return NtripResult.NetworkFailure(response.errorMessage)
        }

        val combined = "${response.statusLine}\n${response.body}".uppercase()
        return when {
            combined.contains("401") ||
                combined.contains("403") ||
                combined.contains("BAD PASSWORD") ||
                combined.contains("UNAUTHORIZED") ||
                combined.contains("FORBIDDEN") -> {
                Log.w(TAG, "ntrip: auth failure detected")
                NtripResult.AuthFailure
            }

            combined.contains("404") ||
                combined.contains("NOT FOUND") ||
                combined.contains("BAD MOUNTPOINT") -> {
                Log.w(TAG, "ntrip: mountpoint not found detected")
                NtripResult.MountpointNotFound
            }

            else -> null
        }
    }

    private fun sanitizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return "Unable to reach caster."
        return message
            .replace(Regex("(?i)authorization: basic\\s+[A-Za-z0-9+/=]+"), "authorization: [redacted]")
            .replace(Regex("(?i)basic\\s+[A-Za-z0-9+/=]+"), "basic [redacted]")
            .replace(Regex("(?i)([\\w.%+-]+):([^@\\s]+)@"), "$1:[redacted]@")
    }

    private data class RawResponse(
        val statusLine: String,
        val body: String,
        val errorMessage: String? = null
    ) {
        fun isSuccess(): Boolean {
            val upperStatus = statusLine.uppercase()
            return upperStatus.contains("200 OK") || upperStatus.contains("SOURCETABLE 200 OK")
        }
    }

    private data class StreamingHandshake(
        val headerText: String,
        val remainingBody: ByteArray
    )

}
