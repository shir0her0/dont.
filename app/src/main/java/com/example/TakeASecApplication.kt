package com.example

import android.app.Application
import com.example.data.TakeASecDatabase
import com.example.data.TakeASecRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TakeASecApplication : Application() {
    val database by lazy { TakeASecDatabase.getDatabase(this) }
    val repository by lazy { TakeASecRepository(this, database.dao()) }

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.initializeDatabaseIfEmpty()
        }
    }
}
