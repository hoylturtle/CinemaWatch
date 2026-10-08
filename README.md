# CinemaWatch 0.2.0 预览版

Android 10+ 影院设备巡检与本地 RF 采样。界面支持简体中文、繁体中文、英文：默认匹配系统，不支持时回退简体；设置中可手动选择。

[下载 APK](https://github.com/hoylturtle/CinemaWatch/releases/tag/v0.2.0-preview) · [信号检测逻辑（中文）](docs/SIGNAL_DETECTION_LOGIC.md) · [本次改动](docs/V0_2_CHANGES.md)

## 开始使用

1. 创建影城并填写影厅数量，自动生成编号区域；已有影城可在设置中批量增加影厅或单独添加其他区域。
2. 在“资产”直接创建影院拥有或获授权管理的资产，不必先巡检。地址可暂不填写，之后添加 Wi-Fi/BLE 绑定，也可在扫描中绑定到已有资产。
3. 选择区域，开启定位、Wi-Fi、蓝牙并授予扫描权限，进行 2/3 分钟采样。信号按名称、厂商和上游规则初步分组，显示依据与置信；未知就显示未知。
4. 在“记录”点报告卡片或“预览报告”，直接查看采样质量、分组数量和资产状态；可分享文本或导出 CSV。
5. 至少 3 次健康巡检收集 RSSI 基准；后续做相对比较。连续 2 次健康未见才提示缺失；信号不是距离。

## 保留旧版记录

0.1.0 来自一次性调试签名，0.2.0 新预览包与旧版并存，**不要求先卸载旧版**。旧记录仍在旧应用。先在旧版导出 CSV，再到新版“设置 → 导入 CSV 历史”导入，最大 2 MB。导入只恢复汇总报告，不重建资产无线绑定或校准基准，重复自动跳过。原数据库 v1→v2 也提供非破坏迁移。

## 信号与隐私

Wi-Fi 的 AP／热点数与 BLE 地址数不等于物理设备或人数。厂商不是型号，广播名称可伪装；分组不是设备所有权证明。未登记地址、名称和原始载荷只在会话内存使用，结束清除；仅保存区域、时间、分类数量和质量。授权资产使用独立 UUID。不建立跨日顾客身份、不接会员/POS、没有网络权限或云 LLM。人数仍显示待校准。

固定基线：Fieldwatch v1.1.21 / `5379a2049c351c2483f7f106ca68d5d6d5f00e64`。见 [复用审计](FIELDWATCH_REUSE_REPORT.md)、[MIT](third_party/fieldwatch/LICENSE)、[署名](third_party/fieldwatch/NOTICE)与查表条款。

## 构建和验证

JDK 17、SDK 35、Gradle Wrapper 8.11.1：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions 构建、验证关键流程后发布预览包。实际结果见 Actions／Release；现场扫描与厂商省电策略仍需真机验证。预览签名尚不作为稳定生产签名。
