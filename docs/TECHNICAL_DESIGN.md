# YunTV 技术设计与实施规范

- 状态：已定稿，可直接实施
- 目标平台：Android TV / 常见安卓电视盒子
- 交付形态：单个 APK，本机直接访问百度网盘，不依赖 NAS、电脑、Docker、云服务器或 OpenList

## 1. 目标与已锁定决策

### 1.1 产品目标

YunTV 是供单个用户长期自用的百度网盘电视播放器。首次安装通过电视上的二维码完成百度授权，以后自动登录。应用启动后直接进入配置的媒体根目录，通过遥控器浏览文件夹和视频，使用 Media3 ExoPlayer 直连百度 CDN 播放。

默认媒体根目录：

```text
/apps/视频
```

这个路径必须做成设置项，但首次启动默认使用上述值。用户在该目录根节点按返回键时不得继续进入更上层目录。

### 1.2 已锁定的技术决策

- 新建纯原生 Kotlin Android TV 应用，不 fork AListLiteTV/OpenListApp。
- UI 使用 Jetpack Compose for TV。
- 播放使用 AndroidX Media3 ExoPlayer。
- 网盘访问直接使用百度网盘官方 OAuth 与文件 API。
- 不内嵌 OpenList，不启动 localhost 服务，不使用 WebDAV。
- 第一版只读：不上传、删除、移动、重命名或分享文件。
- 第一版单账号、单百度应用、单电视设备配置。
- 目录与文件元数据长期缓存；默认不后台扫描，不自动刷新目录。
- 默认根目录是导航层，只展示直属文件夹，不获取任何 `dlink`。
- 进入根目录下的具体媒体文件夹后，自动批量获取该文件夹直属全部视频的 `dlink`，方便随机选择播放。
- `dlink` 在本地持久化缓存 8 小时；新播放开始前保留 5 分钟安全余量。
- 视频以 16:9 封面卡片展示；封面下载或生成后持久化缓存到本地。
- `dlink` 实际播放流量不作为文件 API 调用处理。
- 依赖注入使用显式 `AppContainer`，不引入 Hilt/Koin。
- Token 使用 Android Keystore 加密保存。
- 百度 AppKey/SecretKey 通过未提交版本库的本地构建配置注入 APK。
- 项目包名：`com.yuntv.app`；应用显示名：`YunTV`。

### 1.3 实测通过的链路

以下链路已经使用真实百度开发者应用和账号验证：

- 设备码 OAuth 授权成功。
- `/` 可以列出整个网盘。
- `/apps/视频/宠爱时光` 可以列出 37 个视频。
- `filemetas` 可以根据 `fs_id` 返回 `dlink`。
- 使用 `User-Agent: pan.baidu.com` 和 `Range: bytes=0-1023` 请求 `dlink`，百度 CDN 返回 `206 PartialContent`。
- 返回正确的 `Content-Type: video/mp4`。
- 实测总文件 API 请求超过公开文档名义上的 10 次后仍可使用；正式实现仍必须处理频控错误。

## 2. 非目标

以下内容不进入第一版：

- 多网盘、多百度账号或多人档案。
- 外部 OpenList/AList/WebDAV 服务器。
- 影视刮削、联网海报匹配、豆瓣/TMDB 元数据。
- 云端配置同步。
- 文件写操作。
- 向应用商店公开发布。
- 服务端隐藏百度 SecretKey。

以后若公开分发 APK，必须重新评估将 `client_secret` 放入 APK 的风险，并考虑自有后端。私人侧载场景接受 APK 可被逆向提取 SecretKey 的事实，不做无意义的字符串混淆作为安全承诺。

## 3. 工程和依赖基线

### 3.1 Android 基线

| 项目 | 选择 |
|---|---|
| JDK | 17 |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | AGP 内置支持，升级到 2.4.10 |
| KSP | 2.3.6 |
| compileSdk | 37 |
| targetSdk | 37 |
| minSdk | 26 |
| Java/Kotlin JVM target | 17 |
| ABI | 默认通用 APK；发布时额外生成 arm64-v8a 和 armeabi-v7a APK |

如果实际盒子低于 Android 8.0/API 26，再单独评估降级；第一版不为老旧 Android 版本牺牲网络安全、解码和生命周期实现。

### 3.2 主要依赖

锁定稳定版，禁止使用 alpha/beta/rc：

| 依赖 | 版本/策略 | 用途 |
|---|---|---|
| Compose BOM | 2026.09.00 | Compose 统一版本 |
| `androidx.tv:tv-material` | 1.1.0 | TV 组件与焦点样式 |
| Media3 | 1.11.1 | ExoPlayer、UI、OkHttp DataSource |
| Room | 2.8.5 | 目录、文件、播放进度缓存 |
| DataStore Preferences | 1.3.0 | 非敏感设置 |
| Navigation Compose | 2.10.2 | 页面导航 |
| Lifecycle | 2.11.0 | 生命周期与 StateFlow |
| Activity Compose | 1.13.0 | Activity/Compose 集成 |
| OkHttp | 5.5.1 | OAuth 与百度 API 网络层 |
| Kotlin Serialization | 与 Kotlin 版本匹配 | JSON 解析 |
| ZXing Core | 3.5.4 | 授权二维码生成 |
| Coil Compose / Network OkHttp | 3.6.3 | 本地封面与远程缩略图加载 |

Media3 至少包含：

```text
media3-exoplayer
media3-ui
media3-datasource-okhttp
media3-session
```

播放器 UI 使用 `PlayerView` 嵌入 Compose 的 `AndroidView`，上层覆盖自定义 Compose TV 控制条。第一版不使用实验性的纯 Compose Media3 Player UI。

### 3.3 国内镜像

按照仓库 `AGENTS.md`：依赖下载优先使用可信的大陆镜像，失败或校验异常时回退官方源。

`pluginManagement` 和 `dependencyResolutionManagement` 仓库顺序：

1. 阿里云 Google Maven 镜像。
2. 阿里云 Maven Central 镜像。
3. `google()`。
4. `mavenCentral()`。
5. `gradlePluginPortal()`，仅插件解析需要。

Gradle Wrapper 使用腾讯云的 Gradle 9.6.0 分发包，并固定官方 SHA-256；镜像不可用或校验不一致时改用相同版本的 Gradle 官方分发地址。不得关闭 TLS 或依赖校验来迁就镜像。

Gradle 插件固定使用：

```text
com.android.application 9.5.1
com.android.library 9.5.1
AGP built-in Kotlin + kotlin-gradle-plugin 2.4.10
org.jetbrains.kotlin.plugin.compose 2.4.10
org.jetbrains.kotlin.plugin.serialization 2.4.10
com.google.devtools.ksp 2.3.6
```

本机 Android SDK 已安装 API 37，采用官方支持 API 37、Gradle 9.6.0 和 JDK 17 的 AGP 9.5.1。AGP 9 默认启用内置 Kotlin，因此不再应用 `org.jetbrains.kotlin.android`；通过根构建脚本把 AGP 自带的 Kotlin 2.2.10 升级到 2.4.10，Compose Compiler 与 Serialization 使用相同版本，KSP 使用官方 AGP 9 迁移要求的 2.3.6。该组合已经通过本项目 Debug 构建、单元测试和 Android Lint。

## 4. 仓库结构

采用多模块结构：

```text
yuntv/
├─ app/                       Android Application、MainActivity、AppContainer
├─ core/
│  ├─ model/                  领域模型、接口、错误类型
│  ├─ network/                OkHttp、序列化、脱敏日志
│  ├─ database/               Room DB、DAO、migration
│  ├─ security/               Android Keystore、TokenVault
│  └─ ui/                     主题、焦点、通用 TV 组件
├─ data/
│  ├─ baidu/                  OAuth、百度 API DTO、Repository 实现
│  └─ cover/                  封面下载、帧截取、本地持久化与 LRU
├─ feature/
│  ├─ auth/                   二维码授权页
│  ├─ browser/                文件夹/视频浏览
│  ├─ player/                 播放页面与错误恢复
│  └─ settings/               根目录、显示、诊断、退出登录
├─ docs/
├─ tools/                     已有 API 实测脚本
├─ gradle/libs.versions.toml
├─ secrets.properties         本机文件，必须 gitignore
└─ local.properties           Android SDK，本机文件，必须 gitignore
```

依赖方向必须保持：

```text
feature -> core:model/core:ui
data:baidu -> core:model/core:network/core:security/core:database
data:cover -> core:model/core:network/core:database
app -> all feature + data implementations
core 不依赖 feature 或 data
```

## 5. 核心领域接口

`core:model` 定义以下接口；UI 不得直接依赖百度 DTO：

```kotlin
interface CloudDriveRepository {
    fun observeDirectory(path: String): Flow<DirectorySnapshot?>
    suspend fun refreshDirectory(path: String, force: Boolean = false): DirectorySnapshot
    suspend fun resolvePlayback(fsIds: List<Long>): Map<Long, PlaybackSource>
    suspend fun invalidatePlayback(fsId: Long)
}

interface AuthRepository {
    fun observeAuthState(): StateFlow<AuthState>
    suspend fun requestDeviceCode(): DeviceAuthorization
    suspend fun pollDeviceAuthorization(deviceCode: String): TokenBundle
    suspend fun ensureValidAccessToken(): String
    suspend fun logout()
}

interface PlaybackProgressRepository {
    fun observeProgress(fsId: Long): Flow<PlaybackProgress?>
    suspend fun saveProgress(progress: PlaybackProgress)
}

interface CoverRepository {
    fun observeCover(fsId: Long): Flow<LocalCover?>
    suspend fun ensureCovers(items: List<DriveItem>)
    suspend fun invalidateCover(fsId: Long)
    suspend fun trimToSize(maxBytes: Long)
}
```

主要领域模型：

```kotlin
data class DriveItem(
    val fsId: Long,
    val parentPath: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtEpochSeconds: Long?,
    val category: Int?,
    val mimeType: String?,
    val thumbnailUrl: String?
)

data class DirectorySnapshot(
    val path: String,
    val items: List<DriveItem>,
    val fetchedAt: Instant,
    val source: SnapshotSource // CACHE or NETWORK
)

data class PlaybackSource(
    val fsId: Long,
    val url: String,
    val obtainedAt: Instant,
    val expiresAt: Instant,
    val requiredHeaders: Map<String, String>
)

data class LocalCover(
    val fsId: Long,
    val localPath: String,
    val sourceType: CoverSourceType,
    val width: Int,
    val height: Int
)
```

## 6. 百度 OAuth 实现

官方参考：[设备码模式授权](https://pan.baidu.com/union/doc/使用入门/接入授权/设备码模式授权/)。

### 6.1 首次授权

请求设备码：

```http
GET https://openapi.baidu.com/oauth/2.0/device/code
    ?response_type=device_code
    &client_id={APP_KEY}
    &scope=basic,netdisk
User-Agent: pan.baidu.com
```

授权页显示：

- QR URL 生成的二维码。
- `user_code` 文本作为扫码失败的备用方式。
- 剩余有效时间。
- 取消按钮。

按响应 `interval` 轮询，间隔不得低于 5 秒：

```http
GET https://openapi.baidu.com/oauth/2.0/token
    ?grant_type=device_token
    &code={DEVICE_CODE}
    &client_id={APP_KEY}
    &client_secret={SECRET_KEY}
User-Agent: pan.baidu.com
```

处理 `authorization_pending`、`slow_down`、超时和用户拒绝。二维码过期后只显示“重新生成二维码”，不得无限自动重试。

### 6.2 Token 生命周期

- `access_token` 约 30 天。
- `refresh_token` 长期有效，但每次刷新后旧值失效，必须保存响应中的新值。
- Access Token 到期前 24 小时视为需要刷新。
- 刷新操作由进程级 `Mutex` 串行化。
- 新 Access/Refresh Token 必须作为一个完整 `TokenBundle` 原子覆盖旧值。
- 刷新网络失败时保留旧值；明确返回凭证无效时才切换到 `NeedsLogin`。

刷新请求：

```http
GET https://openapi.baidu.com/oauth/2.0/token
    ?grant_type=refresh_token
    &refresh_token={REFRESH_TOKEN}
    &client_id={APP_KEY}
    &client_secret={SECRET_KEY}
```

认证状态机：

```text
Unknown
  -> Authenticated            本地 Token 有效
  -> Refreshing               Access Token 临期
  -> NeedsLogin               无 Token/刷新凭证无效

NeedsLogin -> WaitingForUser -> Authenticated
Refreshing -> Authenticated | NeedsLogin | OfflineWithCachedData
```

离线且存在目录缓存时允许浏览缓存，但点击未缓存 `dlink` 的视频时显示网络错误。

## 7. 百度文件 API

### 7.1 列目录

官方参考：[获取文件列表](https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/获取文件列表/)。

```http
GET https://pan.baidu.com/rest/2.0/xpan/file
    ?method=list
    &dir={URL_ENCODED_PATH}
    &order=name
    &start=0
    &limit=1000
    &web=1
    &folder=0
    &access_token={ACCESS_TOKEN}
User-Agent: pan.baidu.com
```

规则：

- 首次进入未缓存目录时请求网络一次。
- 已缓存目录立即显示缓存，不自动刷新。
- 当 `path == root_path` 时只展示直属文件夹；根目录中的直属视频即使存在也不展示、不解析 `dlink`。
- 当 `path != root_path` 时展示直属子文件夹和直属视频，并进入媒体文件夹的 `dlink` 批处理流程。
- 用户按菜单键或设置中的“刷新”才发起强制刷新。
- 返回正好 1000 条时继续分页，每一页独立计为 API 请求。
- 同一路径同时发生的多个刷新请求必须合并为一个共享协程。
- API 即使 HTTP 200 也可能通过 JSON `errno` 报错，必须先判断 `errno`。
- 成功后用单个 Room transaction 替换该目录快照，不能先删后插导致 UI 闪空。

### 7.2 获取 `dlink`

官方参考：[查询文件信息](https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/查询文件信息/)。

```http
GET https://pan.baidu.com/rest/2.0/xpan/multimedia
    ?method=filemetas
    &fsids=[123,456]
    &dlink=1
    &needmedia=1
    &access_token={ACCESS_TOKEN}
User-Agent: pan.baidu.com
```

规则：

- 一次最多 100 个 `fs_id`。
- 默认根目录 `/apps/视频` 只展示分类文件夹，不调用 `filemetas`。
- 进入默认根目录下的具体媒体文件夹后，在目录数据可用时批量解析该文件夹直属的全部视频；不递归处理子文件夹。
- 先过滤仍在 8 小时缓存期内且距离过期超过 5 分钟的缓存，只请求缺失、已过期或即将过期的视频。
- `fs_id` 超过 100 个时按每批最多 100 个拆分；每批是一次 `filemetas` API 调用。
- 目录有 37 个视频且无有效缓存时，只需要一次 `filemetas` 调用即可获得全部 `dlink`。
- `dlink` 解析在后台进行，不阻塞目录缓存首帧；卡片显示“播放地址准备中”，完成后即可播放。
- 缓存返回的每个 `dlink`，`expiresAt = obtainedAt + 8 hours`。
- 缓存记录保留完整 8 小时；新播放开始时若剩余有效期不足 5 分钟，则后台刷新后再播放，避免播放中立即过期。
- 持久化 `dlink` 允许 App 重启后复用；超过 8 小时必须删除。
- 长期身份只使用 `fs_id`，不把 `dlink` 当永久地址。

### 7.3 播放 URL

将 Access Token 追加到 `dlink`：

```text
{dlink}&access_token={URL_ENCODED_ACCESS_TOKEN}
```

如果原 URL 没有查询串则用 `?`，否则用 `&`。不得重复追加 Token。

所有播放请求必须带：

```http
User-Agent: pan.baidu.com
Range: bytes=...
```

允许 HTTP 302 跳转。OkHttp 的播放专用客户端禁止输出 URL、Token 和响应 Location 到日志。

## 8. 数据库与缓存

Room 数据库名：`yuntv.db`。

### 8.1 表结构

`directory_snapshot`

```text
path TEXT PRIMARY KEY
fetched_at INTEGER NOT NULL
item_count INTEGER NOT NULL
```

`drive_item`

```text
fs_id INTEGER NOT NULL
parent_path TEXT NOT NULL
path TEXT NOT NULL UNIQUE
name TEXT NOT NULL
is_directory INTEGER NOT NULL
size_bytes INTEGER NOT NULL
modified_at INTEGER NULL
category INTEGER NULL
mime_type TEXT NULL
thumbnail_url TEXT NULL
sort_key TEXT NOT NULL
PRIMARY KEY(fs_id, parent_path)
INDEX(parent_path)
```

`dlink_cache`

```text
fs_id INTEGER PRIMARY KEY
url TEXT NOT NULL
obtained_at INTEGER NOT NULL
expires_at INTEGER NOT NULL
```

`cover_cache`

```text
fs_id INTEGER PRIMARY KEY
source_modified_at INTEGER NULL
source_type TEXT NOT NULL
local_path TEXT NOT NULL
width INTEGER NOT NULL
height INTEGER NOT NULL
file_size INTEGER NOT NULL
created_at INTEGER NOT NULL
last_accessed_at INTEGER NOT NULL
```

`playback_progress`

```text
fs_id INTEGER PRIMARY KEY
path TEXT NOT NULL
position_ms INTEGER NOT NULL
duration_ms INTEGER NOT NULL
completed INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

`api_call_log`（仅保留最近 7 天，用于诊断，不用于强制拦截）

```text
id INTEGER PRIMARY KEY AUTOINCREMENT
endpoint TEXT NOT NULL
requested_at INTEGER NOT NULL
errno INTEGER NULL
request_id TEXT NULL
```

### 8.2 设置项

DataStore 保存：

```text
root_path = /apps/视频
display_mode = GRID
grid_columns = 5
sort_mode = NATURAL_NAME
seek_seconds = 10
resolve_all_dlinks_in_folder = true
autoplay_next = false
show_non_video_files = false
cover_cache_max_mb = 512
```

敏感 Token 不得进入 Room/DataStore 明文。

### 8.3 视频封面

浏览页的视频必须使用 16:9 封面卡片，不以通用文件图标作为正常完成态。封面解析顺序固定为：

1. 命中 `cover_cache` 且本地文件存在、`source_modified_at` 与视频修改时间一致：直接显示本地封面。
2. 百度 `list?web=1` 或 `filemetas` 返回可用的 `thumbnail_url/thumbs`：下载图片并持久化到本地。
3. 百度没有缩略图：等待对应视频 `dlink` 可用后，使用 `MediaMetadataRetriever.setDataSource(url, headers)` 尝试截取第 3 秒画面。
4. 远程缩略图与帧截取都失败：显示带文件名首字和播放图标的降级封面，并记录失败时间；24 小时内不重复生成。

本地封面规范：

- 存放目录：`filesDir/covers/`，不能只依赖 Coil 临时磁盘缓存。
- 文件名：`{fsId}_{modifiedAt}.webp`。
- 最大尺寸：640×360，保持 16:9；源图非 16:9 时居中裁剪。
- 格式：WebP lossy，质量 82。
- 生成与下载并发上限为 2，不能阻塞目录首帧或播放器。
- 封面下载/视频帧读取属于 CDN 数据请求，不写入文件 API 调用计数。
- 总容量默认 512 MB；超过后按 `last_accessed_at` 从旧到新清理到 90% 容量。
- 视频 `modified_at` 变化时删除旧封面并重新生成。
- 删除目录缓存时不立即删除封面；由 LRU 统一回收。

Compose 卡片通过 `CoverRepository.observeCover(fsId)` 观察本地路径；图片加载可使用 Coil，但 `cover_cache` 和本地文件才是持久缓存事实来源。

## 9. Token 安全

`core:security` 实现 `TokenVault`：

- Android Keystore 创建 AES-256/GCM 密钥，别名 `yuntv_token_key_v1`。
- 将完整 `TokenBundle` 序列化后一次加密。
- 文件内容包含版本、IV 和密文，使用同目录临时文件 + 原子 rename 写入。
- Token 文件位于应用私有目录，不允许备份到云端；Manifest 设置相应备份规则。
- 退出登录时删除 Token 文件、Keystore alias 和全部 `dlink_cache`，保留或清除目录缓存由用户确认。
- 日志拦截器必须脱敏 `access_token`、`refresh_token`、`client_secret`、`dlink`、`sign`。

`secrets.properties`：

```properties
BAIDU_CLIENT_ID=your_app_key
BAIDU_CLIENT_SECRET=your_secret_key
```

该文件必须被 `.gitignore`。构建时生成 `BuildConfig` 字段。Release APK 本身仍可被逆向获得 SecretKey，这是无后端单 APK 方案的已接受限制。

## 10. 浏览界面与遥控器交互

### 10.1 页面

```text
Splash/AuthGate
  -> DeviceAuthorizationScreen
  -> BrowserScreen(rootPath)
  -> PlayerScreen(fsId)
  -> SettingsScreen
  -> DiagnosticsScreen
```

不设置复杂首页。授权完成后直接进入默认根目录，减少启动 API 请求和遥控器操作。以后可增加多个媒体入口，但不改变 Repository 接口。

### 10.2 BrowserScreen

- 默认 5 列卡片网格。
- `root_path` 页面只显示直属文件夹，使用文件夹卡片；该页面不触发 `filemetas`。
- 进入具体媒体文件夹后显示直属子文件夹与视频；视频使用 16:9 封面卡片，并立即启动该文件夹直属视频的批量 `dlink` 与封面准备任务。
- 文件夹先显示，视频后显示。
- 默认自然排序，确保“第 2 集”排在“第 10 集”前。
- 使用通用自然排序，让文件名中的数字按数值排序；不假定内容是连续剧。
- 非视频默认隐藏；支持扩展名：`mp4,mkv,mov,avi,webm,flv,ts,m2ts,m4v,rmvb`。
- 视频卡片必须显示本地缓存封面；准备期间显示稳定占位骨架，不能因图片加载改变卡片尺寸。
- 视频卡片显示文件名、时长（获取到时）、播放进度和已看完标记。
- 目录缓存存在时首帧只读数据库，不等待网络。
- 空目录、无网络、授权过期和频控必须有不同文案。

遥控器：

| 按键 | 行为 |
|---|---|
| 方向键 | 移动焦点 |
| OK/Enter | 进入文件夹或播放视频 |
| Back | 返回上级；根目录时二次确认退出 |
| Menu | 打开刷新/显示模式/设置菜单 |
| Play/Pause | 在浏览页恢复最近播放；播放器内控制播放 |

离开目录时记录焦点 `fs_id` 和滚动位置，返回时恢复。

### 10.3 手动刷新

刷新菜单必须明确显示：

```text
上次更新：2026-11-19 20:30
刷新此文件夹（将调用一次百度 API）
```

第一版不提供“刷新整个网盘”。

## 11. 播放器实现

### 11.1 Media3 配置

- 使用 `OkHttpDataSource.Factory`。
- 播放专用 OkHttpClient 设置 `followRedirects=true`、`followSslRedirects=true`。
- DataSource User-Agent 固定 `pan.baidu.com`。
- 网络超时：连接 15 秒、读取 30 秒；播放流本身不设置整体 call timeout。
- `DefaultLoadControl` 先使用 Media3 默认值，真机测试后再调缓冲，不在第一版主观优化。
- `AudioAttributes` 使用电影/媒体内容类型，正确处理 TV 音频焦点。
- Activity 保持横屏，播放期间保持屏幕常亮。

### 11.2 播放流程

```text
点击视频
  -> 查有效 dlink 缓存
  -> 无缓存则等待/触发当前文件夹的批量 dlink 解析
  -> 构造带 access_token 的 URL
  -> 从 Room 读取进度
  -> prepare + seekTo(progress)
  -> play
```

进度保存：

- 播放期间每 15 秒保存一次。
- Pause、Stop、Back、进程进入后台时立即保存。
- 剩余小于 2 分钟或观看超过 95% 标记完成。
- 已完成视频再次打开默认从头播放，同时提供“从上次位置继续”。

### 11.3 错误恢复

遇到以下情况时仅自动恢复一次：

- HTTP 401/403。
- 百度 `31360`（链接过期）。
- 播放 URL 跳转失效。

恢复步骤：

```text
记录当前 position
-> 删除该 fs_id 的 dlink 缓存
-> ensureValidAccessToken()
-> 重新 filemetas
-> 替换 MediaItem
-> seekTo(position)
-> prepare/play
```

同一播放会话第二次失败则显示错误页，不得无限循环。错误页提供“重试”“返回目录”“查看诊断”。

### 11.4 播放结束

- 内容按随机选择观看设计，不自动播放下一个文件。
- 播放结束后停留在完成页，提供“重新播放”和“返回文件夹”。
- 返回文件夹时恢复离开前的焦点和滚动位置。

## 12. API 配额策略

官方公开文档对未审核应用写有“10 次/每小时”，但没有说明按 AppId 全局还是按接口计数；实测超过 10 次仍成功。实现不得写死一个未经证实的本地硬限额。

策略：

- 不做后台目录轮询。
- 目录缓存默认永久有效，只有缓存缺失或手动刷新才调用 `list`。
- 启动直接读取默认根目录缓存；不先请求 `/`。
- 进入文件夹时使用 `filemetas` 批量获取该文件夹直属的全部视频；只请求缺失或过期项。
- 记录每次 API 调用，诊断页显示最近一小时按 endpoint 的次数。
- 遇到 `20012`：记录 endpoint 的 `cooldownUntil = now + 60 minutes`，非必要请求停止；用户可手动重试。
- 遇到 `31034`：指数退避 60 秒、120 秒、300 秒，最多 3 次。
- 播放 CDN 请求不写入 `api_call_log`。

## 13. 错误映射

至少映射以下错误：

| 代码/情况 | UI 文案 | 行为 |
|---|---|---|
| `0` | 成功 | 正常处理 |
| `-7` / `20011` | 无权访问此目录 | 返回设置检查根目录 |
| `-9` | 文件夹不存在 | 提示修改根目录 |
| `20012` | 百度接口调用过于频繁 | 进入一小时冷却 |
| `31034` | 当前接口请求过快 | 短时退避 |
| `31045` | 登录凭证失效 | 刷新 Token；失败则扫码 |
| `31326` | 播放请求校验失败 | 检查 UA，不盲目重试 |
| `31360` | 播放链接过期 | 重新解析 dlink 一次 |
| HTTP 401/403 | 播放授权失效 | 刷新 Token/dlink 一次 |
| 无网络 | 当前离线 | 浏览缓存，禁止未缓存播放 |

所有未知错误保留 `errno` 和 `request_id` 到诊断页，但不显示 Token/URL。

## 14. AppContainer 与并发

`YunTvApplication` 创建单例 `AppContainer`：

```kotlin
class AppContainer(application: Application) {
    val database: YunTvDatabase
    val tokenVault: TokenVault
    val authRepository: AuthRepository
    val cloudDriveRepository: CloudDriveRepository
    val coverRepository: CoverRepository
    val playbackProgressRepository: PlaybackProgressRepository
    val settingsRepository: SettingsRepository
    val apiDiagnosticsRepository: ApiDiagnosticsRepository
}
```

并发规则：

- Token 刷新使用一个全局 `Mutex`。
- 每个目录路径使用 single-flight，避免重复 `list`。
- 同一批 `fs_id` 的解析进行去重；缓存命中项不再发请求。
- 封面下载/生成使用应用级 `SupervisorJob`，并发上限 2；同一 `fs_id` 只允许一个任务。
- Repository 只暴露 `suspend`/`Flow`；不得在 UI 层直接创建线程。
- ViewModel 使用 `viewModelScope`，网络/数据库由挂起 API 自行切换调度器。

## 15. 测试计划

### 15.1 单元测试

必须覆盖：

- 中文路径 URL 编码。
- `dlink` 正确追加 Access Token。
- 8 小时缓存、5 分钟播放安全余量和时钟边界。
- 根目录不解析 `dlink`，具体媒体文件夹才触发批量解析。
- 封面命中、失效、远程下载、帧截取降级和 512 MB LRU 清理。
- Refresh Token 轮换及并发刷新只发生一次。
- `第 2 集`、`第 10 集` 自然排序。
- 批量 `fsids` 分组，每组不超过 100。
- 目录分页与 transaction 替换。
- 敏感日志脱敏。
- errno 到领域错误的映射。

### 15.2 网络集成测试

使用 OkHttp MockWebServer：

- 设备码 pending -> success。
- Token refresh 返回新 Refresh Token。
- list 多页。
- filemetas 批量响应。
- 302 -> 206 Range。
- 第一次 403，刷新 dlink 后成功。
- 20012/31034 退避。

CI 不运行真实百度账号测试，不保存任何 Secret。

### 15.3 真机验收

至少在目标电视盒子完成：

1. 首次扫码登录。
2. 杀进程后自动登录。
3. 进入默认根目录并显示缓存。
4. 播放小 MP4。
5. 播放大于 4 GB 的 MOV/MP4。
6. 快进、快退、拖动到视频中后段。
7. 播放 30 分钟无异常。
8. 断网后恢复。
9. 强制让 dlink 失效并验证自动重新解析。
10. 播放结束后返回原文件夹并恢复焦点。
11. 重启盒子后恢复播放进度。
12. 遥控器全流程不需要鼠标或触屏。

## 16. 实施顺序

按以下顺序提交，禁止先做海报墙等非关键功能：

### 阶段 1：工程骨架

- 创建 Gradle 多模块工程、版本目录、国内镜像与官方回退。
- 配置 TV Manifest、Leanback launcher、横屏、banner/icon。
- 建立 AppContainer、主题和基础导航。
- 验收：空壳 APK 可安装并由 TV Launcher 启动，遥控器焦点可见。

### 阶段 2：OAuth

- 实现 secrets 注入、OAuthClient、TokenVault、AuthRepository。
- 实现二维码授权页和刷新状态机。
- 验收：首次扫码成功；杀进程后无需扫码；模拟过期可自动刷新。

### 阶段 3：目录与缓存

- 实现百度 list DTO、Repository、Room schema。
- 实现默认路径浏览、根目录文件夹过滤、自然排序、手动刷新、焦点恢复。
- 实现封面下载/生成、`cover_cache` 和本地 LRU。
- 验收：进入 `/apps/视频` 只显示文件夹且不请求 `dlink`；已缓存目录第二次进入不调用 `list`。

### 阶段 4：播放

- 实现具体媒体文件夹的 filemetas 批量解析、8 小时 dlink 缓存、Media3 播放器。
- 加入 UA、Range、302 和进度保存。
- 验收：真实视频返回 206 并完成播放、拖动和续播。

### 阶段 5：可靠性

- Token/dlink 自动恢复、API 错误映射、离线缓存、诊断页。
- 文件夹内全部 dlink 的分批解析、失效重取和诊断。
- 验收：网络中断、过期链接、频控不会导致崩溃或循环请求。

### 阶段 6：体验完善

- 列表/网格切换、字号、卡片密度、最近播放、已看标记。
- 大文件和不同编码真机测试。
- Release 签名、R8、配置备份说明。

## 17. 第一版完成定义

同时满足以下条件才算完成：

- 单 APK 安装，无任何外部服务依赖。
- 首次扫码后，正常使用不再要求重复扫码。
- 默认直接进入指定根目录。
- 目录缓存命中时启动不调用百度文件 API。
- 能用遥控器完整浏览、播放、暂停、快进、快退和返回。
- 根目录只展示文件夹，不获取 `dlink`。
- 具体媒体文件夹的 `dlink` 批量获取与 8 小时缓存有效。
- 每个视频有 16:9 封面，重新启动应用后可直接使用本地封面缓存。
- 大文件 Range 播放和拖动可用。
- 播放进度可保存；播放结束不自动连播。
- Token/dlink 过期可自动恢复一次。
- 所有 Token、Secret、完整 dlink 不出现在日志或版本库。
- 单元测试、MockWebServer 测试和目标盒子验收全部通过。

## 18. 实施时无需再询问的默认值

- 应用名：YunTV。
- 包名：`com.yuntv.app`。
- 默认根目录：`/apps/视频`。
- 默认显示：5 列网格、文件夹优先、自然排序。
- 默认隐藏非视频。
- 默认进入文件夹后批量获取该文件夹直属的全部视频 dlink，每批最多 100 个。
- 默认快进/快退：10 秒。
- 默认自动下一集：关闭。
- 目录只手动刷新。
- 只读，不实现任何文件修改 API。
- Release 先侧载，不接入应用商店。

## 19. 官方资料

- [百度设备码授权](https://pan.baidu.com/union/doc/使用入门/接入授权/设备码模式授权/)
- [百度获取文件列表](https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/获取文件列表/)
- [百度查询文件信息](https://pan.baidu.com/union/doc/基础网盘服务/获取文件信息/查询文件信息/)
- [百度下载与 dlink](https://pan.baidu.com/union/doc/基础网盘服务/下载/)
- [百度权限与配额](https://pan.baidu.com/union/doc/使用入门/权限与配额/)
- [Compose for TV](https://developer.android.com/training/tv/playback/compose)
- [Compose 焦点](https://developer.android.com/develop/ui/compose/touch-input/focus)
- [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [AGP 9.5 发布说明](https://developer.android.com/build/releases/agp-9-5-0-release-notes)
- [AGP 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
