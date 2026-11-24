package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryDao {

    // Saved Devices
    @Query("SELECT * FROM saved_devices ORDER BY lastConnectedAt DESC")
    fun observeSavedDevices(): Flow<List<SavedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDeviceEntity)

    @Query("DELETE FROM saved_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: Int)

    // Batch Scripts
    @Query("SELECT * FROM batch_scripts ORDER BY isBuiltIn DESC, updatedAt DESC")
    fun observeBatchScripts(): Flow<List<BatchScriptEntity>>

    @Query("SELECT COUNT(*) FROM batch_scripts")
    suspend fun countBatchScripts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: BatchScriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScripts(scripts: List<BatchScriptEntity>)

    @Update
    suspend fun updateScript(script: BatchScriptEntity)

    @Query("DELETE FROM batch_scripts WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomScriptById(id: Int)

    // Command Logs
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeCommandLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommandLog(log: CommandLogEntity)

    @Query("DELETE FROM command_logs")
    suspend fun clearCommandLogs()

    // Saved Presets
    @Query("SELECT * FROM saved_presets ORDER BY createdAt DESC")
    fun observeSavedPresets(): Flow<List<SavedPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SavedPresetEntity)

    @Query("DELETE FROM saved_presets WHERE id = :id")
    suspend fun deletePresetById(id: Int)
}
