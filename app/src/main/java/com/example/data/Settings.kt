package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val mindfulnessText: String = "Its time to take a deep breathe.",
    val delayDurationSeconds: Int = 5,
    val averageSessionLengthMinutes: Int = 5
)
