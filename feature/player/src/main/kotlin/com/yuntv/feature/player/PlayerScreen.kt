package com.yuntv.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.ui.FocusTile

@Composable
fun PlayerScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("播放器骨架", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Media3、Range 与 dlink 将在阶段 4 接入",
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
        FocusTile(
            title = "返回文件夹",
            subtitle = "恢复原焦点",
            onClick = onBack,
            modifier = Modifier
                .padding(top = 28.dp)
                .size(width = 340.dp, height = 140.dp),
        )
    }
}
