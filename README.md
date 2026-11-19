# YunTV

面向 Android TV 的百度网盘原生播放器。项目采用单 APK、百度官方 API 直连方案，不依赖 NAS、电脑、Docker、OpenList 或其他服务端。

当前处于阶段 2（OAuth 与安全存储）：代码实现已完成，等待使用本地百度应用密钥进行真实扫码与重启验收。

- [技术设计与实施规范](docs/TECHNICAL_DESIGN.md)
- [实施进度](docs/PROGRESS.md)
- 默认媒体根目录：`/apps/视频`
- 进入文件夹后批量获取该文件夹直属全部视频的短期播放地址
- 目录与文件元数据本地缓存，默认仅手动刷新

本机调试前复制 `secrets.properties.example` 为 `secrets.properties`，填入百度开发者应用的 AppKey 和 SecretKey。该文件已被 Git 忽略；没有配置时 Debug APK 会显示配置提示，不会发起授权请求。Debug APK 同时提供手机桌面入口，Release APK 仍只提供 TV Launcher。
