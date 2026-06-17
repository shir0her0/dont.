package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppConfig::class, InterventionLog::class, Settings::class],
    version = 2,
    exportSchema = false
)
abstract class TakeASecDatabase : RoomDatabase() {
    abstract fun dao(): TakeASecDao

    companion object {
        @Volatile
        private var INSTANCE: TakeASecDatabase? = null

        fun getDatabase(context: Context): TakeASecDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TakeASecDatabase::class.java,
                    "take_a_sec_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
