package com.example.droneservicesapp.ui.common

import android.media.AudioManager
import android.media.ToneGenerator

object RtkTonePlayer {
    fun playConnectedTone() {
        play(
            stream = AudioManager.STREAM_NOTIFICATION,
            volume = 85
        ) { tone ->
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 220)
            Thread.sleep(90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
        }
    }

    fun playDisconnectedTone() {
        play(
            stream = AudioManager.STREAM_NOTIFICATION,
            volume = 90
        ) { tone ->
            tone.startTone(ToneGenerator.TONE_PROP_NACK, 320)
        }
    }

    private inline fun play(
        stream: Int,
        volume: Int,
        block: (ToneGenerator) -> Unit
    ) {
        val tone = ToneGenerator(stream, volume)
        try {
            block(tone)
        } finally {
            tone.release()
        }
    }
}
