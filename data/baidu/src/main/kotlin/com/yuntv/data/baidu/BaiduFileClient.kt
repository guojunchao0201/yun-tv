package com.yuntv.data.baidu

import com.yuntv.core.model.DriveItem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface BaiduFileService {
    suspend fun listDirectory(path: String, accessToken: String): List<DriveItem>
    suspend fun fileMetas(fsIds: List<Long>, accessToken: String): List<ResolvedDlink>
}

data class ResolvedDlink(val fsId: Long, val url: String)

class BaiduFileClient(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoint: HttpUrl = "https://pan.baidu.com/rest/2.0/xpan/file".toHttpUrl(),
    private val multimediaEndpoint: HttpUrl = "https://pan.baidu.com/rest/2.0/xpan/multimedia".toHttpUrl(),
) : BaiduFileService {
    override suspend fun listDirectory(path: String, accessToken: String): List<DriveItem> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<DriveItem>()
            var start = 0
            do {
                val page = requestPage(path, accessToken, start)
                result += page.items.map { it.toModel(path) }
                start += page.items.size
            } while (page.items.size == PAGE_SIZE)
            result
        }

    override suspend fun fileMetas(
        fsIds: List<Long>,
        accessToken: String,
    ): List<ResolvedDlink> = withContext(Dispatchers.IO) {
        require(fsIds.isNotEmpty()) { "fsIds 不能为空" }
        require(fsIds.size <= FILE_METAS_BATCH_SIZE) { "filemetas 每批最多 100 个 fs_id" }
        val url = multimediaEndpoint.newBuilder()
            .addQueryParameter("method", "filemetas")
            .addQueryParameter("fsids", fsIds.joinToString(separator = ",", prefix = "[", postfix = "]"))
            .addQueryParameter("dlink", "1")
            .addQueryParameter("needmedia", "1")
            .addQueryParameter("access_token", accessToken)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (body.isBlank()) throw IOException("百度文件信息返回空响应（HTTP ${response.code}）")
            val decoded = runCatching { json.decodeFromString<FileMetasResponse>(body) }
                .getOrElse { throw BaiduFileException("无法解析百度文件信息响应", cause = it) }
            if (decoded.errno != 0) throw BaiduFileException.from(decoded.errno, decoded.requestId)
            decoded.list.mapNotNull { item -> item.dlink?.let { ResolvedDlink(item.fsId, it) } }
        }
    }

    private fun requestPage(path: String, accessToken: String, start: Int): ListResponse {
        val url = endpoint.newBuilder()
            .addQueryParameter("method", "list")
            .addQueryParameter("dir", path)
            .addQueryParameter("order", "name")
            .addQueryParameter("start", start.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("web", "1")
            .addQueryParameter("folder", "0")
            .addQueryParameter("access_token", accessToken)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (body.isBlank()) throw IOException("百度文件列表返回空响应（HTTP ${response.code}）")
            val decoded = runCatching { json.decodeFromString<ListResponse>(body) }
                .getOrElse { throw BaiduFileException("无法解析百度文件列表响应", cause = it) }
            if (decoded.errno != 0) throw BaiduFileException.from(decoded.errno, decoded.requestId)
            return decoded
        }
    }

    @Serializable
    private data class ListResponse(
        val errno: Int = 0,
        @SerialName("request_id") val requestId: String? = null,
        val list: List<FileItemResponse> = emptyList(),
    ) {
        val items: List<FileItemResponse> get() = list
    }

    @Serializable
    private data class FileMetasResponse(
        val errno: Int = 0,
        @SerialName("request_id") val requestId: String? = null,
        val list: List<FileMetaResponse> = emptyList(),
    )

    @Serializable
    private data class FileMetaResponse(
        @SerialName("fs_id") val fsId: Long,
        val dlink: String? = null,
    )

    @Serializable
    private data class FileItemResponse(
        @SerialName("fs_id") val fsId: Long,
        @SerialName("server_filename") val name: String,
        val path: String,
        val isdir: Int = 0,
        val size: Long = 0,
        @SerialName("server_mtime") val modifiedAt: Long? = null,
        val category: Int? = null,
        @SerialName("media_type") val mediaType: String? = null,
        val thumbs: Map<String, String>? = null,
    ) {
        fun toModel(parentPath: String) = DriveItem(
            fsId = fsId,
            parentPath = parentPath,
            path = path,
            name = name,
            isDirectory = isdir == 1,
            sizeBytes = size,
            modifiedAtEpochSeconds = modifiedAt,
            category = category,
            mimeType = mediaType,
            thumbnailUrl = thumbs?.get("url3") ?: thumbs?.get("url2") ?: thumbs?.get("url1"),
        )
    }

    private companion object {
        const val PAGE_SIZE = 1_000
        const val FILE_METAS_BATCH_SIZE = 100
        const val USER_AGENT = "pan.baidu.com"
    }
}

class BaiduFileException(
    message: String,
    val errno: Int? = null,
    val requestId: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause) {
    companion object {
        fun from(errno: Int, requestId: String?) = BaiduFileException(
            message = when (errno) {
                -6 -> "百度登录状态已失效"
                -7 -> "没有访问该目录的权限"
                31034, 20012 -> "百度接口请求过于频繁，请稍后重试"
                31066 -> "百度网盘中不存在该目录"
                else -> "百度文件接口请求失败（错误码 $errno）"
            },
            errno = errno,
            requestId = requestId,
        )
    }
}
