package com.example.droneservicesapp.data.rtk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 7000
    }
}
