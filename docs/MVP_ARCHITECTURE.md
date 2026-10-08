# CinemaWatch MVP 架构与改动范围

## 基线

先完成固定 commit 的未修改 Fieldwatch Wrapper 构建与 478 项测试。`fieldwatch-core` 保留上游 domain 与 Wi-Fi/BLE scanner/parser 原始源码，来源 SHA-1 清单在 `third_party/fieldwatch/SOURCE_MANIFEST.json`；保持 MIT、署名与查表条款。未接入原版 DeviceStore、日志或在线 LLM 功能。

## 新增模块

- 根目录 Gradle、Wrapper 与 `.gitignore`：Android 多模块项目；Wrapper JAR 来自完整上游 Git checkout。
- `fieldwatch-core/`：原始 scanner、Observation、Signature、OUI、RSSI 解析与对应上游测试。
- `app/.../radio/`：原始 radio 回调的 adapter、Wi-Fi 时间戳新鲜度验证、BLE 健康状态、512 项有界队列、最多 512 条临时信号、前台采样服务。观察计数与缓存剔除分开；溢出导致不完整质量标记。
- `app/.../domain/`：会话内地址去重，最多 4096 项；基于健康采样的 RSSI 相对基准与连续缺失状态。
- `app/.../data/`：Room 的影院、区域、独立 UUID 资产、授权 radio binding、汇总会话与授权资产结果。登记/保存使用事务；CSV 只接受这些投影并保留上游公式注入防护。
- `app/.../MainActivity.kt`：影院/区域建立、巡检、资产登记与寻信号、记录与 CSV、隐私/许可/资料清除。原生 Compose，不依赖云服务。
- `app/.../AppLanguage.kt` 与 `res/values*`：简体默认资源、zh-Hans、zh-Hant、英文完整资源；AppCompat 持久化语言选择，系统匹配与 Android 13 应用语言设置；通知、演示与导出同语言。
- `app/src/test`、`app/src/androidTest`：隐私界限、资产事务、数据库重新打开、CSV、巡检质量与语言映射、首次设置/演示/记录流程。

## 数据与局限

授权绑定允许持久化地址，但资产 ID 不是 MAC。未授权原始观察在内存有界存在，采样结束先清除，再保存汇总；保存失败仅保留授权资产样本与汇总，可重试。不同采样不会复用未授权标识。

Wi-Fi 有效批次至少 2 个、BLE 有效事件至少 10 个且无错误，完整时长且无丢弃溢出才用于健康判断；这只是工程质量门槛，不代表影院全覆盖。基准学习至少 3 次健康资产读数；缺失只在连续健康巡检未见时升级。厂商与 signature 仅提供线索，不自动证明归属或安全风险。

第一版未实现人数模型、生产账号权限、跨设备同步、云分析、资产地址更新工作流或正式发布签名。后续应先验证不同 Android 厂商的扫描节流/后台限制、影院分区覆盖与授权设备的 RSSI 基准，再评估人数模型。禁止跨日顾客跟踪，不合并 main。
