package com.yuntv.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.model.AuthState
import com.yuntv.core.ui.FocusTile

@Composable
fun AuthorizationScreen(
    state: AuthState,
    onStartAuthorization: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("百度网盘授权", style = MaterialTheme.typography.headlineLarge)

        when (state) {
            AuthState.Initializing -> StatusMessage("正在读取本地登录状态…")
            AuthState.RequestingDeviceCode -> StatusMessage("正在生成授权二维码…")
            AuthState.NeedsLogin -> ActionMessage(
                message = "首次使用需要扫码授权，以后将自动登录。",
                actionTitle = "生成二维码",
                actionSubtitle = "使用百度网盘 App 扫码",
                onAction = onStartAuthorization,
            )
            is AuthState.ConfigurationMissing -> ActionMessage(
                message = state.message,
                actionTitle = "重新检查",
                actionSubtitle = "配置并重新安装 APK 后再试",
                onAction = onStartAuthorization,
            )
            is AuthState.WaitingForUser -> WaitingForAuthorization(state)
            is AuthState.Error -> ActionMessage(
                message = state.message,
                actionTitle = if (state.authorizationExpired) "重新生成二维码" else "重试",
                actionSubtitle = if (state.retryable) "再次发起百度授权" else "检查百度应用配置",
                onAction = onStartAuthorization,
            )
            is AuthState.Refreshing -> StatusMessage("正在刷新百度登录状态…")
            is AuthState.Authenticated -> StatusMessage("授权成功，正在进入媒体目录…")
            is AuthState.OfflineWithCachedData -> StatusMessage("网络暂时不可用，将使用本地缓存。")
        }

        if (state is AuthState.WaitingForUser || state is AuthState.RequestingDeviceCode) {
            FocusTile(
                title = "取消",
                subtitle = "停止本次授权",
                onClick = onCancel,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .size(width = 280.dp, height = 110.dp),
            )
        }
    }
}

@Composable
private fun WaitingForAuthorization(state: AuthState.WaitingForUser) {
    val authorization = state.authorization
    val qrCode = remember(authorization.qrCodeUrl) {
        runCatching { generateQrCode(authorization.qrCodeUrl) }.getOrNull()
    }
    Row(
        modifier = Modifier.padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(Color.White)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qrCode != null) {
                Image(bitmap = qrCode, contentDescription = "百度网盘授权二维码")
            } else {
                Text("二维码生成失败", color = Color.Black)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("请使用百度网盘 App 扫码", style = MaterialTheme.typography.titleLarge)
            Text("授权码：${authorization.userCode}", style = MaterialTheme.typography.titleMedium)
            Text("剩余 ${formatRemaining(state.secondsRemaining)}")
            Text(
                text = "扫码确认后页面会自动进入媒体目录",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(top = 28.dp),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ActionMessage(
    message: String,
    actionTitle: String,
    actionSubtitle: String,
    onAction: () -> Unit,
) {
    StatusMessage(message)
    FocusTile(
        title = actionTitle,
        subtitle = actionSubtitle,
        onClick = onAction,
        modifier = Modifier
            .padding(top = 28.dp)
            .size(width = 380.dp, height = 130.dp),
    )
}

private fun formatRemaining(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
