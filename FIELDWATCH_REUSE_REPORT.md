# Fieldwatch 复用审计报告 — Issue #1

审计日期：2026-10-07（UTC）。目标分支：`cinema-watch-mvp`。
状态：源码审计已完成；原版 Android 构建尝试被环境阻断，**未证明原版构建成功**。
Issue #1 的“Original upstream builds successfully”验收条件仍未满足；在补齐完整 checkout 和构建证据前，不进入 scanner refactor 或产品层开发，不开始 Issue #3，不合并 main。

## 2026-10-08 更新：原始 Wrapper JAR 已恢复

此节为最新状态；前文 2026-10-07 的缺失与失败记录保留为历史证据。

- 使用 GitHub Connector 的 fetch_file **encoding=base64** 读取固定 commit 中的原始二进制，Base64 解码为字节；没有把 JAR 作为 UTF-8 解码，也没有重新生成/编译 JAR。
- 已安装到 `/workspace/Fieldwatch/gradle/wrapper/gradle-wrapper.jar`。
- 大小：43583 bytes。
- Git blob SHA-1：`a4b76b9530d66f5e68d973ea569d8e19de379189`，与固定 commit 完全一致。
- SHA-256：`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`。
- file/stat/unzip -l 检查正常；Python ZipFile.testzip() 返回 None，存在 org/gradle/wrapper/GradleWrapperMain.class，共 33 个 ZIP entries。
- 首次恢复后 Wrapper 能启动，但默认 /home/agent/.gradle 不在可写路径，无法创建 distribution lock。
- 将 GRADLE_USER_HOME 指向 /workspace/.gradle-fieldwatch 后重试 assembleDebug，已进入 Gradle 8.11.1 distribution 下载阶段。显式设置 JVM 使用环境 proxy:8080 后重试仍失败：
  `java.net.SocketException: Operation not permitted`，exit 1。
- proxy 可解析为 172.31.9.61；Python socket 检查也返回 Operation not permitted。因此无法仅凭 curl 的“Could not connect to server”认定代理服务未启动，当前已实证执行沙箱阻止网络 socket。
- 当前复现命令：

```sh
cd /workspace/Fieldwatch
GRADLE_USER_HOME=/workspace/.gradle-fieldwatch bash ./gradlew \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  assembleDebug --stacktrace
```

JAR 缺失问题已解决；当前构建阻断转为执行网络权限、未下载 Gradle distribution、未配置 SDK，且 radiodb.bin / launcher PNG 尚未恢复。仍无源码编译或测试成功证据，不开始产品改造。JAR 只恢复在独立上游本地审计目录；CinemaWatch 本次仅更新本报告。

## 1. 唯一上游基线与审计范围

- 上游：https://github.com/OffGridPete/Fieldwatch
- Release：`v1.1.21`；版本配置：versionName 1.1.21 / versionCode 31。
- Annotated tag object：`8f2ad40e1be3d27b5337ebb14742ad0f69b21ca9`。
- Source commit：`5379a2049c351c2483f7f106ca68d5d6d5f00e64`。
- 通过 GitHub tag API 验证上述 tag 的 object.type=commit、object.sha 等于指定 SHA；tag 未签名，不将其当作签名验证。
- 所有审计源码读取均显式指定该 commit，不使用上游 main 的最新内容。
- 已读取目标分支 AGENTS.md、docs/UPSTREAM_PIN.md、ARCHITECTURE.md、PRODUCT_BOUNDARY.md、LOCAL_MIGRATION.md 和 Issue #1。
- 获取固定 commit 的完整递归文件清单（API truncated=false），并通过连接器获取 156 个文本文件用于本地审计，含生产 Kotlin、测试、Manifest、构建脚本、LICENSE、NOTICE。本地逐文件按 Git blob SHA-1 校验；恢复文本获取引入的额外末尾换行及 gradlew.bat 的 CRLF 后，156 个文本文件均与上游 blob SHA 相符。
- 本地 `/workspace/Fieldwatch` 是**文本审计快照，不是完整 Git checkout**。17 个二进制文件未被该获取方式包含：Wrapper JAR、radiodb.bin、15 个 launcher PNG。dist/docs 二进制发行物也未作为本地构建证据。
- 没有修改上游业务逻辑、包名、扫描器、依赖版本、Manifest 或产品原型。文本字节恢复只用于匹配上游文件，不是源码修复。

下文路径除 prototype 外均相对于固定版本上游。源码证据统一可用以下前缀查看：
https://github.com/OffGridPete/Fieldwatch/blob/5379a2049c351c2483f7f106ca68d5d6d5f00e64/

## 2. 原版 Android 构建结果及失败证据

### 2.1 固定版本工具链

| 项目 | 上游要求 / 当前实测 |
| --- | --- |
| Gradle Wrapper | gradle-8.11.1-bin.zip；networkTimeout=10000 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin / Compose / serialization plugin | 2.0.21 |
| Java 编译目标 | Java/Kotlin JVM 17；上游 CI 使用 Temurin 17 |
| Android SDK | minSdk 29、compileSdk 35、targetSdk 35 |
| 上游 CI SDK 包 | platforms;android-35、build-tools;35.0.0 |
| 本地 Java | OpenJDK 21.0.12.1 |
| 本地系统 Gradle | `gradle --version`：command not found |
| 本地 SDK | ANDROID_HOME、ANDROID_SDK_ROOT 均为空；指定目录搜索未发现 sdkmanager/android.jar |

证据：build.gradle.kts、app/build.gradle.kts、gradle/wrapper/gradle-wrapper.properties、.github/workflows/android.yml。当前失败不是 Java 编译错误；不能仅因 JDK 21 与 CI 17 不同就归因于 JDK。

### 2.2 获取路径与本地 JAR 检查

正常二进制获取已尝试：

```sh
git clone --no-checkout https://github.com/OffGridPete/Fieldwatch.git /workspace/Fieldwatch-original
curl --fail --location --connect-timeout 10 --max-time 20 \
  --output /tmp/fieldwatch-pinned.tar.gz \
  https://codeload.github.com/OffGridPete/Fieldwatch/tar.gz/5379a2049c351c2483f7f106ca68d5d6d5f00e64
```

结果：git exit 128、curl exit 7，均报：
`Failed to connect to proxy port 8080 ... Could not connect to server`。
环境 HTTP 策略快照为 unrestricted，VPN 未配置；实际故障是代理连接不可用，未证实是 GitHub 授权或目标域名限制。GitHub Connector 可访问仓库，但其可用性不等于 shell 网络可用性。未绕过代理、未禁用 TLS 验证。

按要求在 /workspace/Fieldwatch 检查：

| 命令 | 实测结果 |
| --- | --- |
| ls -lah gradle/wrapper/ | 只有 gradle-wrapper.properties |
| file gradle/wrapper/gradle-wrapper.jar | cannot open ... No such file or directory |
| stat gradle/wrapper/gradle-wrapper.jar | cannot statx ... No such file or directory |
| unzip -l gradle/wrapper/gradle-wrapper.jar \| head -50 | cannot find or open |
| cat gradle/wrapper/gradle-wrapper.properties | distributionUrl 指向 Gradle 8.11.1 |

GitHub 固定树中 JAR 存在，size=43583、blob SHA=`a4b76b9530d66f5e68d973ea569d8e19de379189`。本地缺失是文本快照遗漏二进制。此前 Connector 的 UnicodeDecodeError 仅证明该接口不能将二进制作为 UTF-8 返回，**不证明 JAR 损坏**；已停止该读取方式，没有通过文本接口重建 JAR。

### 2.3 构建与测试实际尝试

在源码逻辑未改动的文本快照执行：

```sh
cd /workspace/Fieldwatch
bash ./gradlew assembleDebug --stacktrace
bash ./gradlew testDebugUnitTest --stacktrace
```

两个命令均 exit 1：

```text
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
Caused by: java.lang.ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain
```

结论：Wrapper 启动失败；Gradle 配置、依赖解析、Kotlin 编译、R8、资源打包和单元测试均未执行。无本地 APK、测试通过数或成功构建可报告。也未执行 fallback build，因为系统未安装兼容 Gradle。上游 dist APK 或 CI 工作流定义不能代替本次本地构建成功证据。

### 2.4 环境恢复后的原版复现步骤（尚未执行成功）

1. 修复 Cloud 环境继承代理的可连接性；支持 GitHub/codeload、Gradle distribution、Google Maven、Maven Central、Android SDK 下载及其实际重定向域名。
2. 用正常 Git clone 到新的空目录，checkout --detach 指定 SHA；验证 git rev-parse HEAD、git status --porcelain，验证 v1.1.21^{commit}。
3. 验证 JAR、radiodb.bin、PNG 等全部二进制存在；可用 git hash-object 对照固定树，不使用 UTF-8 Connector 还原。
4. 安装 CI 同款 JDK 17，Android SDK platform 35、build-tools 35.0.0，接受 SDK licenses，配置 ANDROID_HOME。不修改受版本控制的上游构建文件。
5. 使用 `./gradlew testDebugUnitTest --stacktrace`、`./gradlew assembleDebug --stacktrace`，保留完整命令、退出码、日志、XML/HTML 测试报告和 APK SHA-256，最终确认源码无改动。
6. 如 Wrapper 获取确实受阻但完整源码/资源可取得，且系统存在 Gradle 8.11.1，可用 `gradle ...` 验证；报告必须明确标记 **fallback build**，不可称 Wrapper build。
7. 未成功前保持 Issue #1 构建验收未完成。debug 配置本身启用 minify/shrink、isDebuggable=false，不为“方便构建”改掉这些原版设置；debug 不要求 release signing secrets。

## 3. Wi-Fi Scanner

证据：radio/WifiRadio.kt、WifiIeParser.kt、ScanService.kt。

- 使用 WifiManager.startScan()/scanResults、SCAN_RESULTS_AVAILABLE_ACTION，以及 API 30+ ScanResultsCallback。观察 BSSID/SSID、RSSI、频率、频道、安全能力和 IE；这是 Android AP 扫描，**不是监听顾客手机 probe request，也不是 Wi-Fi 客户端枚举或 monitor mode**。
- start() 首先发出缓存结果（fresh=false），再请求扫描。回调/广播/轮询统一 emitResults；350ms 内抑制重复发出。
- 应用配额为 120 秒窗口最多 4 次成功 startScan，常规请求最少 28 秒；服务 PERFORMANCE/BALANCED/SAVER 间隔分别 30/40/55 秒。失败指数退避 8/16/32/45 秒。
- fast 模式 8 秒间隔仅在用户设置启用且 API 30+ OS scan throttle 实际关闭时使用；不能绕过 Android 系统或 OEM 限制。后台配额、位置开关、权限、休眠和厂商行为仍需真机验证。
- fresh 是启发式：broadcast updated 或 awaitingScan、callback/poll 的 awaitingScan 可使结果标记 fresh；**未用 ScanResult.timestamp 验证逐条扫描时间**。发出 at 是接收时墙钟时间。缓存、部分结果或并发通知可能被误当新观测，Adapter 应保留此不确定性。
- 对已存在且 fresh=false 的 Wi-Fi，DeviceStore 只更新名称/RSSI/facts，不增加 hitCount 或 lastSeen；首次出现的缓存 BSSID 仍可创建 Sighting。这不是“缓存已全部剔除”的保证。
- WifiIeParser.parseIes 可独立测解析：supported rates、DS channel、RSN/WPA、vendor IE（最多 12，单载荷最多 200 字节）；Android IE 提取仅 API 30+，更低版本退化至 capabilities。
- 静态风险：channelOf 的 `freq in 2412..2484` 排在 `freq == 2484` 前面，使 2484 的 channel 14 分支不可达，计算会给出 15（有 DS channel 时可能覆盖）。本次仅记录，不修改上游。

复用建议：保留 Android radio 壳和纯 IE 解码，通过批次/健康状态 Adapter 输出；不得把 AP 数作为在场顾客人数。

## 4. BLE Scanner

证据：radio/BleRadio.kt、BleAdParser.kt、domain/RadioFacts.kt。

- BluetoothLeScanner，空 ScanFilter 对象组成列表匹配所有广播；注释说明这是屏幕关闭时 Samsung 的兼容措施，不代表保证所有 OEM 均有效。
- LOW_LATENCY/BALANCED/LOW_POWER 对应扫描强度；ALL_MATCHES、AGGRESSIVE、reportDelay=0；支持非 legacy 广播和全部支持 PHY。不连接广播设备，不主动读取 GATT 服务。
- advertised name 来自 AD localName / ScanRecord.deviceName，刻意不读配对缓存 getName()。UUID 合并 serviceUuids、serviceData keys 和原始 AD 解析结果。
- manufacturerId/dataHex 只取首个 mfg record 作为便捷字段；RadioFacts.mfgRecords 才保留多个。Adapter 不应只依赖该首条字段。
- BLE rawHex 截断至 1024 个 hex 字符；frequencyMhz 固定为 2402、channel=0，是实现占位值，**不能宣称实际广播频道或实测频率**。
- Observation.at 为当前墙钟接收时间，未使用 ScanResult.timestampNanos。重启/重复广播/地址轮换会影响数量与时间推断。
- 失败回调和 start 异常有 4 秒起、最多 30 秒退避，demoted 后 PERFORMANCE 退为 BALANCED；Bluetooth off/unavailable 下 8 秒重试。
- PERFORMANCE 最长 70 秒、BALANCED 180 秒、SAVER 20 分钟周期回收；demoted 150 秒。静默超过阈值会休息并重启，但静默也可能是环境本来没有广播，不能确定 OS suspension。
- API 35 才调用 BluetoothDevice.getAddressType；较低版本返回 null。MacUtil 的本地管理位标记不是设备身份或随机地址类型的充分证明。
- BLE 广播回调数、可观察地址数、实体设备数、人头数是不同量；屏幕关闭、扫描注册频率限制、功耗、拥堵、地址轮换和不广播设备都造成偏差。

复用建议：BleAdParser 的字节解码逻辑、事实模型和现有测试可复用；Android 入口、强度及重试策略需扫描状态 Adapter 和真机验证。

## 5. Observation 与服务/存储管线

证据：domain/Models.kt（Observation/Sighting）、RadioFacts.kt、data/DeviceStore.kt、radio/ScanService.kt、FieldwatchApp.kt。

原始管线：
`WifiRadio/BleRadio -> Observation -> Channel -> DeviceStore.ingestBatch -> Sighting -> signature / sit / log / UI / alert / TAK`。

- Observation：kind、mac、name、rssi、channel、frequencyMhz、hiddenSsid、serviceUuids、manufacturerId/dataHex、rawHex、extras、at、可选 latitude/longitude、fresh（默认 true）、vendorIeOuis、facts。
- RadioFacts：txPowerDbm、AD flags/appearance/addressType/interval、connectable/PHY/deviceClass、Wi-Fi standard/width/center frequencies/capabilities/rates/security、多条 mfg/service/vendor IE。
- Sighting：以 `kind:normalizedMAC` 为 key，保存 first/lastSeen、hitCount、rssiMin/Max、40 个 RSSI 历史、presence spans、fleetIds、GPS trail 和合并后的载荷/facts。它是有状态“可观察地址”的会话汇总，不是无标识聚合或真实设备身份。
- DeviceStore 稳态目标 400 条、hard ceiling 900 条；过期/匿名淘汰参数分别 15/3 分钟。容量及淘汰会影响全量统计。
- ScanService 的 Channel 容量 512、DROP_OLDEST，drain 批量最多 80；拥堵会丢样且当前边界没有完整丢弃计数。BLE 启动错峰 500ms，循环约 2 秒。
- tagLocation 启用时给 Observation 附加手机定位；这是探针位置，不能当作广播设备位置，更不能用于自动判定影厅或资产精确位置。
- 服务强耦合 FieldwatchApp、DeviceStore、ConfigStore、sit/flood、日志、告警和 TAK；不能完整接入后再依赖下游隐私过滤。

## 6. Signature / OUI / RSSI

### Signature 与 OUI

证据：SignatureEngine.kt、Models.kt（Fleet/MatchRule/MacUtil）、RadioDb.kt、OuiLookup.kt、DefaultCatalog.kt、SignatureExchange.kt、SignatureFieldDecoder.kt、NOTICE。

- Fleet/MatchRule 提供 any/all 规则；覆盖 OUI/MAC_PREFIX、NAME_CONTAINS/GLOB、SERVICE_UUID/DATA、MANUFACTURER_ID/DATA、RADIO_KIND、HIDDEN_SSID、VENDOR_IE_OUI。
- 引擎以 OUI/radio 索引编译规则，使用 Sighting.facts 的多载荷；有 minPeers/peerWindowSec、clusterByOui、sequentialMac 群集规则。结果为每个 key 的 fleet ID 集合，**不是概率化置信度**。
- 有 iBeacon 协议与产品标签去重、Apple/AirTag、DJI、Meraki 等特判；说明通用协议/制造商并不能充分证明具体产品型号。
- Wi-Fi vendor IE 匹配排除协议 OUI 00:50:F2 / 00:0F:AC，并尝试清除本地管理位恢复虚拟 BSSID OUI。这是厂商启发式，不是授权资产身份确认。
- RadioDb 从离线 radiodb.bin 加载 IEEE MA-S/MA-M/MA-L（36/28/24 位优先顺序）及 CID/Bluetooth assigned numbers；未 ready 时返回 null。当前本地缺失 binary，未验证运行时查询。
- RadioDb.vendorForMac 的随机地址分支尝试 wifiOui24Universal，但接口没有 radio kind，可能对 BLE 的本地地址也做 Wi-Fi 启发式；Adapter 要携带 radio kind，并将该厂商推断视为不确定。
- SignatureEngine.match 接收 DetectionPolicy，但当前函数体未使用该参数；不能假定调用该接口即执行 RSSI/置信度过滤。
- 集合引用缓存和规则/配置耦合需要保持不可变更新习惯。目录同步与配置下载不是 MVP 必需；本地固定目录优先，不依赖网络或云 LLM。

### RSSI

证据：Rssi.kt、Models.kt（averageRssi 等）、DeviceStore.kt、RadioFacts.kt。

- Rssi.measured 有效区间 -127..126，127 表示 unavailable；无效值不追加历史。初始 Sighting.rssi/min/max 仍可包含无效原值，不能只看非空 Int。
- RSSI 是接收端 dBm；txPowerDbm 是不同字段，不能混用。sessionRange / lastMeasured 和短窗平均可复用，但要明确样本数、窗口、来源、新鲜度和缺失。
- 测量受手机型号、握持、人体遮挡、墙壁、多径、信道、发射功率、扫描强度影响。未经每影厅/探针校准不能将 RSSI 直接转为距离或“Moved”，也不能以零填充缺失。
- Cinema prototype 当前 rssi:Int 无缺失表达，建议 Adapter 丢弃无效强度并输出质量统计，或后续评审可空 RSSI；本次不改 schema。

## 7. 权限、后台运行与扫描限制

证据：AndroidManifest.xml、radio/Permissions.kt、ScanService.kt、FieldwatchApp.kt。

- minSdk 29；需要 Wi-Fi、Bluetooth、BLE 硬件，location hardware 可选。
- ACCESS_FINE_LOCATION/COARSE_LOCATION；API 31+ BLUETOOTH_SCAN/CONNECT；<=30 BLUETOOTH/ADMIN；API 33+ NEARBY_WIFI_DEVICES、POST_NOTIFICATIONS。
- Manifest 另含 WIFI_STATE/CHANGE_WIFI_STATE、INTERNET/NETWORK_STATE、FOREGROUND_SERVICE（location|connectedDevice 对应权限）、WAKE_LOCK、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
- RadioPermissions.granted 把所有 required 权限统一检查，包括通知；之后若调整产品权限流，应区分通知能力、扫描权限和 location settings，并测试权限撤回，不假定 Nearby Wi-Fi 取代 AP 扫描定位权限。
- BLE scan 未声明 neverForLocation；不要随意添加该标记改变广播可见性。
- 前台服务有通知、stopWithTask=true、START_NOT_STICKY；不能承诺杀进程后持续扫描。Android 12+ 启动限制、Android 14+ location 前台权限/启动条件、屏幕关闭、OEM 电池策略均需真机覆盖。
- 普通 Android AP 扫描没有 client census；BLE 没有“所有手机必须广播”保证。控制循环继续运行不代表 radio 实际提供新样本。扫描断流必须报告 unavailable/low confidence，不能报告 occupancy=0。

## 8. 日志与导出

证据：data/LogStore.kt、domain/LogReplay.kt、GeoExport.kt、CsvCells.kt、SitExport.kt、Models.kt（Settings.demoMode）。

- 内部写入实际固定 JSONL：configure 忽略 format 参数并设 LogFormat.JSON；filesDir/logs/fieldwatch-%03d.jsonl，默认阈值 1 MiB，最低配置 64 KiB，循环 12 个分片，16 行 flush。GeoExport 的“Disk is always CSV”注释与 LogStore 实现不符，应以实现为准。
- append 接收 Sighting；ScanService 仅记录 hitCount<=1 或每 25 次 hit、每批最多 16 条，不是完整原始观察事件日志。重复 Sighting、丢样、轮转都限制其统计用途。
- JSONL 字段：ts/iso/kind/mac/name/rssi/channel/freq/oui/vendor/fleets/mfg/uuids/raw/vendor_ie/rand/hidden/lat/lon；raw 截断至 160 hex 字符，uuid 以字符串序列化。不能通过日志还原所有 RadioFacts。
- CSV 导出字段：timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie。
- 支持分片合并、radio 过滤、CSV/JSONL 转换、URI/文件导出、LogReplay 汇总/重新匹配。导出会暂停日志，造成观测日志间隙。CsvCells 有公式注入保护，可复用转义机制；仍需对最终 schema 验证。
- GPX/KML/WiGLE 导出手机听到的位置，可含 MAC/名称/自定义注释和 operator path；WiGLE 的 auth/altitude/accuracy 有简化占位值，不能当作精确原始测量。
- sit/report/PDF、watchlist、TAK 出口需单独检查脱敏，默认不得接入 Cinema Flow。
- Settings.demoMode 文档明确：屏幕/sit-report MAC tail/GPS mask，**不改变 logs 或 matching**。日志 loggingEnabled 默认 true。现成隐私模式不符合“默认不持久化 raw customer identifiers”；只屏蔽显示、只 hash 固定 MAC、或删除导出字段均不足够。

## 9. 可复用模块与 Adapter 边界（设计建议，未实现）

| 模块 | 复用判断 | 接口 / 必要隔离 |
| --- | --- | --- |
| BleAdParser.parse、WifiIeParser.parseIes | 可直接复用解析核心及现有测试样本 | Android result/facts 包装仍需 API gate；保留依赖模型 |
| RadioFacts、Rssi、CsvCells、UUID utilities | 可复用数据/纯逻辑 | schema 裁剪、无效 RSSI、隐私字段白名单 |
| WifiRadio、BleRadio | 复用 Android 采集核心 | 生命周期、权限、批次新鲜度、scan health、错误/丢样输出 |
| SignatureEngine/Fleet/MatchRule | 复用分类核心，不能直接等同资产解析 | catalog 固定版本、集合依赖、候选多标签/不确定性、radio-aware OUI |
| RadioDb/OuiLookup | 条件复用 | 完整 binary 初始化、数据来源条款、失败时 null 状态 |
| DeviceStore | 可参考缓冲/合并/RSSI 逻辑 | MAC-key、历史/位置/容量策略仅受控资产或短期内存；非 Flow 持久层 |
| ScanService/FieldwatchApp | 调度可参考；不可整块直接挂产品 | 移除隐式日志/位置/sit/TAK/告警出口耦合前必须先原版构建成功 |
| LogStore/LogReplay/GeoExport/SitExport | 复用编码/流式 IO 技术，schema 需 Adapter | Flow 仅聚合；资产日志仅授权对象；不能重用原始日志作 Flow 数据源 |
| CatalogRemote/云 prompt / tracking/hunt 功能 | 非本阶段直接集成对象 | MVP local-first；无云 LLM 依赖；不引入跨日客户跟踪 |

当前 CinemaWatch prototype/domain/Models.kt 已有 RadioObservation。建议先在 scanner 回调后、**任何 DeviceStore/LogStore/SitStore/导出入口之前**建立边界：

| RadioObservation 字段 | 固定上游映射与约束 |
| --- | --- |
| timestampMs | Observation.at；标明是接收墙钟。后续采集边界额外提供 monotonic capture time / 批次时间与 fresh 质量 |
| cinemaId/zoneId/probeId | 产品采样上下文显式注入；zone 手动选择；上游没有影厅标识 |
| radioType | WIFI/BLE -> RadioType 同枚举映射 |
| stableKey | 仅授权影院资产映射到内部 asset key；不能把客户 MAC 或固定 hash 作为持久 stableKey |
| rssi | 仅有效 Rssi.measured 观测；Flow 校准再采用适合现场的范围；缺失不伪造 0 |
| vendor | 本地 lookup 派生、带未知/启发式来源；BLE 不默认采用 Wi-Fi 本地位恢复 |
| signature | fleetIds 可能多个，现有 String? 应定义主标签选择或后续扩展候选集合；不伪造置信度 |

另需伴随 ObservationEnvelope/ScanHealth 接口（名称仅建议），表达 raw source 是否 fresh/cached、采样窗口、批次 ID、扫描暂停、丢样、权限/radio 状态、lookup/catalog 版本。当前 RadioObservation 不承载这些字段，不能默默丢弃后用于可信统计。

两条分流：
- Cinema Watch：仅授权资产登记匹配后的 internal asset key 可以持久化；匹配到 manufacturer/signature 不等于已授权资产。
- Cinema Flow：非资产标识只在当前短采样窗口内存去重；如采用临时 token，应每窗口/会话轮换且不落盘，不保留跨窗口可关联密钥/映射，不将名称/载荷/GPS 历史传入存储。持久化只允许 zone/time/probe aggregate 与质量信息，不关联会员、票务或 POS 个体身份。

prototype/privacy/PrivacyFilter.kt 的已读风险（本次未修改）：
1. distinct stableKey 计数对 null 会归零；不能因“隐私”先设 null 后宣称完整密度。
2. group key 缺 cinemaId，跨影院同 zone/probe/time 的输入会合并并取首行 cinemaId。
3. wifiCount 为 AP/BSSID 可见数、bleCount 为广播可见量，不能直接相加视作设备或人数。
4. rows>=20 时 confidence=0.8 否则 0.5 是占位规则，不是现场验证置信度；重复 BLE 广播即可提高 rows。
后续设计须同时解决隐私与可测量性，但这里不实现产品改造。

## 10. 风险与下一阶段建议

优先级：
1. **阻断**：代理不可用、缺完整二进制、系统无 Gradle/SDK，原版 build/test 尚未验证。先修复环境并完成原版构建闭环。
2. **高**：默认原始日志、GPS/payload、稳定 MAC-key 与历史出口不符合 Flow 默认隐私要求；先确定数据分流、存储白名单与删除边界。
3. **高**：Wi-Fi AP≠手机，BLE 广播≠人，随机地址/多 radio/不广播/墙外干扰使 RF 不可直接人头计数。单手机、手动选影厅、2–3 分钟采样与实际/闸机计数校准；输出区间/置信度/异常风险。
4. **高**：缓存 fresh 误判、DROP_OLDEST、日志抽样/轮转/导出暂停，导致缺失不透明。先定义 scan-health 和样本覆盖率，不用历史 log 当完整事件流。
5. **中**：API 29/30/31/33/35 权限和 OEM 屏幕关闭/节电/位置开关表现，需至少多厂商真机验证；无真机结果已获得。
6. **中**：OUI/Signature 属于启发式；多标签/本地位恢复可能误报，不能将“PHONE”类标签当人员或未经登记资产身份。
7. **中**：RSSI 和定位不稳定，需探针校准；channel 2484 映射、BLE 2402 占位等静态问题要通过后续独立、可测试修复处理。
8. **许可**：MIT 只覆盖上游原始软件；IEEE/SIG lookup 表和 Fast Pair 数据不可统称 MIT（见 NOTICE）。

下一阶段只建议：
- 完整固定 checkout + 原版 Wrapper build/test 成功，保存可复现证据，并更新此报告；成功前不开始 scanner refactor。
- 原版成功后评审采集/健康/分类/授权资产/短窗聚合 Adapter 契约，再以小提交逐步实现；本次没有实施。
- 优先延用现有 BleAdParserTest、WifiIeParserTest、DeviceStoreTest、Signature*Test、GeoExportTest/GeoPrivacyTest、LogReplayTest 测试基线；测试存在不代表本次已通过。
- 再规划真机权限/节电/断流/拥堵测试与影院校准。任何 occupancy 数值和 confidence 必须有现场依据。
- Issue #3 不在本次范围内，也未启动。

## 11. 本次变更与验收记录

CinemaWatch 仓库本次只新增 `FIELDWATCH_REUSE_REPORT.md`（本报告，含完整上游 MIT License 和 NOTICE）。
未导入不完整上游快照、未修改 prototype 或现有 docs、未改 main、未创建产品功能或合并。
源码文本与许可已在独立本地审计目录获取；远端提交通过 GitHub API 完成，shell git clone 不可用。
检查：固定 tag->commit 映射、上游文本 blob SHA 校验、本地 JAR/Java/Gradle/SDK 实测、两条 Gradle 任务启动失败、报告字段与源码核对。
构建/测试成功验收仍 BLOCKED；本报告不宣称 Issue #1 完全关闭。

## 附录 A：上游 MIT License（原文保留）

Fieldwatch — Copyright (c) 2026 Off Grid Pete LLC。
上游 LICENSE blob：`ba8a9e00dcce0960864a994f5646eacba48fb65a`。

```text
MIT License

Copyright (c) 2026 Off Grid Pete LLC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 附录 B：上游 NOTICE / attribution（原文保留）

上游 NOTICE blob：`2092160b748082988dafbd20c91c9b861ea028a9`。
后续复制或分发 Fieldwatch 原始/衍生源码与软件时必须继续携带完整 LICENSE 和 NOTICE；不得用本报告中的摘要替代分发许可文件。

```text
Fieldwatch
Copyright (c) 2026 Off Grid Pete LLC

This product includes software developed by third parties, licensed
under Apache License 2.0 (see LICENSE and the Apache license text
those projects ship):

  AndroidX (Jetpack Compose, Core, Lifecycle, Navigation, Activity, and
  related Android Open Source Project libraries)
  Kotlin standard library, kotlinx-coroutines, kotlinx-serialization
  JetBrains annotations
  Guava listenablefuture (empty stub)
  androidx.graphics:graphics-path (libandroidx.graphics.path.so)

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

Offline assigned-number tables in app/src/main/assets/lookups/radiodb.bin
(and MA-L prefixes in ApVendorOuis.kt) are packed from:

  IEEE Registration Authority MA-L / MA-M / MA-S / CID
  https://standards-oui.ieee.org/

  Bluetooth SIG Assigned Numbers (company identifiers, GAP Appearance,
  16-bit service UUIDs)
  https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/

Those lists are not licensed as MIT by Off Grid Pete LLC. Use of the
packed tables is subject to IEEE and Bluetooth SIG terms.

Fast Pair 24-bit model names in FastPairModels.kt are compiled from
public partner / community listings of Google Fast Pair model IDs.
They are not a Google product and are not covered by the MIT grant
as original Fieldwatch source.

Fieldwatch continues the Spectre 1.2.14 field build under a new name,
application id (app.fieldwatch), and license.
```
