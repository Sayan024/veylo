package com.veylo.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.veylo.app.data.ProfileStore
import com.veylo.app.data.VeyloRepository
import com.veylo.app.data.local.VeyloDatabase
import com.veylo.app.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VeyloApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container (keeps the build light; swap for Hilt later if wanted). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: VeyloDatabase = Room.databaseBuilder(context, VeyloDatabase::class.java, "veylo.db")
        .fallbackToDestructiveMigration()
        .build()
    val profiles = ProfileStore(context)
    val repository = VeyloRepository(context, db.dao(), profiles)
    val sync = SyncEngine(context, db.dao(), profiles, repository.changes, appScope)
}
