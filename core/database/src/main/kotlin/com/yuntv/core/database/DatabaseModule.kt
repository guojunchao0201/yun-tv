package com.yuntv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DirectorySnapshotEntity::class,
        DriveItemEntity::class,
        CoverCacheEntity::class,
        CoverFailureEntity::class,
        DlinkCacheEntity::class,
        PlaybackProgressEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class YunTvDatabase : RoomDatabase() {
    abstract fun directoryDao(): DirectoryDao
    abstract fun coverDao(): CoverDao
    abstract fun playbackDao(): PlaybackDao

    companion object {
        fun create(context: Context): YunTvDatabase = Room.databaseBuilder(
            context.applicationContext,
            YunTvDatabase::class.java,
            "yuntv.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cover_cache` (
                        |`fs_id` INTEGER NOT NULL,
                        |`source_modified_at` INTEGER,
                        |`source_type` TEXT NOT NULL,
                        |`local_path` TEXT NOT NULL,
                        |`width` INTEGER NOT NULL,
                        |`height` INTEGER NOT NULL,
                        |`file_size` INTEGER NOT NULL,
                        |`created_at` INTEGER NOT NULL,
                        |`last_accessed_at` INTEGER NOT NULL,
                        |PRIMARY KEY(`fs_id`))
                    """.trimMargin(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cover_failure` (
                        |`fs_id` INTEGER NOT NULL,
                        |`source_modified_at` INTEGER,
                        |`failed_at` INTEGER NOT NULL,
                        |PRIMARY KEY(`fs_id`))
                    """.trimMargin(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `dlink_cache` (
                        |`fs_id` INTEGER NOT NULL,
                        |`url` TEXT NOT NULL,
                        |`obtained_at` INTEGER NOT NULL,
                        |`expires_at` INTEGER NOT NULL,
                        |PRIMARY KEY(`fs_id`))
                    """.trimMargin(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `playback_progress` (
                        |`fs_id` INTEGER NOT NULL,
                        |`path` TEXT NOT NULL,
                        |`position_ms` INTEGER NOT NULL,
                        |`duration_ms` INTEGER NOT NULL,
                        |`completed` INTEGER NOT NULL,
                        |`updated_at` INTEGER NOT NULL,
                        |PRIMARY KEY(`fs_id`))
                    """.trimMargin(),
                )
            }
        }
    }
}
