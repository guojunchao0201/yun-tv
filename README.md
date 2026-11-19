# YunTV

面向 Android TV 的百度网盘原生播放器。项目采用单 APK、百度官方 API 直连方案，不依赖 NAS、电脑、Docker、OpenList 或其他服务端。

当前处于阶段 1（工程骨架）：Android TV 多模块工程、基础导航、遥控器焦点界面和 Debug APK 已建立，等待目标电视真机启动验收。

- [技术设计与实施规范](docs/TECHNICAL_DESIGN.md)
- [实施进度](docs/PROGRESS.md)
- 默认媒体根目录：`/apps/视频`
- 进入文件夹后批量获取该文件夹直属全部视频的短期播放地址
- 目录与文件元数据本地缓存，默认仅手动刷新
