package com.yuntv.app

import android.app.Application
import com.yuntv.core.database.YunTvDatabase
import com.yuntv.core.database.RoomPlaybackProgressRepository
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.security.AndroidTokenVault
import com.yuntv.data.baidu.BaiduOAuthClient
import com.yuntv.data.baidu.BaiduFileClient
import com.yuntv.data.baidu.BaiduPlaybackResolver
import com.yuntv.data.baidu.DefaultCloudDriveRepository
import com.yuntv.data.baidu.DefaultAuthRepository
import com.yuntv.data.baidu.RoomPlaybackCacheStore
import com.yuntv.data.cover.DefaultCoverRepository
import com.yuntv.data.cover.RoomCoverStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(
    val application: Application,
) {
    val defaultRootPath: String = MediaPathPolicy.DEFAULT_ROOT_PATH
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val oauthHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val playbackHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val tokenVault = AndroidTokenVault(application)
    private val oauthClient = BaiduOAuthClient(
        client = oauthHttpClient,
        clientId = BuildConfig.BAIDU_CLIENT_ID,
        clientSecret = BuildConfig.BAIDU_CLIENT_SECRET,
    )

    val authRepository = DefaultAuthRepository(
        oauthClient = oauthClient,
        tokenVault = tokenVault,
        applicationScope = applicationScope,
    )

    private val database = YunTvDatabase.create(application)
    private val fileClient = BaiduFileClient(oauthHttpClient)
    private val playbackResolver = BaiduPlaybackResolver(
        client = fileClient,
        authRepository = authRepository,
        cacheStore = RoomPlaybackCacheStore(database.playbackDao()),
    )
    val cloudDriveRepository = DefaultCloudDriveRepository(
        client = fileClient,
        authRepository = authRepository,
        directoryDao = database.directoryDao(),
        playbackResolver = playbackResolver,
        rootPath = defaultRootPath,
    )
    val playbackProgressRepository = RoomPlaybackProgressRepository(database.playbackDao())
    val coverRepository = DefaultCoverRepository(
        store = RoomCoverStore(database.coverDao()),
        coversDirectory = application.filesDir.resolve("covers"),
        httpClient = oauthHttpClient,
        applicationScope = applicationScope,
    )
}
