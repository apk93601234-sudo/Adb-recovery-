package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val host: String,
    val port: Int = 5555,
    val model: String = "",
    val lastConnectedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "batch_scripts")
data class BatchScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val titleEn: String,
    val description: String,
    val descriptionEn: String,
    val category: String,
    val commandsText: String,
    val delayMs: Long = 250L,
    val stopOnError: Boolean = true,
    val isBuiltIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceLabel: String,
    val command: String,
    val output: String,
    val isSuccess: Boolean,
    val durationMs: Long,
)

@Entity(tableName = "saved_presets")
data class SavedPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val command: String,
    val category: String,
    val riskLevel: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
