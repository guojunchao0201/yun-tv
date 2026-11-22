package com.yuntv.core.database

import com.yuntv.core.model.PlaybackProgress
import com.yuntv.core.model.PlaybackProgressRepository
import kotlinx.coroutines.flow.map

class RoomPlaybackProgressRepository(
    private val dao: PlaybackDao,
) : PlaybackProgressRepository {
    override fun observeProgress(fsId: Long) = dao.observeProgress(fsId).map { it?.toModel() }

    override suspend fun saveProgress(progress: PlaybackProgress) {
        dao.upsertProgress(progress.toEntity())
    }

    private fun PlaybackProgressEntity.toModel() = PlaybackProgress(
        fsId, path, positionMs, durationMs, completed, updatedAtEpochSeconds,
    )

    private fun PlaybackProgress.toEntity() = PlaybackProgressEntity(
        fsId, path, positionMs, durationMs, completed, updatedAtEpochSeconds,
    )
}
