package com.yuntv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DirectorySnapshotEntity::class, DriveItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class YunTvDatabase : RoomDatabase() {
    abstract fun directoryDao(): DirectoryDao

    companion object {
        fun create(context: Context): YunTvDatabase = Room.databaseBuilder(
            context.applicationContext,
            YunTvDatabase::class.java,
            "yuntv.db",
        ).build()
    }
}
