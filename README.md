# YunTV

面向 Android TV 的百度网盘原生播放器。项目采用单 APK、百度官方 API 直连方案，不依赖 NAS、电脑、Docker、OpenList 或其他服务端。

当前阶段先完成技术方案，应用代码尚未开始实现。

- [技术设计与实施规范](docs/TECHNICAL_DESIGN.md)
- [实施进度](docs/PROGRESS.md)
- 默认媒体根目录：`/apps/视频`
- 进入文件夹后批量获取该文件夹直属全部视频的短期播放地址
- 目录与文件元数据本地缓存，默认仅手动刷新
