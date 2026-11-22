package com.yuntv.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.model.CloudDriveRepository
import com.yuntv.core.model.CoverRepository
import com.yuntv.core.model.DriveItem
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.model.PlaybackSource
import com.yuntv.core.ui.FocusTile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun BrowserScreen(
    repository: CloudDriveRepository,
    coverRepository: CoverRepository,
    onOpenSettings: () -> Unit,
    onPlay: (Long) -> Unit,
) {
    var currentPath by rememberSaveable { mutableStateOf(MediaPathPolicy.DEFAULT_ROOT_PATH) }
    val isRoot = MediaPathPolicy.isRoot(currentPath)
    val focusedIndexByPath = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val snapshot by remember(currentPath) {
        repository.observeDirectory(currentPath)
    }.collectAsState(initial = null)
    var refreshing by remember(currentPath) { mutableStateOf(false) }
    var errorMessage by remember(currentPath) { mutableStateOf<String?>(null) }
    var playbackSources by remember(currentPath) { mutableStateOf<Map<Long, PlaybackSource>>(emptyMap()) }
    var playbackPreparing by remember(currentPath) { mutableStateOf(false) }
    var playbackError by remember(currentPath) { mutableStateOf<String?>(null) }

    suspend fun refresh(force: Boolean) {
        refreshing = true
        errorMessage = null
        try {
            repository.refreshDirectory(currentPath, force)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: "目录加载失败"
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(currentPath) { refresh(force = false) }
    LaunchedEffect(currentPath, snapshot?.items?.map { it.fsId }) {
        val videoIds = snapshot?.items.orEmpty().filterNot { it.isDirectory }.map { it.fsId }
        if (!isRoot && videoIds.isNotEmpty()) {
            playbackPreparing = true
            playbackError = null
            try {
                playbackSources = repository.resolvePlayback(videoIds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                playbackError = error.message ?: "播放地址准备失败"
            } finally {
                playbackPreparing = false
            }
        }
    }
    BackHandler(enabled = !isRoot) {
        currentPath = currentPath.substringBeforeLast('/').ifBlank { MediaPathPolicy.DEFAULT_ROOT_PATH }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = if (isRoot) "YunTV" else currentPath.substringAfterLast('/'),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = currentPath,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !refreshing,
                    onClick = { scope.launch { refresh(force = true) } },
                ) {
                    Text(if (refreshing) "刷新中" else "刷新")
                }
                Button(onClick = onOpenSettings) { Text("设置") }
            }
        }

        Text(
            text = errorMessage ?: when {
                refreshing && snapshot == null -> "正在加载目录…"
                isRoot -> "根目录只展示文件夹；已有缓存不会自动请求网络"
                playbackError != null -> playbackError.orEmpty()
                playbackPreparing -> "正在批量准备播放地址…"
                else -> "显示当前文件夹的直属子文件夹和视频"
            },
            modifier = Modifier.padding(top = 20.dp, bottom = 18.dp),
            color = if (errorMessage == null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )

        val entries = snapshot?.items.orEmpty()
        if (entries.isEmpty() && !refreshing) {
            Text(if (errorMessage == null) "此目录没有可显示的内容" else "保留当前缓存，可稍后重试")
        } else {
            key(currentPath) {
                DirectoryGrid(
                    entries = entries,
                    restoreFocusIndex = focusedIndexByPath[currentPath] ?: 0,
                    coverRepository = coverRepository,
                    playbackSources = playbackSources,
                    onItemFocused = { focusedIndexByPath[currentPath] = it },
                    onDirectory = { currentPath = it.path },
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun DirectoryGrid(
    entries: List<DriveItem>,
    restoreFocusIndex: Int,
    coverRepository: CoverRepository,
    playbackSources: Map<Long, PlaybackSource>,
    onItemFocused: (Int) -> Unit,
    onDirectory: (DriveItem) -> Unit,
    onPlay: (Long) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val restoredItemFocus = remember { FocusRequester() }
    val targetIndex = restoreFocusIndex.coerceIn(0, entries.lastIndex.coerceAtLeast(0))
    LaunchedEffect(entries.map { it.fsId }, targetIndex) {
        if (entries.isNotEmpty()) {
            gridState.scrollToItem(targetIndex)
            withFrameNanos { }
            restoredItemFocus.requestFocus()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(entries, key = { _, item -> "${item.parentPath}:${item.fsId}" }) { index, item ->
            val itemModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(if (index == targetIndex) Modifier.focusRequester(restoredItemFocus) else Modifier)
                .padding(2.dp)
            if (item.isDirectory) {
                FocusTile(
                    title = item.name,
                    subtitle = "文件夹",
                    onClick = { onDirectory(item) },
                    modifier = itemModifier,
                    onFocused = { onItemFocused(index) },
                )
            } else {
                VideoCoverTile(
                    item = item,
                    coverRepository = coverRepository,
                    playbackSource = playbackSources[item.fsId],
                    onClick = { onPlay(item.fsId) },
                    modifier = itemModifier,
                    onFocused = { onItemFocused(index) },
                )
            }
        }
    }
}

@Composable
private fun VideoCoverTile(
    item: DriveItem,
    coverRepository: CoverRepository,
    playbackSource: PlaybackSource?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val cover by remember(item.fsId) {
        coverRepository.observeCover(item.fsId)
    }.collectAsState(initial = null)
    LaunchedEffect(item.fsId, item.modifiedAtEpochSeconds, item.thumbnailUrl, playbackSource?.url) {
        coverRepository.requestCover(item, playbackSource?.url)
    }

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cover-scale")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable(onClick = onClick)
            .background(Color(0xFF182231), shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF3B4452),
                shape = shape,
            )
            .clip(shape),
    ) {
        val localCover = cover?.localPath?.let(::File)
        if (localCover != null) {
            AsyncImage(
                model = localCover,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = "▶  ${item.name.firstOrNull() ?: '视'}",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (playbackSource == null) {
                    "播放地址准备中 · ${formatFileSize(item.sizeBytes)}"
                } else {
                    formatFileSize(item.sizeBytes)
                },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "视频"
}
