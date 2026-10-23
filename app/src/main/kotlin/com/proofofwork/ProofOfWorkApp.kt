package com.proofofwork

import android.app.Application
import com.proofofwork.core.data.UserPreferencesRepository
import com.proofofwork.core.data.database.AppDatabase

class ProofOfWorkApp : Application() {

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesRepository = UserPreferencesRepository(this)
        database = AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: ProofOfWorkApp
            private set
    }
}
