package com.example.droneservicesapp.data.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TcpTransportTest {
    @Test
    fun exchangesBytesInBothDirections() {
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val serverExchange = executor.submit {
                server.accept().use { client ->
                    val request = ByteArray(4)
                    client.getInputStream().readFully(request)
                    assertArrayEquals("ping".toByteArray(), request)
                    client.getOutputStream().write("pong".toByteArray())
                    client.getOutputStream().flush()
                }
            }

            val transport = TcpTransport(
                host = "127.0.0.1",
                port = server.localPort,
                connectTimeoutMs = 1_000,
                logInfo = {},
                logError = { _, _ -> },
            )
            try {
                transport.start()
                transport.output.write("ping".toByteArray())
                transport.output.flush()

                val response = ByteArray(4)
                transport.input.readFully(response)
                assertArrayEquals("pong".toByteArray(), response)
                serverExchange.get(2, TimeUnit.SECONDS)
            } finally {
                transport.stop()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun startDoesNotPerformNetworkIoOnCallingThread() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val transport = TcpTransport(
            host = "127.0.0.1",
            port = unavailablePort,
            connectTimeoutMs = 250,
            logInfo = {},
            logError = { _, _ -> },
        )

        val startedAt = System.nanoTime()
        transport.start()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        transport.stop()

        org.junit.Assert.assertTrue("start blocked for ${elapsedMs}ms", elapsedMs < 200)
    }

    private fun java.io.InputStream.readFully(destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = read(destination, offset, destination.size - offset)
            check(count >= 0) { "Stream closed before ${destination.size} bytes were read" }
            offset += count
        }
    }
}
