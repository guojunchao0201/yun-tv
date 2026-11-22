package com.yuntv.feature.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.yuntv.core.model.CloudDriveRepository
import com.yuntv.core.model.DriveItem
import com.yuntv.core.model.PlaybackProgress
import com.yuntv.core.model.PlaybackProgressRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Composable
fun PlayerScreen(
    fsId: Long,
    cloudDriveRepository: CloudDriveRepository,
    progressRepository: PlaybackProgressRepository,
    playbackHttpClient: OkHttpClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val player = remember(playbackHttpClient) {
        val dataSourceFactory = OkHttpDataSource.Factory(playbackHttpClient).setUserAgent(USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
            }
    }
    var item by remember(fsId) { mutableStateOf<DriveItem?>(null) }
    var loading by remember(fsId) { mutableStateOf(true) }
    var errorMessage by remember(fsId) { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var retryGeneration by remember { mutableIntStateOf(0) }

    suspend fun saveProgress(forceCompleted: Boolean = false) {
        val currentItem = item ?: return
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)
        progressRepository.saveProgress(
            PlaybackProgress(
                fsId = fsId,
                path = currentItem.path,
                positionMs = position,
                durationMs = duration,
                completed = forceCompleted || isCompleted(position, duration),
                updatedAtEpochSeconds = System.currentTimeMillis() / 1_000L,
            ),
        )
    }

    fun returnToBrowser() {
        player.pause()
        scope.launch {
            saveProgress(completed)
            onBack()
        }
    }

    BackHandler(onBack = ::returnToBrowser)

    LaunchedEffect(fsId, retryGeneration) {
        loading = true
        errorMessage = null
        completed = false
        runCatching {
            val driveItem = checkNotNull(cloudDriveRepository.findItem(fsId)) { "视频已不在目录缓存中" }
            val source = checkNotNull(cloudDriveRepository.resolvePlayback(listOf(fsId))[fsId]) {
                "无法获取播放地址"
            }
            val progress = progressRepository.observeProgress(fsId).first()
            item = driveItem
            player.setMediaItem(MediaItem.fromUri(source.url))
            player.prepare()
            if (progress != null && !progress.completed && progress.positionMs > 0L) {
                player.seekTo(progress.positionMs)
            }
            player.play()
        }.onFailure { error ->
            errorMessage = error.message ?: "视频加载失败"
        }
        loading = false
    }

    LaunchedEffect(player, item) {
        while (isActive) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            saveProgress()
        }
    }

    DisposableEffect(player, lifecycleOwner, hostView) {
        hostView.keepScreenOn = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                if (!value && item != null) scope.launch { saveProgress() }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    completed = true
                    scope.launch { saveProgress(forceCompleted = true) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "播放失败"
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && item != null) scope.launch { saveProgress() }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            hostView.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading || errorMessage != null) {
            PlayerStatus(
                message = if (loading) "正在准备播放…" else errorMessage.orEmpty(),
                showActions = !loading,
                onRetry = { retryGeneration++ },
                onBack = ::returnToBrowser,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (completed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("播放完成", style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        completed = false
                        player.seekTo(0L)
                        player.play()
                    }) { Text("重新播放") }
                    Button(onClick = ::returnToBrowser) { Text("返回目录") }
                }
            }
        }

        if (!loading && errorMessage == null && !completed) {
            PlaybackControls(
                isPlaying = isPlaying,
                onRewind = { player.seekTo((player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)) },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onForward = {
                    val target = player.currentPosition + SEEK_INCREMENT_MS
                    player.seekTo(if (player.duration > 0L) target.coerceAtMost(player.duration) else target)
                },
                onBack = ::returnToBrowser,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PlayerStatus(
    message: String,
    showActions: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.headlineSmall)
        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRetry) { Text("重试") }
                Button(onClick = onBack) { Text("返回目录") }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onRewind) { Text("快退 10 秒") }
        Button(onClick = onPlayPause, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(if (isPlaying) "暂停" else "播放")
        }
        Button(onClick = onForward) { Text("快进 10 秒") }
        Button(onClick = onBack, modifier = Modifier.padding(start = 16.dp)) { Text("返回") }
    }
}

private fun isCompleted(positionMs: Long, durationMs: Long): Boolean = durationMs > 0L &&
    (durationMs - positionMs < COMPLETE_REMAINING_MS || positionMs.toDouble() / durationMs >= COMPLETE_RATIO)

private const val USER_AGENT = "pan.baidu.com"
private const val SEEK_INCREMENT_MS = 10_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 15_000L
private const val COMPLETE_REMAINING_MS = 2 * 60_000L
private const val COMPLETE_RATIO = 0.95
