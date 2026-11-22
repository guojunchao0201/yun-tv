package com.yuntv.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.model.CloudDriveRepository
import com.yuntv.core.model.DriveItem
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.ui.FocusTile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    repository: CloudDriveRepository,
    onOpenSettings: () -> Unit,
    onPlay: (Long) -> Unit,
) {
    var currentPath by rememberSaveable { mutableStateOf(MediaPathPolicy.DEFAULT_ROOT_PATH) }
    val isRoot = MediaPathPolicy.isRoot(currentPath)
    val firstItemFocus = remember(currentPath) { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snapshot by remember(currentPath) {
        repository.observeDirectory(currentPath)
    }.collectAsState(initial = null)
    var refreshing by remember(currentPath) { mutableStateOf(false) }
    var errorMessage by remember(currentPath) { mutableStateOf<String?>(null) }

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
    LaunchedEffect(currentPath, snapshot?.items?.firstOrNull()?.fsId) {
        if (!snapshot?.items.isNullOrEmpty()) firstItemFocus.requestFocus()
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
            DirectoryGrid(entries, firstItemFocus, onDirectory = { currentPath = it.path }, onPlay = onPlay)
        }
    }
}

@Composable
private fun DirectoryGrid(
    entries: List<DriveItem>,
    firstItemFocus: FocusRequester,
    onDirectory: (DriveItem) -> Unit,
    onPlay: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(entries, key = { _, item -> "${item.parentPath}:${item.fsId}" }) { index, item ->
            FocusTile(
                title = item.name,
                subtitle = if (item.isDirectory) "文件夹" else formatFileSize(item.sizeBytes),
                onClick = { if (item.isDirectory) onDirectory(item) else onPlay(item.fsId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                    .padding(2.dp),
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "视频"
}
