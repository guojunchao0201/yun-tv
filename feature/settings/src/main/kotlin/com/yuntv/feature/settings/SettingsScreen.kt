package com.yuntv.feature.settings

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
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.ui.FocusTile

@Composable
fun SettingsScreen(
    onAuthorize: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "默认根目录：${MediaPathPolicy.DEFAULT_ROOT_PATH}",
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            color = MaterialTheme.colorScheme.secondary,
        )
        FocusTile(
            title = "百度授权",
            subtitle = "打开二维码授权骨架",
            onClick = onAuthorize,
            modifier = Modifier.size(width = 360.dp, height = 140.dp),
        )
        FocusTile(
            title = "返回",
            subtitle = "回到媒体目录",
            onClick = onBack,
            modifier = Modifier
                .padding(top = 18.dp)
                .size(width = 360.dp, height = 140.dp),
        )
    }
}
