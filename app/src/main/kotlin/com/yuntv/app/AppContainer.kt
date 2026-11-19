package com.yuntv.app

import android.app.Application
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.security.AndroidTokenVault
import com.yuntv.data.baidu.BaiduOAuthClient
import com.yuntv.data.baidu.DefaultAuthRepository
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
}
