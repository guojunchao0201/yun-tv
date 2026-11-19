package com.yuntv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPathPolicyTest {
    @Test
    fun rootDoesNotResolveDlinks() {
        assertTrue(MediaPathPolicy.isRoot("/apps/视频/"))
        assertFalse(MediaPathPolicy.shouldResolveDlinks("/apps/视频"))
    }

    @Test
    fun childFolderResolvesDlinks() {
        assertTrue(MediaPathPolicy.shouldResolveDlinks("/apps/视频/宠爱时光"))
    }

    @Test
    fun fsIdsAreDeduplicatedAndBatchedByOneHundred() {
        val ids = (1L..205L).toList() + listOf(1L, 2L)
        val batches = MediaPathPolicy.batchFsIds(ids)

        assertEquals(listOf(100, 100, 5), batches.map(List<Long>::size))
        assertEquals(205, batches.flatten().size)
    }
}
