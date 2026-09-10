# KeepAlive

一个基于 **前台服务 + 无障碍服务** 双看门狗机制的 Android 应用保活工具。用户勾选需要保活的目标应用后，应用会监控这些应用是否仍在运行，被系统杀死时自动重新拉起，并可在开机后自动恢复监控。

## 功能特性

- **目标应用保活**：枚举设备上所有可启动的应用，用户勾选后即加入保活名单；当目标应用进程被系统回收时自动通过其启动 Intent 重新拉起
- **自身保活**：可选"保活本应用"，通过前台服务 + 无障碍服务双通道维持自身进程存活
- **开机自启**：监听 `BOOT_COMPLETED`，开机后自动恢复监控并重新应用已保存的保活名单
- **状态可视**：主界面实时显示监控状态、无障碍服务开关状态、已安装可启动应用数量
- **防抖机制**：对同一目标应用连续重启设上限（默认 5 次），避免反复拉起失败导致系统抖动
- **权限引导**：在 Android 13+ 自动申请通知权限、引导用户开启无障碍服务与忽略电池优化

## 技术架构

### 保活机制（三层看门狗）

| 层级 | 组件 | 作用 |
|---|---|---|
| 前台服务 | `KeepAliveForegroundService` | 常驻通知栏，`START_STICKY` 保证进程被杀后系统会重建；提供"停止监控""请求重启无障碍"两个动作入口 |
| 无障碍服务 | `KeepAliveAccessibilityService` | 系统授予高优先级，进程极少被杀；周期性（15s）轮询目标应用运行状态，缺失即重启；监听窗口切换事件加速响应；自身被销毁时通知前台服务尝试恢复 |
| 开机广播 | `BootReceiver` | 设备启动后拉起前台服务，恢复监控状态 |

### 状态存储

`PreferenceHelper` 基于 `SharedPreferences`（`keep_alive_prefs`）保存 4 项配置：

- `keep_self_alive`：是否保活本应用
- `target_packages`：目标应用包名集合
- `monitoring_enabled`：监控总开关
- `auto_start_on_boot`：开机自动启动

### 目录结构

```
androidproject/
├── .github/workflows/build-release.yml   # CI：构建 & 发布 Release
├── build.gradle                          # 顶层构建脚本
├── settings.gradle                       # 仓库与模块配置（:app）
├── gradle.properties                     # Gradle JVM 参数
├── keystore.properties                   # 签名配置（由 CI 或本地提供，缺失时 Release 不签名）
└── app/
    ├── build.gradle                      # 应用模块构建配置
    └── src/main/
        ├── AndroidManifest.xml           # 权限、四大组件声明
        ├── java/com/example/keepalive/
        │   ├── MainActivity.kt           # 主界面：开关控制、应用列表、权限引导
        │   ├── KeepAliveForegroundService.kt  # 前台服务（常驻看门狗）
        │   ├── KeepAliveAccessibilityService.kt # 无障碍服务（核心监控与重启逻辑）
        │   ├── BootReceiver.kt           # 开机自启广播接收器
        │   ├── AppListAdapter.kt         # 应用列表 RecyclerView 适配器
        │   ├── PreferenceHelper.kt       # 配置持久化
        │   └── AppInfo.kt                # 应用条目数据模型
        └── res/                          # 布局、字符串、主题、图标等资源
```

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.2.0 |
| Kotlin | 1.9.20 |
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0+） |
| Gradle | 系统 Gradle 8.x（仓库未内置 gradlew wrapper） |

## 构建

```powershell
# 调试包
gradle assembleDebug

# 发布包（如存在 keystore.properties 则签名）
gradle assembleRelease
```

产物输出到 `app/build/outputs/apk/`。

### 签名配置（可选）

在项目根目录创建 `keystore.properties` 后 Release 包会自动签名：

```properties
storeFile=../your-keystore.jks
storePassword=your-store-password
keyAlias=your-alias
keyPassword=your-key-password
```

该文件不提交到仓库（已在 `.gitignore` 中），CI 构建时由 GitHub Secrets 生成。

## 使用说明

1. 安装并打开应用
2. 在系统设置中开启 **KeepAlive 保活服务**（无障碍）——应用内"打开无障碍设置"按钮可直接跳转
3. （可选）点击"请求忽略电池优化"，降低系统休眠回收概率
4. 在应用列表中勾选需要保活的目标应用
5. 按需打开"保活本应用"、"开机自动启动"开关
6. 点击"启动监控"，前台服务开始运行，通知栏出现常驻通知

## 已知限制与注意事项

- **后台限制**：Android 9+ 对后台启动 Activity 有严格限制，目标应用被杀死后从后台拉起可能被系统拦截，部分系统（如国内厂商 ROM）限制更严
- **无障碍权限**：用户可在系统设置中随时关闭无障碍服务，关闭后监控停止，需重新开启
- **应用商店政策**：`QUERY_ALL_PACKAGES` 与保活类行为在 Google Play 属于受限类别，上架前需确认目标商店政策
- **重启上限**：单个目标应用连续 5 次拉起失败后放弃，避免资源浪费（代码中 `MAX_RESTART_ATTEMPTS` 可调）

## CI

`.github/workflows/build-release.yml` 在推送到主干（或手动触发）时执行构建，产物以 Release 附件形式发布。
