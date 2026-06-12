package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isMonitored: Boolean = false
)
