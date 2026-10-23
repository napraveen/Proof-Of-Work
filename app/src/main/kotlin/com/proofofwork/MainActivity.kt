package com.proofofwork

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.proofofwork.ui.navigation.NavGraph
import com.proofofwork.ui.theme.ProofOfWorkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ProofOfWorkTheme {
                NavGraph()
            }
        }
    }
}
