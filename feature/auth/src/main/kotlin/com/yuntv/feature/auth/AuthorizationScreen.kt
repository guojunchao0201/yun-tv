package com.yuntv.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.ui.FocusTile

@Composable
fun AuthorizationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("百度网盘授权", style = MaterialTheme.typography.headlineLarge)
        Box(
            modifier = Modifier
                .padding(top = 28.dp)
                .size(280.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("二维码将在阶段 2 接入", color = Color.Black)
        }
        FocusTile(
            title = "返回",
            subtitle = "当前为工程骨架",
            onClick = onBack,
            modifier = Modifier
                .padding(top = 28.dp)
                .size(width = 300.dp, height = 130.dp),
        )
    }
}
