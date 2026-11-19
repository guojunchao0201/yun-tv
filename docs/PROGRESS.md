# YunTV 实施进度

- 最后更新：2026-11-20
- 当前阶段：阶段 2（OAuth 与安全存储）进行中
- 总体状态：工程骨架已验收；OAuth、Keystore 与自动刷新代码已完成，等待真实百度扫码验收
- 技术基线：[TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md)

## 维护规则

本文件只记录实施状态、验收证据、阻塞项和下一步，不重复描述架构。

- 每完成一个可验收任务，当次提交同步更新本文件。
- 状态只使用：`未开始`、`进行中`、`已完成`、`阻塞`。
- 标记“已完成”必须附构建、测试或真机验证证据。
- 技术决策发生变化时先更新 `TECHNICAL_DESIGN.md`，再在本文件“决策变更”中记录原因和提交。
- 不以代码已编写代替验收通过。

## 阶段总览

| 阶段 | 状态 | 完成度 | 验收摘要 |
|---|---|---:|---|
| 0. 需求验证与技术设计 | 已完成 | 100% | 百度 OAuth、目录、dlink、Range 链路实测通过；技术方案定稿 |
| 1. 工程骨架 | 已完成 | 100% | Debug APK 已在 Android 设备安装并冷启动，Activity 前台与横屏验证通过 |
| 2. OAuth 与安全存储 | 进行中 | 80% | OAuth/二维码/Keystore/刷新状态机已实现；待真实扫码与重启验收 |
| 3. 目录、缓存与封面 | 未开始 | 0% | — |
| 4. dlink 与播放器 | 未开始 | 0% | — |
| 5. 可靠性与诊断 | 未开始 | 0% | — |
| 6. 体验完善与发布 | 未开始 | 0% | — |

## 阶段 0：需求验证与技术设计

状态：已完成

- [x] 注册并使用百度网盘开发者应用完成设备码授权。
- [x] 验证根目录 `/` 可访问。
- [x] 验证 `/apps/视频/宠爱时光` 可列出 37 个视频。
- [x] 验证 `filemetas` 根据 `fs_id` 返回 `dlink`。
- [x] 验证 `User-Agent: pan.baidu.com` 与 Range 请求。
- [x] 百度 CDN 返回 `206 PartialContent` 和 `video/mp4`。
- [x] 明确默认根目录为 `/apps/视频`。
- [x] 明确根目录仅展示文件夹，不解析 `dlink`。
- [x] 明确进入具体媒体文件夹后批量获取直属全部视频 `dlink`。
- [x] 明确 `dlink` 持久化缓存 8 小时。
- [x] 明确视频使用 16:9 封面并持久化本地缓存。
- [x] 完成技术设计与实施规范。

## 阶段 1：工程骨架

状态：已完成

- [x] 创建 Gradle 9.6.0 / AGP 9.5.1 多模块工程。
- [x] 配置 AGP 内置 Kotlin、Compose for TV、Media3、Room、KSP 和版本目录。
- [x] 配置国内 Maven 镜像优先、官方源回退，并为 Wrapper 固定 SHA-256。
- [x] 配置 Android TV Manifest、Launcher、横屏、图标与 Banner。
- [x] 创建 `AppContainer`、基础主题、导航和阶段占位页面。
- [x] 添加单元测试与 MockWebServer 测试基础设施。
- [x] Debug APK 构建通过。
- [x] APK 在当前可用 Android 设备安装并可启动；无电视期间使用手机 Debug Launcher 代替基础启动验收。

验收证据：

- `./gradlew :app:assembleDebug :app:lintDebug test --continue` 通过，共执行/检查 509 个任务。
- `aapt2 dump badging` 确认声明 Leanback、无需触屏、`LEANBACK_LAUNCHER`、横屏 Activity。
- 当前 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，20,042,391 bytes，SHA-256 `75E469F77AAE0B9D82496BDB34D5CD053D75B273FF82C5653BABA654B04537D6`。
- 2026-11-20 使用已连接手机冷启动 `com.yuntv.app/.MainActivity` 成功，状态 `ok`，Activity 位于前台；用户确认界面可用。TV Launcher 与遥控器专项验收延后到阶段 6。

## 阶段 2：OAuth 与安全存储

状态：进行中

- [x] 本地构建配置注入 AppKey/SecretKey，敏感文件加入 `.gitignore`。
- [x] 实现设备码、二维码显示和授权轮询。
- [x] 实现 Android Keystore `TokenVault`。
- [x] 实现 Access Token 自动刷新和 Refresh Token 原子轮换。
- [x] 实现认证状态机、离线缓存入口和退出登录。
- [ ] 首次扫码成功，杀进程重开无需扫码。

验收证据：

- OAuth MockWebServer 测试覆盖请求参数、UA、最小轮询间隔、pending、Token 返回和 Refresh Token 轮换。
- 全工程测试、Android Lint 和 Debug APK 构建通过。
- 新 Debug APK 已覆盖安装到手机，冷启动状态 `ok`，前台运行无崩溃；普通 `LAUNCHER` 与 `LEANBACK_LAUNCHER` 均可解析。
- 真实扫码待本机 `secrets.properties` 配置完成后执行。

## 阶段 3：目录、缓存与封面

状态：未开始

- [ ] 实现百度 `list` API、分页和错误映射。
- [ ] 创建 Room 表、DAO 和 migration。
- [ ] 默认进入 `/apps/视频`，只展示直属文件夹。
- [ ] 根目录不调用 `filemetas`。
- [ ] 具体媒体文件夹展示直属子文件夹与视频。
- [ ] 实现目录长期缓存和手动刷新。
- [ ] 实现遥控器焦点与滚动位置恢复。
- [ ] 实现 16:9 视频封面卡片。
- [ ] 实现百度缩略图下载和本地 WebP 缓存。
- [ ] 实现无缩略图时的视频帧截取降级。
- [ ] 实现 512 MB 封面 LRU 清理。

验收证据：待补充。

## 阶段 4：dlink 与播放器

状态：未开始

- [ ] 进入具体媒体文件夹时筛选直属全部视频 `fs_id`。
- [ ] 按每批最多 100 个调用 `filemetas`。
- [ ] 实现 `dlink` 8 小时持久化缓存与 5 分钟安全余量。
- [ ] 实现 Media3 + OkHttp DataSource。
- [ ] 强制 `User-Agent: pan.baidu.com`，支持 302 和 Range。
- [ ] 实现播放、暂停、快进、快退和返回。
- [ ] 实现播放进度保存与恢复。
- [ ] 播放结束不自动连播，返回原文件夹并恢复焦点。
- [ ] 大于 4 GB 视频播放和拖动通过真机测试。

验收证据：待补充。

## 阶段 5：可靠性与诊断

状态：未开始

- [ ] Token 过期自动刷新。
- [ ] dlink 过期/403 自动重新解析一次并恢复原进度。
- [ ] 实现 `20012`、`31034` 等频控退避。
- [ ] 实现断网浏览缓存与恢复联网。
- [ ] 实现 API 调用计数和诊断页。
- [ ] 日志脱敏验证通过。
- [ ] 所有网络错误无无限重试、无崩溃。

验收证据：待补充。

## 阶段 6：体验完善与发布

状态：未开始

- [ ] 网格/列表模式与卡片密度设置。
- [ ] 最近播放、已看标记和重新播放。
- [ ] 多种真实视频编码和字幕测试。
- [ ] 性能、内存、封面缓存和长时间播放测试。
- [ ] Release 签名、R8 和 ABI APK。
- [ ] 完成安装、升级、配置和故障恢复说明。

验收证据：待补充。

## 当前下一步

在本机创建不提交的 `secrets.properties`，重新构建安装，完成首次扫码、杀进程重开和退出登录验收；通过后进入目录与缓存阶段。

## 阻塞项

- 当前没有目标电视，TV Launcher 与遥控器专项验收延后到阶段 6。
- 阶段 2 的真实扫码需要本机 `secrets.properties` 中完整的百度 AppKey/SecretKey；源码和自动化测试未阻塞。

## 决策变更

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-11-20 | 默认根目录确定为 `/apps/视频` | 根目录用于视频分类导航 |
| 2026-11-20 | 根目录不获取 dlink，具体媒体文件夹批量获取直属全部视频 dlink | 内容以随机选择观看为主，无连续剧/下一集假设 |
| 2026-11-20 | dlink 缓存由 7 小时安全 TTL 改为完整 8 小时缓存，播放前保留 5 分钟余量 | 对齐百度链接有效期并最大化 API 调用复用 |
| 2026-11-20 | 增加视频封面持久化缓存 | TV 浏览体验需要可识别的视觉封面 |
| 2026-11-20 | 构建基线调整为 AGP 9.5.1、Gradle 9.6.0、API 37、内置 Kotlin 升级至 2.4.10、KSP 2.3.6 | 与本机 SDK、AGP 官方支持矩阵及 KSP 2.3.x 编译器依赖对齐 |
| 2026-11-20 | Debug 变体增加手机 Launcher，Release 保持 Leanback-only | 当前无电视，允许用手机完成认证、目录和播放联调，不改变正式 TV 交付形态 |
