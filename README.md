# CinemaWatch

Android 10+ 影院设备巡检应用，支持简体中文、繁体中文与英文；默认跟随系统，其他语言回退简体中文。

- 创建影城时按影厅数预创建区域，也可继续追加。
- 资产可独立创建，再绑定明确授权的 Wi-Fi/BLE 地址；支持多个绑定。
- Wi-Fi/BLE 巡检、谨慎的信号预判分组、RSSI 参考基准与采样质量提示。
- 原生报告预览、分享、CSV 导出及汇总历史导入。
- 0.3 恢复原包名 `com.cinemawatch`，使用固定签名；设置中手动检查更新、下载校验并由 Android 确认覆盖安装。

[下载已发布 APK](https://github.com/hoylturtle/CinemaWatch/releases)；不要安装 Actions 的临时测试候选包。

旧 0.1/0.2 临时签名私钥未保存，因此此次先导出需要的历史，再一次性换装。从固定签名 0.3 起后续覆盖安装保留数据。CSV 导入不恢复授权绑定或校准。

详见 [覆盖升级与签名配置](docs/UPDATES_AND_SIGNING.md)、[中文信号检测逻辑](docs/SIGNAL_DETECTION_LOGIC.md)。发布前必须配置固定签名 secrets，缺少密钥时不发布 APK。

扫描与数据存储在本地；仅手动检查／下载更新时联网访问 GitHub，不上传影院、资产或巡检数据。不把无线标识当人数，不记录未授权原始标识，不进行跨日顾客追踪。

基于 Fieldwatch v1.1.21，固定 source commit `5379a2049c351c2483f7f106ca68d5d6d5f00e64`。保留 MIT License 与 attribution，审计见 [FIELDWATCH_REUSE_REPORT.md](FIELDWATCH_REUSE_REPORT.md)。

JDK 17 / Android SDK 35 / Gradle Wrapper 8.11.1。基本验证：`./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest`。CI 补充同签名旧版本 fixture → 覆盖安装 → 数据及语言保留、资产／报告／更新 UI 流程。现场无线扫描仍需真机验证。
