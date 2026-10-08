# 原应用覆盖升级与固定签名

## 这次为什么需要一次性换装

0.1 GitHub APK：包名 `com.cinemawatch`，证书 SHA-256 `a41b6eb9dfe58800bf4d6e6cc774bb52b11bd003c380e2b63ad75bcbe2938e27`。
0.2 GitHub APK：包名 `com.cinemawatch.preview`，证书 SHA-256 `2dd17170f8d7c638f3c43e75353b1565ef9d8e96a5c70fc5aa1cf2d8ecd21898`。
二者由临时 runner 密钥签名，私钥没有保存。本地旧 key 也不匹配，APK 中只有公钥证书，不能从 APK 恢复私钥。

用户已确认没有首版私钥，接受首版一次性删除。0.3 恢复 `com.cinemawatch`，使用已生成并备份的固定密钥。**先导出需要保留的历史，再卸载旧签名的原应用并安装 0.3。**不要提前清空旧版数据。0.2 如仍有数据可保留用来导出／核对；CSV 只导入汇总历史，不恢复授权绑定或校准。

从 0.3 起，后续版必须保持包名、证书一致、versionCode 递增，才能由 Android 原位覆盖，保留数据库、授权资产、绑定、报告及语言／区域设置。

固定证书：`12a3e20ece78354456c1cbb77c44cc6e606ae5d411620ca5a4fb57a1aa45a1bf`。私钥和口令不得提交到仓库。备份保存在独立签名目录并须交给仓库所有者妥善保存。

## 用户操作

设置 → 检查更新 → 显示新版本 → 下载更新 → 校验 → 覆盖安装。

首次调用安装器时，Android 可能要求允许 CinemaWatch 安装应用，用户在系统设置确认后返回。应用不能绕过系统安装确认，也不会自动卸载。巡检期间更新按钮停用；原有检测可离线运行。

仅用户手动检查／下载时联网访问 GitHub，不上传影院、资产、无线标识或报告。Android 网络权限用于更新；扫描与存储仍在本地。

下载前匹配包名、更新版本、最低系统版本和当前证书。下载流限制长度／大小，验证 SHA-256；安装前再次校验文件、APK 实际包名、实际版本和实际签名。错误、不兼容或损坏包会停止安装。当前只接受固定证书，不声明支持未实现的证书轮换。

## 发布配置

GitHub 仓库 Settings → Secrets and variables → Actions，配置两个 Repository secrets：

- `ANDROID_KEYSTORE_BASE64`：固定 `cinemawatch-release.p12` 的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：该密钥的口令。key/store 使用相同口令，alias 固定 `cinemawatch`。

可使用随备份提供的 `configure-signing.py`，在已登录仓库所有者账号的 GitHub CLI 环境中配置，不会在终端打印口令。

工作流的验证任务不接触发布私钥；发布任务读取 secrets、签名已通过验证的 APK、核对固定证书与版本配置后才上传 Release。缺少 secrets 时明确失败，不发布临时签名包。只下载 Release 的 APK，不安装 Actions 的临时测试候选包。

Release 同时发布 `update.json`，包含 schemaVersion、packageName、versionCode、versionName、minSdk、下载 URL、大小、SHA-256、签名指纹和说明。升级检查允许预览 Release，但跳过 draft；仅访问本仓库 HTTPS 资源。必须同时更新 Gradle 版本、资源版本文字及 `.github/release-config.json`；不得因 runner 重建而换 key。

## 验证边界

CI 构建 versionCode=2 的旧版本测试安装包和 versionCode=3 新版，同一测试证书；写入影院、区域、资产、无线绑定、报告、语言／区域设置后执行 `adb install -r`，验证数据保留。它证明同签名覆盖机制，不代表能覆盖使用已丢失私钥的真实 0.1／0.2。

另验证手动更新界面的“已是最新／发现新版／网络失败／错误签名拒绝安装”，包名、版本、签名、URL 与清单检查。系统未知来源授权和真实 GitHub 下载仍需真机验证。
