# CinemaWatch 开源成熟度审核报告

日期：2026-10-21 UTC；仓库：hoylturtle/CinemaWatch；默认分支：main；审核提交：`208da9b98e884c831f25ece0221515a84b9d9654`；发布基线：v0.9.0-preview。

## 审核结论

**有可运行预览版和可核验工程成果，适合作为早期项目说明申请；尚不适合宣传为成熟、普及、许可齐备、精确定位或自动测绘的开源产品。建议先完成P0整改，再提交正式申请。** 这不是对申请资格的官方裁定，也不保证获批。官方页面允许不完全符合典型条件、但能解释生态价值的项目申请；本项目当前不能声称“广泛使用”。[官方说明](https://developers.openai.com/community/codex-for-oss)。

实际价值在于将固定Fieldwatch基础接到授权影院资产、现场维修闭环、保守采样质量判断和有边界的BLE协作实验，而不在于已证明无线定位精度。

## 范围、方式和证据限制

- 正常Git mirror获取完整可见历史，固定提交归档到独立snapshot目录，未编辑原仓库或远程分支。当前163文件，其中102个Kotlin文件、2个二进制文件；全部文本逐文件结构/边界调用/敏感模式检查，运行路径逐段语义审读。大型上游查表项未逐条人工验证含义；不能把此审计描述为对每条OUI或每种广播的真实性背书。
- 当前生产Kotlin：CinemaWatch 28文件/3368行；复用核心49文件/21354行。逐文件哈希清单在source-inventory.json。51个复用清单项全部与SOURCE_MANIFEST中的Git blob SHA一致；这验证与仓库记录的pin一致，不代表本次重新构建了完整上游或审核了所有第三方数据库权利。
- 扫描69个可见提交、355个唯一历史blob，含2个二进制blob；不包含GitHub已不可见/不可达对象、未推送文件、私有仓库、Actions Secrets值或维护者机器。没有在线试用任何疑似凭据。
- 核验README、全部已取回历史、Release元数据和最新main CI报告；静态检查发布APK包内manifest及文件SHA256。当前环境仅见JDK21，未见项目所需Android SDK，**本次没有重新跑Android构建、模拟器或真机RF测试**。
- Codex Security技能的共享预检资源无法读取，当前也未提供Standard扫描启动工具，因此未启动或宣称完成正式Codex Security Standard扫描。这里提供的是本次成熟度审计内的静态公开内容与边界检查，不是安全认证。没有创建漏洞Issue、上传发现或触发扫描/发布流水线。

## 项目真实功能

详细见[CAPABILITY_MATRIX.md](CAPABILITY_MATRIX.md)。巡检、授权资产、人工基线、异常处理、照片、复检、报告、三语言、平面图手绘和更新均有真实实现。多手机功能具有可执行的局域网实验流程，但仅针对预先授权的BLE标签；没有真实顾客追踪、已校准人数估计或自动户型测绘。

### 采集、识别和位置判断

1. **Wi-Fi**：使用Android WifiManager扫描AP和扫描回调，约30秒请求、配额与退避；adapter额外要求ScanResult单调时间戳位于会话内且不超过15秒。它不是监听客户手机探测帧的monitor-mode采集器。[app/src/main/java/com/cinemawatch/radio/ScanEngine.kt:148](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/radio/ScanEngine.kt#L148)
2. **BLE**：扫描可见广播，BALANCED模式、无主动连接；原始Observation.at是回调时墙钟，不是射频硬件时间戳。单机巡检512项队列、512个临时可见对象、4096个去重键；多机实验先按授权名单过滤，再经256项轻量队列，过载会使窗口不可用于判断。[app/src/main/java/com/cinemawatch/flow/FlowInbox.kt:9](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/flow/FlowInbox.kt#L9)
3. **巡检质量**：完整请求时长且无丢弃；Wi-Fi至少两批新扫描；BLE至少10次事件、覆盖至少半个请求时段且末次接近结束、无失败，才允许据此升级缺失。当前展示的实时“有信号”与最终严格健康门槛不同。[app/src/main/java/com/cinemawatch/domain/SamplingQuality.kt:14](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/domain/SamplingQuality.kt#L14)
4. **设备分类**：名称、OUI/公司号、服务UUID、制造商数据和上游规则；随机/非Public BLE不直接按MAC OUI猜厂商。LOW/MEDIUM并非经数据集校准的概率。同一无线对象可进入设备类与品牌生态多个组，不可将分组计数相加当物理设备数。[app/src/main/java/com/cinemawatch/domain/SignalGrouping.kt:12](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/domain/SignalGrouping.kt#L12)
5. **分类的限制**：adapter对每个Sighting单独调用SignatureEngine；依赖多peer的聚类规则不能据此声称完整有效。已有对象的signature结果被缓存，后续payload变化没有完整重新分类。把Wi-Fi与BLE地址人工绑定到一个资产不等于自动指纹融合。[app/src/main/java/com/cinemawatch/radio/ScanEngine.kt:213](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/radio/ScanEngine.kt#L213)
6. **资产异常**：已确认资产级RSSI基线；弱信号差至少12dB，不稳定为至少5样本且跨度至少18dB；健康窗口连续未见从REVIEW到MISSING。不自动诊断故障原因或恶意设备。多radio读数按资产混合、一个资产仅一个基线，可能混合不同发射功率/频段，需要拆分radio级基线。[app/src/main/java/com/cinemawatch/domain/InspectionPolicy.kt:14](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/domain/InspectionPolicy.kt#L14)
7. **多机区域**：各固定节点配置区域；每轮默认约2秒评估，最近6秒每节点至少3样本中位数加人工补偿，同区取最大值；至少两个区域有证据、最强领先6dB并两次获胜才确认。采集端循环还有网络耗时与BLE重启等待，不是严格的固定频率采样；两个重叠窗口不等于独立统计验证。10秒节点过期、断联清空判断，证据不足显示未知。[app/src/main/java/com/cinemawatch/flow/FlowTracker.kt:43](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/flow/FlowTracker.kt#L43)
8. **网络RTT不是无线测距**：TCP往返时间用于近似校正采集端时钟；Wi-Fi是传输通道，没有调用WifiRttManager，也没有与BLE一起解算位置。会话15分钟、采集端连续断联30秒停止。区域是分类标签，无米制坐标、方位、误差半径或已校准概率。[app/src/main/java/com/cinemawatch/flow/PhoneFlow.kt:63](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/flow/PhoneFlow.kt#L63)
9. **平面图**：手工画墙、房间/门、资产点及导入底图。坐标归一化到图片，自动矩形布局只是排列区域；PDF栅格化不等于CAD理解，CDR仅部分容器预览图。[app/src/main/java/com/cinemawatch/FloorPlan.kt:46](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/FloorPlan.kt#L46) [app/src/main/java/com/cinemawatch/FloorPlan.kt:102](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/FloorPlan.kt#L102)

## 主要成熟度问题（优先级不是CVSS）

|优先级/编号|问题及实际证据|申请前建议与验收|
|---|---|---|
|P0 OSS-01|根目录没有项目级LICENSE；GitHub license=null。已有MIT仅明确归属于Fieldwatch。NOTICE明确IEEE/Bluetooth SIG查表及FastPair数据不在上游原创MIT授权范围。|维护者明确CinemaWatch原创部分许可与权属，保留上游MIT/署名，核对查表分发条款和依赖许可清单；本阶段不代选或更改许可证。|
|P0 REL-01|发布APK的application.debuggable=true；工作流组装app-debug.apk后重签，未构建生产release变体。|将经验证的release变体作为发布候选，验证R8后行为、不可调试、同证书与实际旧APK覆盖升级；不要只更换签名便称生产包。|
|P0 PRIV-01|清除所有影院数据只清Room、phone-flow和plans；maintenance-photos以及capture/reports缓存未在该路径删除，删资产也没有关联文件回收。|统一数据清单/清除事务和孤儿文件回收，测试重启后照片、导出缓存及孤立附件不可再取回；不必为了此项重写历史。|
|P0 DOC-01|早期ARCHITECTURE将人数/热图/票务聚合写成主流程；MVP_ARCHITECTURE还称未有签名/跨设备和禁止合并main；原始审计报告仍保留旧BLOCKED段落。|将历史文档封存并标日期；当前README以能力矩阵和最新证据为准，区分已实现、PoC与规划。|
|P1 PRIV-02|未发现可确认的企业生产数据或有效凭据；上游测试有完整地址/广告样本，部分不是明显占位格式，缺少逐样本出处/授权声明。|维护者确认样本来源；公开示例尽量使用可证为合成的标识及坐标。上游pin修改需单独评审，不应为清理示例悄悄破坏完整性。|
|P1 QUAL-01|缺少真实场地、不同Android厂商/版本、不同日期的盲测；RSSI门槛不等于现场准确率。|公开脱敏基准、设备/环境矩阵、区域混淆、未知比例、切换时延及失败案例。|
|P1 TEST-01|单个API29模拟器；无Jacoco/Kover覆盖率报告；升级fixture由当前源码改versionCode构建，不是实际旧版release APK。|扩展API/厂商矩阵、已发布包升级、schema与release变体测试；不把218项数目写成覆盖率或478项上游原版测试。|
|P1 DATA-01|Room version4、exportSchema=false；CSV仅导入汇总；部分历史CSV附带当前资产备注及当前维修状态，不能当不可变历史快照。|导出schema与正式迁移fixture；明确备份/恢复边界及报告as-of语义，版本化数据格式，必要时快照化。|
|P1 RF-01|单机BLE路径把fresh设为true，部分lastSeen采用处理时间；资产不同radio样本混合；区域启发式无校准。|保留单调采集/接收时间与处理时间，排除积压样本，radio级基线、质量与实体关联分离。|
|P1 SEC-01|LAN共享会话密钥提供加密与认证，但所有持有者能声明任意节点名、区域和新ID；这不等于节点身份认证。|仅用于受控实验；规划节点登记、独立会话凭据、撤销、审计与质量异常检测；不宣传已抵抗授权节点造假。|
|P1 SUP-01|Actions依赖使用可变tag；Wrapper无distributionSha256Sum；未见依赖锁、自动依赖更新、SBOM、贡献/安全政策。|审核后固定action SHA与分发校验，增加依赖/许可管理、SBOM和安全上报；不得把证书指纹误判为私钥。|
|P2 ARCH-01|MainActivity与FloorPlan承担大量UI/状态/IO；PhoneFlow密集分号写法，扫描/时钟/网络耦合；21354行上游含许多未接入工具。|渐进提取会话/协议/推断接口与ViewModel，隔离核心最小API，删减或拆包需保留pin与许可证据。|

对应证据：[third_party/fieldwatch/NOTICE:27](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/third_party/fieldwatch/NOTICE#L27)；[.github/scripts/sign-release.py:23](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/.github/scripts/sign-release.py#L23)；[app/src/main/java/com/cinemawatch/MainActivity.kt:496](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/MainActivity.kt#L496)；[app/src/main/java/com/cinemawatch/MaintenanceUi.kt:38](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/MaintenanceUi.kt#L38)；[docs/ARCHITECTURE.md:1](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/docs/ARCHITECTURE.md#L1)；[app/src/main/java/com/cinemawatch/data/Database.kt:90](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/data/Database.kt#L90)；[app/src/main/java/com/cinemawatch/data/ReportExport.kt:6](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/app/src/main/java/com/cinemawatch/data/ReportExport.kt#L6)；[.github/workflows/android-preview.yml:31](https://github.com/hoylturtle/CinemaWatch/blob/208da9b98e884c831f25ece0221515a84b9d9654/.github/workflows/android-preview.yml#L31)。APK二进制证据见apk-manifest-evidence.json。

## 公开内容与凭据检查结果

对当前全部文本及可见历史blob检查私钥头、常见GitHub/云/Slack令牌、硬编码密码/密钥赋值和可疑密钥/数据库文件名，基础模式未命中；当前树额外检查OpenAI/Google形式密钥、URL内嵌密码也未命中。没有发现被追踪的p12/jks/keystore/.env/数据库文件。两项二进制分别是Wrapper JAR和分配号码查表文件，不是用户巡检库。**这不证明不存在自定义格式秘密、编码秘密或未公开历史。**

签名脚本仅引用Actions Secrets名，公开证书SHA256不是私钥。日志内的changeit是Java信任库常见默认口令语境，不等于Android签名密钥泄漏；工作区路径/代理名属于构建环境元数据，应在面向公众的示例中精简。Git作者中有一个公开个人邮箱身份，本报告不重复其地址；是否保留需作者自行确认，不把公开Git署名当企业凭据。

当前未授权地址/名称会在单机实时观察中短暂存在于有界内存；不是“从不采集标识”。授权绑定允许本地保存地址。汇总导出没有未授权地址字段，但用户自由备注、照片、平面图、标签名仍可能含业务信息。提供公开基准前应另做脱敏。没有证据支持“匿名化已不可逆”或“绝无隐私风险”的表述。

## 可运行性、测试和复用价值

- 最新main工作流38004959930：verify和publish成功；已下载XML/日志确认app 60 + fieldwatch-core 158 = **218**项单元测试，9项Android测试，失败0。完整Fieldwatch原版**478**是另一份历史基线证据，不是当前app新增测试，不能相加为696项当前测试。
- 发布APK：60,969,967字节；SHA256 `bf0c9c8c1f23956585ce0ec83ea7b0db5556e4799d0764d9bc89f4a7b26121a0`；包名com.cinemawatch；versionCode11；minSdk29/target35；allowBackup=false；debuggable=true。Wrapper JAR CRC检查通过；没有用UTF-8接口读取二进制。
- 标准构建要求JDK17、SDK35、Wrapper8.11.1；外部贡献者可编译debug包，不需要发布私钥。签名发布需要维护者配置的两个Secrets。无服务器依赖的巡检可离线运行，更新需GitHub网络，多机需局域网。
- 复用优先：授权资产与维修状态机、保守质量门槛、受控实验的FlowTracker、CSV边界与本地存储；适合抽成模块但目前还不是稳定SDK。原始scanner/parser及目录来自Fieldwatch，不应宣传为CinemaWatch原创。查表资源要另审分发权利。
- 不发布没有测量数据支持的精度、人数、活跃用户、部署影院数、漏洞修复量或社区规模。

## 实际仓库统计

|项目|核验值/口径|
|---|---|
|main可达提交|69（含14个merge），非merge 55|
|所有已获取refs可达提交|69；不是不可达对象数量|
|Stars / Forks|0 / 0|
|Releases|11，全部prerelease；无稳定版|
|Issues|5，全部open；明确排除PR|
|PRs|14，全部closed且main历史为对应合并记录|
|贡献者|GitHub API：1个账号（69贡献）；Git：2个作者身份，未证明两位独立维护者|
|APK下载计数|18次，最新0.9.0为2次；不是独立用户数、活跃用户或真实部署数量|
|最近活动|main最新提交2026-10-09T23:32:50Z；采样日2026-10-21|

下载计数是在本次APK检查下载前采样，本次审计下载本身也可能影响后续计数。统计原始口径与链接在repository-metrics.json、EVIDENCE_INDEX.md；提交和发布次数不是项目影响力或成熟度的替代指标。开放Issue #1–#6中除PR #2以外的5项仍open，部分内容已实现但未做Issue验收关闭；本阶段不改其状态。

## 第一阶段产物与建议决策

- README.zh-CN.draft.md、README.en.draft.md：事实边界明确的中英文草稿；未覆盖仓库README。
- ARCHITECTURE.md、ROADMAP.md、CONTRIBUTING.md：明确标识的建议稿，未写入仓库。
- MULTISIGNAL_FUSION_PROPOSAL.md、DUAL_DEVICE_MAPPING_RESEARCH.md：两项独立后续方案，全部属于建议。
- 指标、源码清单、测试和APK manifest JSON：供复核。完整Git mirror、源代码和APK不放入建议文档压缩包，避免误当新版本。

建议先确认P0整改范围，特别是项目许可、release构建与完整数据清除。此后可另开实施阶段；本阶段没有修改源码/许可证、提交、合并、创建Issue、发布版本或提交申请。等待维护者审核。
