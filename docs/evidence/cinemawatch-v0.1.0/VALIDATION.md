# 0.1.0 预览版验证记录

- Wrapper 构建：成功；Temurin JDK 17，SDK 35，Gradle 8.11.1。见 build.log。
- 应用单元测试：12 项通过（6 项策略/临时去重、4 项 Room 事务/持久化/导出隐私、2 项语言资源）。
- 移植的上游 parser/Signature 测试：158 项通过。完整原版 Fieldwatch 的 478 项测试另见上游审计证据。
- 语言资源验证：zh-CN 简体，zh-TW/zh-HK 繁体，en-GB 英文；fr-FR/ja-JP 回退简体。默认跟随系统；AppCompat 自动保存手动选择；设置提供三语言和跟随系统选项。
- 上游文件：51 项 Git blob 哈希与固定 source commit 一致。
- APK：`CinemaWatch-0.1.0-preview.apk`；v2 调试签名验证成功；SHA-256 见 SHA256.txt。Android 10+。

## 未通过的模拟器验证

API 29 x86_64 模拟器，无 KVM，以软件 SwiftShader 运行。APK 与测试 APK 均安装成功。第一次界面测试未能定位影院输入框，截图显示模拟器 System UI 无响应弹窗。清理/重启、降低构建并行、设置模拟器错误弹窗后再试；第二次建立影院后的输入框等待超时，截图出现 framebuffer 渲染异常，UIAutomator 也返回 null root。

因此 **完整设置 → 演示 → 历史 → 三语言切换/重建的 UI 测试未通过**，不能宣称这些交互已被模拟器验证。AndroidRuntime 抓取未见应用崩溃，但不能据此排除界面缺陷。首轮/第二轮日志分别为 instrumentation-first.log 和 instrumentation-final.log。需要在有硬件加速的模拟器或真机重跑。仍应验证 Android 12–15 扫描权限、前台服务和现场扫描节流。

## 交付范围

影院/区域设置、授权资产登记与相对 RSSI 状态、巡检采样、15 秒演示、汇总历史、CSV 分享/导出、三语言、隐私与署名。未提供 RF 人数估计、跨日顾客识别或云服务。预览包没有正式发布签名。

源码已推送 cinema-watch-mvp，未合并 main。当前 GitHub 会话凭证可推送源码和管理 Release 元数据，但对 uploads.github.com 的附件上传返回 401 Bad credentials；空草稿已删除。APK 位于工作区 `/workspace/cinema-build/CinemaWatch-0.1.0-preview.apk`。如需 GitHub Release 附件下载，需要配置能用于 uploads.github.com 的有效 GitHub 凭证及相应仓库写入权限，或由仓库所有者上传 APK。
