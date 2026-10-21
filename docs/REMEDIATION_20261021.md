# 开源成熟度整改记录 — 2026-10-21

审计基线：`208da9b98e884c831f25ece0221515a84b9d9654` / 0.9.0-preview。本文区分代码变更、验证结果和未完成事项；不是新版本发布公告。

|审计问题|本次变更|验收状态|
|---|---|---|
|公开 APK 为 debug 变体|CI 改为 minified release；签名前后检查不可调试、包名和版本；保留固定证书|待本次 CI|
|旧版 fixture 仅修改版本号|下载真实 0.9.0，固定 SHA-256 和证书；临时 CI 重签后验证数据库/设置升级|待 API29/35 CI；不等同真实私钥安装测试|
|清除数据遗漏照片和缓存|统一清理已知私有目录；取消更新/实验写入；保留语言|新增单元/界面测试，待运行|
|删除资产遗留附件|仅删除未共享照片、对应底图标记和导出/拍照缓存|新增共享引用、异常路径和重复清理测试，待运行|
|Actions 可变 tag / Wrapper 缺校验|Actions 固定完整 commit，Wrapper 固定 distributionSha256Sum|静态检查及 CI 验收|
|规划混写为已实现|双语 README、当前架构、贡献指南、路线图；旧架构和上游构建报告标记为历史|文档已校正，不代表未来功能实现|
|原创代码缺许可证|等待维护者明确选择；不改上游 MIT/NOTICE|未完成|

## 验证边界

本地只有 JDK21，没有 Android SDK/Gradle 安装缓存，不能声称本地 Android 构建成功。整改前主分支的历史证据为 app60 + core158 单元测试、9项 API29 Android 测试，见 [历史运行](https://github.com/hoylturtle/CinemaWatch/actions/runs/38004959930)。新增测试不自动计为通过；以本次 CI 产物、日志和结果为准。

Room 与文件系统无法原子提交。附件清理失败时保留数据库记录便于重试，但中途中断可能已删除部分附件。全量清理可重复执行，仅覆盖应用拥有的已知目录；不清理用户另存的外部文件，不承诺取证级擦除。

## 仍需后续处理

- 原创许可决定、依赖/IEEE/Bluetooth SIG/Fast Pair 权利逐项核查、SBOM、依赖锁定与完整公开样例来源说明。不能声称依赖权利已全部审核。
- Room schema 导出、更多历史数据库升级与断电/磁盘异常测试、API31/33 和实体多厂商后台限制验证、覆盖率测量。
- 按无线来源拆分资产基线、可重放带真值数据、区域判断置信度校准、节点身份和撤销机制。
- 多信号融合与双设备测绘是研究提案；没有实现米级坐标、可靠人数或顾客动线。

研究方案见 [多信号融合](MULTISIGNAL_FUSION_PROPOSAL.md) 和 [双设备测绘](DUAL_DEVICE_MAPPING_RESEARCH.md)。本次不改51项 Fieldwatch固定源码/资产，不更换上游，不发版或合并 main。

## 首轮 CI

[运行39688838422](https://github.com/hoylturtle/CinemaWatch/actions/runs/39688838422)：单元测试及真实旧版校验通过，release 应用包构建成功；release instrumentation 的 R8 因 Error Prone 编译期注解引用 `javax.lang.model.element.Modifier` 失败。仅在测试混淆规则中忽略这一 Android 不存在的编译器类型警告，保留 release 混淆及后续界面测试；仍需新一轮 CI 通过。

检查首轮 R8 usage/mapping 还发现测试调用的 updater 注入 setter 和 Kotlin 默认参数 bridge 被优化删除。生产 release 和测试使用同一候选：保留应用公开调用面及跨模块测试使用的 SignatureClass，允许名称混淆，继续裁剪依赖与私有实现。不能只给 CI 构建特殊的“不混淆版本”再发布另一份包。

## Release instrumentation 运行时诊断

运行39692364179中真实旧版 seed 成功，新版安装成功，但 API35 在 AndroidJUnitRunner.onCreate 以 `NoClassDefFoundError: androidx.tracing.Trace` 崩溃，API29 超时。新增的 crash/logcat 采集提供了实际原因。APK 类型引用检查显示还有 Compose/协程共享入口被目标包优化删除；AGP 已从测试包排除了这些重复依赖。生产候选中保留测试框架所需的共享 ABI（Trace、Compose runtime/ui/foundation、核心 Activity/Lifecycle/Core、Kotlin/协程），不保留整套图标库；发布与测试仍使用同一候选。此轮失败不代表旧版安装失败，也不能据此宣称新版运行正常，需下一轮验收。

运行39694459827已通过旧代码升级、报告、三语言、平面图及维修验证；API29后续清除测试未点到确认按钮，API35实验测试未授予Android13+通知权限却断言通知可见。补充稳定的按钮tag/滚动/等待，以及通知断言的权限前提；保留全部功能断言，待下一轮完整矩阵验证。
