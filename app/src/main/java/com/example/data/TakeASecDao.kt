package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TakeASecDao {

    // AppConfig Queries
    @Query("SELECT * FROM app_config ORDER BY appName ASC")
    fun getAllConfigs(): Flow<List<AppConfig>>

    @Query("SELECT * FROM app_config WHERE isMonitored = 1")
    fun getMonitoredConfigs(): Flow<List<AppConfig>>

    @Query("SELECT packageName FROM app_config WHERE isMonitored = 1")
    suspend fun getMonitoredPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<AppConfig>)

    @Query("UPDATE app_config SET isMonitored = :isMonitored WHERE packageName = :packageName")
    suspend fun updateMonitoringStatus(packageName: String, isMonitored: Boolean)

    @Query("UPDATE app_config SET reInterventionMinutes = :minutes WHERE packageName = :packageName")
    suspend fun updateReInterventionSetting(packageName: String, minutes: Int)

    @Query("DELETE FROM app_config WHERE packageName = :packageName")
    suspend fun deleteConfig(packageName: String)

    // InterventionLog Queries
    @Query("SELECT * FROM intervention_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<InterventionLog>>

    @Query("SELECT * FROM intervention_log WHERE packageName = :packageName")
    fun getLogsForPackage(packageName: String): Flow<List<InterventionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: InterventionLog)

    @Query("SELECT COUNT(*) FROM intervention_log WHERE wasCancelled = 1")
    fun getTotalCancelledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM intervention_log WHERE timestamp >= :sinceTimestamp")
    fun getInterventionsCountSince(sinceTimestamp: Long): Flow<Int>

    // Settings Queries
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: Settings)
}
