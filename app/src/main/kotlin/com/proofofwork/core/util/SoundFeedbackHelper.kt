package com.proofofwork.core.util

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SoundFeedbackHelper {

    /**
     * Plays a crisp ascending confirmation chime when screen time minutes are minted.
     */
    fun playSuccessChime() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 140)
                delay(160)
                toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
                delay(250)
                toneGen.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Plays a gentle alert tone when a claim is unverified.
     */
    fun playErrorTone() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 220)
                delay(260)
                toneGen.release()
            } catch (_: Exception) {
            }
        }
    }
}
