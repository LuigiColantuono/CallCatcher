package com.callcatcher.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class CallCatcherDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile private var instance: CallCatcherDatabase? = null

        fun get(context: Context): CallCatcherDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CallCatcherDatabase::class.java,
                    "callcatcher.db",
                ).build().also { instance = it }
            }
    }
}
