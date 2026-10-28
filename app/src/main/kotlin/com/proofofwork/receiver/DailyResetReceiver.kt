package com.proofofwork.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.proofofwork.ProofOfWorkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val app = context?.applicationContext as? ProofOfWorkApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val didReset = app.preferencesRepository.syncDailyResetIfNeeded()
                if (didReset) {
                    app.database.taskDao().resetDailyTasks()
                }
            } catch (_: Exception) {
            }
        }
    }
}
