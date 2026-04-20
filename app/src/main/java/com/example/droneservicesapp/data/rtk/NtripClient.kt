package com.example.droneservicesapp.data.rtk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Base64

class NtripClient {

    suspend fun fetchSourceTable(config: RtkConfig): NtripResult = withContext(Dispatchers.IO) {
        if (!RtkValidator.isValidBaseConfig(config)) {
            return@withContext NtripResult.InvalidConfig("Base RTK settings are incomplete.")
        }

        val response = executeRequest(path = "", config = config, readToEnd = true)
        mapSourceTableResponse(response)
    }

    suspend fun testConnection(config: RtkConfig): NtripResult = withContext(Dispatchers.IO) {
        if (!RtkValidator.isValidConfig(config)) {
            return@withContext NtripResult.InvalidConfig("RTK settings are incomplete.")
        }

        val response = executeRequest(
            path = config.mountpoint.trim().trimStart('/'),
            config = config,
            readToEnd = false
        )
        mapConnectionResponse(response)
    }

    suspend fun streamCorrections(
        config: RtkConfig,
        onStreamStarted: () -> Unit = {},
        onBytesReceived: (ByteArray) -> Unit
    ): NtripResult = withContext(Dispatchers.IO) {
        if (!RtkValidator.isValidConfig(config)) {
            return@withContext NtripResult.InvalidConfig("RTK settings are incomplete.")
        }

        val socket = Socket()
        val coroutineContext = currentCoroutineContext()

        try {
            socket.connect(InetSocketAddress(config.ip.trim(), config.port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = STREAM_READ_TIMEOUT_MS

            val requestPath = "/${config.mountpoint.trim().trimStart('/')}"
            val request = buildRequest(requestPath, config)

            val output = socket.getOutputStream()
            output.write(request.toByteArray(StandardCharsets.ISO_8859_1))
            output.flush()

            val input = socket.getInputStream()
            val handshake = readStreamingHandshake(input)
            val handshakeResult = mapStreamingHandshake(handshake)
            if (handshakeResult != null) {
                return@withContext handshakeResult
            }

            onStreamStarted()

            if (handshake.remainingBody.isNotEmpty()) {
                onBytesReceived(handshake.remainingBody)
            }

            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count == -1) {
                    return@withContext NtripResult.NetworkFailure("Caster closed the correction stream.")
                }
                if (count > 0) {
                    onBytesReceived(buffer.copyOf(count))
                }
            }
        } catch (_: SocketTimeoutException) {
            return@withContext NtripResult.NetworkFailure("Connection timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            coroutineContext.ensureActive()
            return@withContext NtripResult.NetworkFailure(sanitizeMessage(e.message))
        } finally {
            runCatching { socket.close() }
        }

        return@withContext NtripResult.ProtocolFailure("Streaming ended unexpectedly.")
    }

    private fun executeRequest(path: String, config: RtkConfig, readToEnd: Boolean): RawResponse {
        val socket = Socket()
        return try {
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
                combined.contains("FORBIDDEN") -> NtripResult.AuthFailure

            combined.contains("404") ||
                combined.contains("NOT FOUND") ||
                combined.contains("BAD MOUNTPOINT") -> NtripResult.MountpointNotFound

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

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 7000
        private const val STREAM_READ_TIMEOUT_MS = 15000
        private const val STREAM_BUFFER_SIZE = 1024
        private const val MAX_HANDSHAKE_BYTES = 8192
    }
}
