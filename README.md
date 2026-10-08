# CinemaWatch 0.1.0 预览版

本地优先的 Android 影院设备巡检与 RF 校准采样应用。支持 Android 10 及以上。

界面支持**简体中文、繁体中文、英文**：首次跟随系统语言；不支持的系统语言回退到简体中文。可在“设置 → 语言”手动选择，选择会保存。Android 13 及以上也可使用系统的应用语言设置。用户填写的影院、区域和设备名称保留原文。

## 使用

1. 安装预览 APK，创建影院和区域。
2. 可先运行 15 秒演示，熟悉界面。演示记录明确标注，不参与设备基准学习。
3. 现场巡检需允许精确定位、附近设备权限，并开启系统定位、Wi-Fi 和蓝牙。选择 2 或 3 分钟采样。
4. 采样期间从可见信号中登记**影院拥有或明确授权管理**的设备。登记使用独立资产 UUID，无线地址仅作匹配线索。采样开始时固定资产快照，新登记设备从下一次巡检开始评估。
5. 至少 3 次有效巡检建立 RSSI 基准；有效扫描连续 2 次未见才提示缺失。信号强弱不表示距离。
6. 在“记录”查看采样质量，导出或分享当前语言的 CSV。设置可以清除本机资料。

Wi-Fi AP 与 BLE 广播地址**不是人数**。RF 校准采样可记录人工/闸机人数作为标签；当前只显示“待校准”，不提供未经校准的人数预测。未登记信号只在当次内存显示，采样结束清除；数据库与导出不保存其 MAC、名称或原始广播。应用没有网络权限，不建立跨日顾客识别。

## 开发与上游

固定上游 Fieldwatch v1.1.21 / `5379a2049c351c2483f7f106ca68d5d6d5f00e64`。原版构建结果见 [复用审计报告](FIELDWATCH_REUSE_REPORT.md)，模块来源与改造说明见 [MVP 架构](docs/MVP_ARCHITECTURE.md)。保留 [MIT License](third_party/fieldwatch/LICENSE)、[署名](third_party/fieldwatch/NOTICE)及查表数据条款。

JDK 17、Android SDK 35、Gradle Wrapper 8.11.1：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。预览包使用调试签名；正式发布前需要生产签名、真机 Wi-Fi/BLE 与 Android 12–15 权限/前台服务验证。模拟器演示验证不能代替影院现场校准。
