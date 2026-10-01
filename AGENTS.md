# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## 项目概述

Stellar 是一个**单一用途的自用精简工具**（由 Shizuku 深度定制分支裁剪而来），100% Kotlin：

> 通过 **Root 或 ADB** 启动特权服务获得 shell / root 权限，并用该权限**切换系统默认密码管理器**
> （`settings secure` 的 `credential_service` / `credential_service_primary` / `autofill_service`）。

界面只有两个页面：**Home（启动服务）** 与 **PasswordSwitch（密码切换）**。

## 构建命令

```bash
./gradlew :manager:assembleDebug      # debug APK
./gradlew :manager:assembleRelease    # release APK（需要 signing.properties，缺失回退 debug 签名）
./gradlew :server:assemble            # 仅构建特权服务端逻辑
./gradlew clean
```

Release 产物输出到 `out/apk/`，mapping 输出到 `out/mapping/`。

签名配置读取根目录 `signing.properties`（不入库）：`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PASSWORD`。

## 项目架构

### 模块结构

```
manager/     → Android 应用（管理器 UI），applicationId: roro.stellar.manager
server/      → 特权服务端逻辑（运行在 ADB/Root 进程中）
api/         → 客户端 SDK 子模块（独立仓库 Stellar-API，勿在父仓库直接改）
  ├── aidl/        → AIDL 接口（IStellarService, IRemoteProcess, IRemotePtyProcess ...）
  ├── api/         → 客户端 API 入口（Stellar.kt, StellarHelper.kt）
  ├── provider/    → StellarProvider（ContentProvider，接收服务端 Binder）
  ├── shared/      → 共享常量（StellarApiConstants）
  └── userservice/ → UserService 框架
```

> `api/` 是 git submodule。对其内部（例如 `api/demo/`）的改动需要在子模块仓库提交并更新父仓库指针；
> 当前父工程已不再 include `:demo`，demo 源码保留在子模块中但不参与构建。

### 核心数据流

1. **服务启动**：`manager/startup/command/`（`Chid` 降权 + `Starter`）走 Root；`manager/adb/` 走 ADB 无线配对。
2. **Binder 分发**：`server/binder/BinderDistributor` + `server/BinderSender` → 经 ContentProvider 投递到管理器。
3. **客户端连接**：`api/provider/StellarProvider` 接收 Binder → `api/api/Stellar.kt` 封装调用。
4. **密码切换**：`PasswordSwitchViewModel` → `StellarUserService`（以 shell/root 身份）→ `PasswordSwitchService`（AIDL）。

### Manager 应用架构

- UI：**Miuix（HyperOS / MIUI 设计语言）**，整体结构参考 SukiSU 管理器：竖屏底部导航 /
  横屏侧边导航 + 分组卡片 + 状态标签。根主题在 `ui/theme/Theme.kt`（`MiuixTheme` +
  `ThemeController`），导航在 `ui/navigation/`
- 页面：仅 `Home`、`PasswordSwitch`（见 `ui/navigation/routes/NavigationRoutes.kt` 的 `MainScreen`）
- 二级页：`ui/features/manager/ManagerActivity`（仅 Starter 路由，Root/ADB 启动器）
- ADB 无线配对：`adb/` 包实现完整 ADB 协议栈（配对、mDNS 发现、连接）
- 数据层：`model/`、`common/state/`（无 Room）
- 密码切换：`passwordswitch/`（`PasswordSwitchService`、`Settings.kt` 的事务实现、`ShellCommandRunner`、`ProviderCatalog`）
- JNI：`src/main/jni/` 含 starter、chid、adb_pairing、rish 等 native 组件
- 多语言：仅英文（默认）+ 简体中文（`values-zh-rCN`）

> **UI 约定**：不要再引入 Material 3 组件或 Material 图标。图标统一用
> `MiuixIcons.*`（`top.yukonga.miuix.kmp.icon.extended`），颜色/字体统一走
> `MiuixTheme.colorScheme` / `MiuixTheme.textStyles`。对话框用 `ui/components/StellarDialog.kt`
> （内部是 Miuix `WindowDialog`）。

### Server 核心组件

- `StellarService`：AIDL Stub 主体，组装所有子系统
- `service/StellarServiceCore`：聚合各功能管理器（权限/进程/日志/系统属性/用户服务）
- `communication/`：`StellarCommunicationBridge`（统一请求分发）、`PermissionEnforcer`（管理器/自身/客户端三级判断）、`CallerContext`
- `bootstrap/ServerBootstrap`：服务引导（等待系统服务就绪、获取管理器信息）
- `binder/BinderDistributor`：Binder 分发
- `service/permission/`：PermissionManager / Checker / Confirmation / Requester
- `service/process/ProcessManager`、`service/log/LogManager`、`service/system/SystemPropertyManager`
- `service/info/`：ServiceInfoProvider、VersionProvider
- `service/userservice/UserServiceCoordinator` + `userservice/`：用户服务生命周期
- `ClientManager` / `ClientRecord`：客户端连接管理
- `ConfigManager` / `StellarConfig`：进程内配置（哪些 uid 声明了 Stellar 支持、权限标记）
- `grant/ManagerGrantHelper`：授予管理器 `WRITE_SECURE_SETTINGS`
- `api/`：RemoteProcessHolder、RemotePtyProcessHolder、IContentProviderUtils
- `monitor/PackageMonitor`：包变更监听

## 技术栈

- compileSdk 37, minSdk 24, targetSdk 37, JVM 21
- AGP 8.13.2, **Kotlin 2.3.21**, Compose Compiler 2.3.21
- **Miuix 0.9.1**（`top.yukonga.miuix.kmp:miuix-ui-android` / `-preference` / `-icons`）
- Compose BOM 2026.01.01, Navigation Compose 2.9.7
- NDK 29 + CMake 3.22.1+（JNI 组件）
- 版本目录：各模块独立 `*.versions.toml`（manager/server/api），根目录 `gradle/libs.versions.toml` 管理 hidden-api/refine

> **版本约束（改 Miuix 前必读）**：Miuix 0.9.1 依赖 Compose 1.11.0（AAR 元数据要求 AGP ≥ 8.6.0），
> Kotlin 元数据版本 2.3.0，因此工具链需 Kotlin ≥ 2.3。**不要**升到 Miuix 0.9.4 —— 它依赖
> Compose 1.12.0，会要求 AGP 9.1.0 并连带升级 Gradle。

## 版本号规则

`versionCode`: XYYZZZ（如 101000 = 1.1.0）
`versionName`: X.Y.Z[-suffix]（suffix: dev/alpha/beta/rc）
定义在根 `build.gradle` 的 `ext` 块中。

## CI/CD

- `.github/workflows/manager-ci.yml`：Manager 构建 CI（push/PR）
- `.github/workflows/manager-release.yml`：打 tag 时构建签名 release

## 注意事项

- **AIDL 契约不可随意改动**：`api/aidl/.../IStellarService.aidl` 位于子模块。即使本分支已移除
  Shizuku 兼容层与进程守护，`StellarService` 仍必须实现 `isShizukuCompatEnabled` /
  `setShizukuCompatEnabled` / `isDaemonEnabled` / `setDaemonEnabled`（当前为无害桩）。
- 本分支**不向第三方应用授予任何权限**（`PermissionEnforcer` 对非管理器一律拒绝）。
- `local.properties` 中设置 `api.useLocal=true` + `api.dir=路径` 可切换到本地 API 源码。
- JitPack 环境下（`JITPACK=true`）不包含 manager 和 server 模块。
- 密码切换的 Android 用户必须取 `Process.myUid() / 100000`，**不要**使用 shell 的 user 0。
