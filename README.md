# CinemaWatch

[English](README.en.md) · [整改记录](docs/REMEDIATION_20261021.md)

Android 10+ 的本地影院设备巡检预览应用，基于固定版本 Fieldwatch v1.1.21。界面支持简体中文、繁体中文和英文，默认匹配系统，不支持的语言回退简体中文。

当前版本：0.9.0-preview。项目处于早期验证阶段。**多手机功能用于授权BLE测试标签的区域变化实验，尚未证明真实顾客动线、人数估计或准确室内坐标。**

## 可以做什么

- 建立影城、批量影厅及大堂、通道、办公区、放映层。
- 登记明确授权的资产名称、位置、备注和Wi-Fi/BLE绑定；巡检时参考信号、采样质量和人工确认的基线。
- 记录弱信号、不稳定与连续健康采样下的缺失；补充维修备注、照片，复检正常后关闭待办。
- 查看按设备类型与品牌生态分组的实时无线对象。分类是证据线索，不能确认具体型号、归属或恶意行为。
- 原生预览报告、分享/导出CSV；只导入汇总历史，不恢复完整资产库。
- 手绘墙、房间、门及资产点，缩放/平移/横屏；导入图片和PDF页。部分CDR只能使用内嵌PNG预览图。
- 多台固定手机在局域网汇总授权BLE标签的RSSI，记录区域判断、人工位置核验和本地实验报告。
- 手动检查更新，校验包名、版本、SHA-256与签名后由Android确认覆盖安装。

## 尚未实现

自动户型测绘、米级坐标定位、UWB/RTT/视觉惯性融合、经校准的定位置信度、可靠人数模型、真实顾客轨迹、票务/POS接口、跨日个人识别、设备远程控制。不要将已复制的上游工具或prototype目录当作已提供的应用功能。

## 下载与安装

[0.9.0 APK](https://github.com/hoylturtle/CinemaWatch/releases/download/v0.9.0-preview/CinemaWatch-0.9.0.apk) · [发布记录](https://github.com/hoylturtle/CinemaWatch/releases)

0.3起使用固定签名，后续版本支持原位升级；0.1/0.2临时签名不保证可覆盖。不要安装Actions的临时测试候选包。当前公开预览APK仍是重签的debug变体，`debuggable=true`；本次整改改为构建真正的 release 变体，并在发布前检查不可调试标志；未经 CI 验证和维护者合并，不代表新版本已发布。

## 本地构建

要求JDK17、Android SDK35及可用Google/Maven/Gradle网络。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :fieldwatch-core:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest
```

Debug构建无需维护者私钥，使用Android默认debug签名；不要把这个APK当作可覆盖官方签名版本。连接测试设备或模拟器后可运行 `./gradlew :app:connectedDebugAndroidTest`；这不能代替跨版本安装验证。完整旧版本fixture覆盖流程见 `.github/scripts/verify-android.sh`，它需要CI步骤生成的fixture和测试APK。CI发布需要维护者Actions Secrets，贡献者不要复制或索要发布私钥。

## 首次使用与多手机实验

先建立影城和区域，再登记授权资产，开启蓝牙/定位及系统要求的扫描权限。巡检支持120/180秒；演示数据不用于真实基线。手机厂商限制、后台省电和无线覆盖会影响结果。

实验建议3台同型号手机固定在不同区域，同一普通Wi-Fi网络，另携带一个稳定广播地址的授权BLE标签走已知路线；在“设置 → 多手机采集验证”配置协调端与采集端。Wi-Fi只是数据传输，普通Android AP扫描不能监听顾客手机探测帧。详见 [部署及逻辑](docs/PHONE_FLOW_PILOT.md)。

## 数据边界

未授权地址仅用于有界会话内观察与汇总，不写入常规巡检数据库和导出结构；授权资产绑定允许本地保存地址。备注、照片、底图和标签名可能含业务信息，分享前需检查。更新访问GitHub；用户启动实验后在局域网交换授权标签数据。“清除所有影院数据”会停止更新及实验写入，并清理数据库、维修照片、底图、实验报告与相关缓存，保留语言设置；删除资产回收无其他资产引用的照片及对应平面图标记。用户另存到外部的导出文件需自行删除；这不是闪存取证级擦除。

## 验证与已知限制

截至2026-10-21，最新main CI证据为app60项、复用核心158项单元测试和9项Android测试通过。该记录为整改前基线；本次 CI 新增 API29/35 release 验证，结果见对应 PR，未通过前不能计为成功；没有测试覆盖率百分比或真实影院定位精度承诺。[CI证据](https://github.com/hoylturtle/CinemaWatch/actions/runs/38004959930)。

## 来源与许可

Fieldwatch固定提交 `5379a2049c351c2483f7f106ca68d5d6d5f00e64`，保留其 [MIT](third_party/fieldwatch/LICENSE) 和 [NOTICE](third_party/fieldwatch/NOTICE)。IEEE/Bluetooth SIG查表及Fast Pair模型表不在该原创MIT授权范围。**CinemaWatch原创部分尚缺项目级许可证声明，维护者需要明确；本文不新增许可或对第三方数据重新授权。**

本仓库的现有文档中有历史规划，应以版本对应实现和证据为准。当前架构见 [架构说明](docs/ARCHITECTURE.md)，后续工作见 [路线图](ROADMAP.md)，参与方式见 [贡献说明](CONTRIBUTING.md)。请只使用合成/明确授权数据提交公开报告。
