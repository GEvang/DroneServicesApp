package com.example.droneservicesapp.ui.common

import android.media.AudioManager
import android.media.ToneGenerator
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object RtkTonePlayer {
    private const val CONNECTED_BEEP_DURATION_MS = 160
    private const val CONNECTED_BEEP_PAUSE_MS = 110L
    private const val DISCONNECTED_BEEP_DURATION_MS = 900
    private val playbackGeneration = AtomicInteger(0)
    private val playbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RtkTonePlayer").apply { isDaemon = true }
    }

    fun playConnectedTone() {
        playAsync(
            stream = AudioManager.STREAM_NOTIFICATION,
            volume = 85
        ) { tone, generation ->
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, CONNECTED_BEEP_DURATION_MS)
            Thread.sleep(CONNECTED_BEEP_DURATION_MS + CONNECTED_BEEP_PAUSE_MS)
            if (generation != playbackGeneration.get()) return@playAsync
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, CONNECTED_BEEP_DURATION_MS)
            Thread.sleep(CONNECTED_BEEP_DURATION_MS.toLong())
        }
    }

    fun playDisconnectedTone() {
        playAsync(
            stream = AudioManager.STREAM_NOTIFICATION,
            volume = 90
        ) { tone, _ ->
            tone.startTone(ToneGenerator.TONE_PROP_NACK, DISCONNECTED_BEEP_DURATION_MS)
            Thread.sleep(DISCONNECTED_BEEP_DURATION_MS.toLong())
        }
    }

    private fun playAsync(
        stream: Int,
        volume: Int,
        block: (ToneGenerator, Int) -> Unit
    ) {
        val generation = playbackGeneration.incrementAndGet()
        playbackExecutor.execute {
            if (generation != playbackGeneration.get()) return@execute
            val tone = ToneGenerator(stream, volume)
            try {
                block(tone, generation)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                tone.stopTone()
                tone.release()
            }
        }
    }
}
