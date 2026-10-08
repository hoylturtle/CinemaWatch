# 0.2.0 改动与验证范围

- `data/CinemaRepository.kt`、`AssetDialogs.kt`：无扫描创建授权资产；手动／扫描绑定已有资产，多无线绑定，重命名；地址验证、重复提示、失败事务回滚。
- `MainActivity.kt`：批量生成影厅／追加编号，记住区域选择；分组筛选与名称搜索；报告直接预览与区域筛选；导入历史入口；简体／繁体／英文同步。
- `ReportPreview.kt`、`data/ReportExport.kt`、`HistoryImport.kt`：原生报告、文本分享；CSV 增加分类汇总／目标时长；旧版 25 列与新版 27 列汇总导入，限制 2 MB；不重建资产绑定或基准。
- `data/Database.kt`：v2 非破坏迁移；新增分类计数表、导入标记、目标时长。旧版建库 SQL 保存为测试 fixture，用已有资产／绑定／报告验证迁移。
- `radio/ScanEngine.kt`、`InspectionService.kt`：未知 Signature 不重复计算，匹配离开主线程，缓存 Wi-Fi 时间戳并按时间戳去重批次；停止计时独立于 BLE 重试；限时唤醒锁，超时／短时突发不能形成健康判定；绑定变化更新实时标签。
- `domain/SignalGrouping.kt`、`SamplingQuality.kt`、`InspectionPolicy.kt`：广播证据的有限置信分组，未知类别与内存清理；无效／缓存剔除和丢弃区分。上游源码没有改动。
- `app/build.gradle.kts`、资源：版本 0.2.0，独立预览 application ID `com.cinemawatch.preview`，避免临时签名冲突时要求卸载旧版；旧版数据保留原应用中，可导出后导入汇总。
- `.github/workflows/android-preview.yml`：PR 构建／单元测试与硬件加速 Android 模拟器 UI 流程；main 成功后发布 APK、校验值、UI 证据。正式稳定签名尚未配置。

单元测试覆盖：独立创建／多绑定、授权界限、错误回滚、批量区域与编号、CSV 隐私/公式防护/多行解析/去重/旧格式/拒绝损坏文件、v1 迁移、分组不把 Apple 当 iPhone、批次去重、超时和 BLE 突发质量门槛。仪器测试覆盖：创建 2 个影厅、无需扫描创建资产、绑定、重复错误、演示、应用内报告与分组、三语言切换与页面重建保留。

最终执行结果以 GitHub Actions 和发布说明为准，不把未执行或失败的测试描述为通过。现场 Wi-Fi/BLE 硬件行为仍需真机验证。
