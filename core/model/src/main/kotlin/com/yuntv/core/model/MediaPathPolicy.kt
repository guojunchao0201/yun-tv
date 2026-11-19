package com.yuntv.core.model

object MediaPathPolicy {
    const val DEFAULT_ROOT_PATH: String = "/apps/视频"
    const val MAX_FILE_META_BATCH_SIZE: Int = 100

    fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") return "/"
        return "/" + trimmed.trim('/').replace(Regex("/{2,}"), "/")
    }

    fun isRoot(path: String, rootPath: String = DEFAULT_ROOT_PATH): Boolean =
        normalize(path) == normalize(rootPath)

    fun shouldResolveDlinks(path: String, rootPath: String = DEFAULT_ROOT_PATH): Boolean =
        !isRoot(path, rootPath)

    fun batchFsIds(fsIds: List<Long>): List<List<Long>> =
        fsIds.distinct().chunked(MAX_FILE_META_BATCH_SIZE)
}
