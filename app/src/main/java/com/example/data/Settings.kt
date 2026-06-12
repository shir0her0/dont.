package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val mindfulnessText: String = "Take a breath. Do you really want to open this app?",
    val delayDurationSeconds: Int = 5,
    val averageSessionLengthMinutes: Int = 5
)
