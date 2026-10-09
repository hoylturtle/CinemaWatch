# Fieldwatch 分组界面适配（0.7.0）

唯一基线仍为 Fieldwatch v1.1.21 / 5379a2049c351c2483f7f106ca68d5d6d5f00e64，未修改上游源码、MIT License 和 NOTICE。

直接参考并移植 `LiveScreens.kt` 的 `OutlineGroupRow`、`DeviceRow` 布局，以及 `Widgets.kt` 的 `RssiBar`，放入 `SignalOutline.kt`。保留 12dp 填充圆角卡、类别图标、右侧展开箭头与数量、缩进设备卡、彩色识别标签、RSSI 强度条及首次/最近发现时间；类别和国内生态使用相同组件。按本次需求省去签名子级，展开类别直接呈现设备，多个组可独立展开，不再把设备统一放到所有分组之后。

MainActivity 以 LazyColumn 分别渲染分组和设备。搜索先过滤内存中的名称、厂商、签名，再计算各组数量。设备可同时归属类型与生态，统计不等同独立设备数或人数。详情显示原识别证据、置信度与局限，资产登记继续要求授权且仅在真实扫描期间可用；演示不提供登记。

ScanEngine 为会话内 LiveRadio 补充 firstAt 和实际观测到的 frequencyMhz；保持最多 20 次 RSSI 历史。均值取可测量读数，127 等缺失值不绘制真实强度。强度条使用上游 `(RSSI + 100) / 70` 截断到 0..1，不是距离或人数。时间每秒更新，首次时间随同会话内地址保留；未增加原始信号数据库、网络请求或跨会话跟踪。

中文、繁体中文、英文文案同步更新。Android 验证直接展开设备、强度条、设备详情、演示禁止登记、多组独立展开及国内生态。截图仅采用合成演示信号。

改动：SignalOutline.kt、MainActivity.kt、ScanEngine.kt、FirstRunTest.kt、四套 strings.xml、版本与发布配置。
