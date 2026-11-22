package com.yuntv.data.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File

data class GeneratedCover(val width: Int, val height: Int, val fileSizeBytes: Long)

interface CoverGenerator {
    suspend fun fromImage(bytes: ByteArray, destination: File): GeneratedCover
    suspend fun fromVideo(url: String, headers: Map<String, String>, destination: File): GeneratedCover
}

class AndroidCoverGenerator : CoverGenerator {
    override suspend fun fromImage(bytes: ByteArray, destination: File): GeneratedCover {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("缩略图无法解码")
        return bitmap.useAsCover(destination)
    }

    override suspend fun fromVideo(
        url: String,
        headers: Map<String, String>,
        destination: File,
    ): GeneratedCover {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, headers)
            val bitmap = retriever.getFrameAtTime(
                FRAME_TIME_US,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: error("视频帧提取失败")
            bitmap.useAsCover(destination)
        } finally {
            retriever.release()
        }
    }

    private fun Bitmap.useAsCover(destination: File): GeneratedCover {
        try {
            val sourceRatio = width.toFloat() / height
            val targetRatio = TARGET_WIDTH.toFloat() / TARGET_HEIGHT
            val cropWidth: Int
            val cropHeight: Int
            if (sourceRatio > targetRatio) {
                cropHeight = height
                cropWidth = (height * targetRatio).toInt()
            } else {
                cropWidth = width
                cropHeight = (width / targetRatio).toInt()
            }
            val cropped = Bitmap.createBitmap(
                this,
                (width - cropWidth) / 2,
                (height - cropHeight) / 2,
                cropWidth,
                cropHeight,
            )
            val scaled = if (cropped.width > TARGET_WIDTH || cropped.height > TARGET_HEIGHT) {
                Bitmap.createScaledBitmap(cropped, TARGET_WIDTH, TARGET_HEIGHT, true)
            } else {
                cropped
            }
            try {
                val temporary = File(destination.parentFile, "${destination.name}.tmp")
                temporary.outputStream().buffered().use { output ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    check(scaled.compress(format, WEBP_QUALITY, output)) { "封面写入失败" }
                }
                if (destination.exists()) check(destination.delete()) { "旧封面删除失败" }
                check(temporary.renameTo(destination)) { "封面原子替换失败" }
                return GeneratedCover(scaled.width, scaled.height, destination.length())
            } finally {
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== this) cropped.recycle()
            }
        } finally {
            recycle()
        }
    }

    private companion object {
        const val TARGET_WIDTH = 640
        const val TARGET_HEIGHT = 360
        const val WEBP_QUALITY = 82
        const val FRAME_TIME_US = 3_000_000L
    }
}
