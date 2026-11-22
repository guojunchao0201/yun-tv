package com.yuntv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DirectoryDao {
    @Query("SELECT * FROM directory_snapshot WHERE path = :path")
    abstract fun observeSnapshot(path: String): Flow<DirectorySnapshotEntity?>

    @Query("SELECT * FROM drive_item WHERE parent_path = :path ORDER BY is_directory DESC, sort_key ASC")
    abstract fun observeItems(path: String): Flow<List<DriveItemEntity>>

    @Query("SELECT * FROM directory_snapshot WHERE path = :path")
    abstract suspend fun getSnapshot(path: String): DirectorySnapshotEntity?

    @Query("SELECT * FROM drive_item WHERE parent_path = :path ORDER BY is_directory DESC, sort_key ASC")
    abstract suspend fun getItems(path: String): List<DriveItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSnapshot(snapshot: DirectorySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(items: List<DriveItemEntity>)

    @Query("DELETE FROM drive_item WHERE parent_path = :path")
    protected abstract suspend fun deleteItems(path: String)

    @Transaction
    open suspend fun replaceDirectory(
        snapshot: DirectorySnapshotEntity,
        items: List<DriveItemEntity>,
    ) {
        deleteItems(snapshot.path)
        insertItems(items)
        insertSnapshot(snapshot)
    }
}
