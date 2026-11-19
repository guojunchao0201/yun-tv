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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.ui.FocusTile

@Composable
fun BrowserScreen(
    onOpenSettings: () -> Unit,
    onPlayDemo: () -> Unit,
) {
    var currentPath by rememberSaveable { mutableStateOf(MediaPathPolicy.DEFAULT_ROOT_PATH) }
    val isRoot = MediaPathPolicy.isRoot(currentPath)
    val firstItemFocus = FocusRequester()

    BackHandler(enabled = !isRoot) {
        currentPath = MediaPathPolicy.DEFAULT_ROOT_PATH
    }

    LaunchedEffect(currentPath) {
        firstItemFocus.requestFocus()
    }

    val entries = if (isRoot) {
        listOf("宠爱时光", "家庭录像", "待整理")
    } else {
        List(8) { index -> "演示视频 ${index + 1}" }
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
                    text = if (isRoot) "YunTV" else "视频",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = currentPath,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Button(onClick = onOpenSettings) {
                Text("设置")
            }
        }

        Text(
            text = if (isRoot) {
                "根目录只展示文件夹，不请求播放地址"
            } else {
                "媒体文件夹：将批量准备当前文件夹的视频与封面"
            },
            modifier = Modifier.padding(top = 20.dp, bottom = 18.dp),
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.titleMedium,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(entries) { index, name ->
                FocusTile(
                    title = name,
                    subtitle = if (isRoot) "文件夹" else "16:9 封面占位",
                    onClick = {
                        if (isRoot) {
                            currentPath = "${MediaPathPolicy.DEFAULT_ROOT_PATH}/$name"
                        } else {
                            onPlayDemo()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .padding(2.dp),
                )
            }
        }
    }
}
