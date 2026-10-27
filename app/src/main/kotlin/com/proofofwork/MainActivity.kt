package com.proofofwork

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.proofofwork.ui.navigation.NavGraph
import com.proofofwork.ui.theme.ProofOfWorkTheme

class MainActivity : ComponentActivity() {

    private var autoOpenVoiceModal by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkVoiceTriggerIntent(intent)

        setContent {
            ProofOfWorkTheme {
                NavGraph(
                    autoOpenVoiceModal = autoOpenVoiceModal,
                    onConsumedAutoOpenVoice = { autoOpenVoiceModal = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkVoiceTriggerIntent(intent)
    }

    private fun checkVoiceTriggerIntent(intent: Intent?) {
        if (intent == null) return
        val shouldOpenVoice = intent.getBooleanExtra(EXTRA_AUTO_START_VOICE, false) ||
                intent.action == ACTION_SPEAK_PROOF ||
                intent.action == Intent.ACTION_ASSIST ||
                intent.action == Intent.ACTION_VOICE_COMMAND
        if (shouldOpenVoice) {
            autoOpenVoiceModal = true
        }
    }

    companion object {
        const val EXTRA_AUTO_START_VOICE = "extra_auto_start_voice"
        const val ACTION_SPEAK_PROOF = "com.proofofwork.ACTION_SPEAK_PROOF"
    }
}
