package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedDeviceEntity::class,
        BatchScriptEntity::class,
        CommandLogEntity::class,
        SavedPresetEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RecoveryDatabase : RoomDatabase() {
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        @Volatile
        private var instance: RecoveryDatabase? = null

        fun getInstance(context: Context): RecoveryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecoveryDatabase::class.java,
                    "recovery_adb_offline.db",
                ).build().also { instance = it }
            }
        }
    }
}
