# Stellar 瘦身改造 · 执行报告（Refactor Report）

> 文档编号：02-refactor-report
> 阶段：执行完成（代码已改动）
> 结论依据：直接读取仓库源码 + 全量符号引用扫描（**本机无编译环境，未做编译验证**）

---

## 1. 一句话结果

把 Stellar 从「通用 Shizuku 定制框架」收敛为**单一用途工具**：

> **通过 Root / ADB 启动特权服务获得 shell 权限 → 用该权限切换系统默认密码管理器。**

界面仅剩 **Home（启动）** 与 **PasswordSwitch（密码切换）** 两个页面。

| 指标 | 改造前 | 改造后 |
|---|---|---|
| manager Kotlin 文件 | 84 | **51** |
| server Kotlin 文件 | 54 | **41**（另有 3 个 Java：`rikka/rish` 迁入） |
| manager 资源目录 | 19 | **11** |
| 默认 strings.xml 条目 | 348 | **153** |
| git 变更 | — | 删除 **81** 个文件、修改 **34** 个 |

---

## 2. 保留（In Scope）——核心能力

### 能力 A：启动服务获取 shell / root 权限

| 环节 | 落点 |
|---|---|
| 首页与启动卡 | `ui/features/home/`（HomeScreen / HomeComponents / HomeViewModel） |
| Root 启动 | `startup/command/Chid.kt`、`Starter.kt` + JNI `chid.cpp` / `starter.cpp` + libsu |
| ADB 无线启动 | `adb/`（AdbClient / AdbPairingClient / AdbPairingService / AdbWirelessHelper / AdbMdns / AdbProtocol / AdbKey / AdbMessage / AdbException）+ JNI `adb_pairing.cpp` |
| 启动器二级页 | `ui/features/manager/ManagerActivity.kt`（Starter 路由）+ `StarterScreen.kt` |
| 特权服务端 | `server/` 模块整体（StellarService / ServerBootstrap / BinderSender / BinderDistributor / ClientManager / ConfigManager / PermissionEnforcer / StellarCommunicationBridge / service/* / userservice/*） |
| 客户端链路 | `:api`、`:provider`、`:aidl`、`:shared`、`:userservice` 模块 |
| Binder 投递 | `manager/StellarManagerProvider`（仅复用基类收发逻辑） |

### 能力 B：用该权限驱动密码管理器切换（**语义零回归**）

| 环节 | 落点 |
|---|---|
| UI | `ui/features/passwordswitch/PasswordSwitchScreen.kt`、`PasswordSwitchViewModel.kt` |
| 特权执行体 | `passwordswitch/PasswordSwitchService.kt`（UserService，shell/root 身份） |
| 目标系统键 | `passwordswitch/Settings.kt` → `SettingKey`（`credential_service` / `credential_service_primary` / `autofill_service`） |
| 事务语义 | `SettingsController.replace()`：写前快照比对 → 逐项写入 → 读回校验 → 失败**逆序回滚** → 返回「已恢复 / 无法确认」 |
| 命令执行 | `ShellCommandRunner.kt`（固定 argv，不拼接 shell 字符串） |
| 服务发现 | `ProviderCatalog.kt`、`ProviderCodec.kt` |
| 跨进程接口 | `manager/src/main/aidl/roro/stellar/manager/passwordswitch/IPasswordSwitchService.aidl` |

> 约束保持：Android 用户取 `Process.myUid() / 100000`，**不使用** shell 的 user 0。

---

## 3. 删除清单（Out of Scope）

### 3.1 不可达 UI 页面与关联代码（manager）

| 对象 | 路径 |
|---|---|
| Apps 授权管理页 | `ui/features/apps/`（AppsScreen） |
| Terminal 终端页 | `ui/features/terminal/`（TerminalScreen / TerminalViewModel） |
| Settings 设置页 | `ui/features/settings/`（SettingsScreen） |
| Logs 服务日志页 | `ui/features/manager/LogsScreen.kt`（并移除 `ManagerActivity` 的 Logs 路由与 `createLogsIntent`） |
| AppsViewModel / AppType | `domain/apps/` |
| 授权入口 | `authorization/`（AuthorizationManager、RequestPermissionActivity） |
| 命令快捷方式 | `shortcut/`（CommandShortcutActivity、CommandShortcutManager） |
| Room 数据层 | `db/`（AppDatabase、Daos、Entities） |
| 开机脚本 | `startup/boot/BootScriptManager.kt` |
| 应用内更新 | `util/update/`（ApkDownloader、AppUpdate、UpdateUtils） |
| 失效组件 | `ui/components/`（SettingsItemCard、IconContainer、StellarSegmentedSelector） |
| 失效工具 | `util/`（PinyinUtils、StellarSystemApis、UserInfoCompat）、`ktx/PackageManager.kt` |

### 3.2 开机自启 / 无障碍 / 保活通道（manager）

| 对象 | 路径 |
|---|---|
| 开机广播 | `receiver/BootCompleteReceiver.kt` |
| 后台拉起 | `receiver/StellarReceiver.kt`、`receiver/StellarReceiverStarter.kt` |
| ADB 自启 Worker | `startup/worker/AdbStartWorker.kt`、`AdbStarter.kt` |
| 通知重试 | `startup/notification/BootStartNotifications.kt`、`BootStartActionReceiver.kt` |
| 前台自启服务 | `startup/service/SelfStarterService.kt` |
| 无障碍服务 | `service/StellarAccessibilityService.kt` |
| 设置项 | `StellarSettings` 的 `BOOT_MODE` / `BootMode` / `SHIZUKU_COMPAT_ENABLED` / `ACCESSIBILITY_AUTO_START_PROMPTED` / `DAEMON_ENABLED` / `BOOT_BROADCAST_ACCESSIBILITY_ENABLED` 及迁移逻辑 |
| 清单声明 | `AndroidManifest.xml` 中上述 service / receiver 与 `RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE_DATA_SYNC`、WorkManager 服务声明 |

### 3.3 Shizuku 兼容层（跨模块，风险最高）

| 对象 | 说明 |
|---|---|
| 模块 | 整个 `shizuku/`（`:shizuku-aidl`、`:shizuku-api`），并从 `settings.gradle` / `server/build.gradle` 移除依赖 |
| server 实现 | `server/.../shizuku/` 全部 9 个文件 |
| 改写点 | `BinderSender`（去 Shizuku 分支）、`BinderDistributor`（去 `sendShizukuBinderToUserApp`）、`ClientManager`（去 `attachShizukuApplication` / `getOrCreateClient`）、`ClientRecord`（去 `shizukuApplication` / `dispatchShizukuPermissionResult`）、`ConfigManager`（去 Shizuku 权限探测与开关）、`ProcessManager`（`BINDER_DESCRIPTOR` 改用 `StellarApiConstants`）、`ProviderDiscovery`（去 `hasShizukuProvider`） |

### 3.4 其他 server 无用能力

| 对象 | 说明 |
|---|---|
| 进程守护 | `daemon/`（DaemonManager、StellarDaemon）及 `StellarService` 中的守护监控逻辑 |
| 跟随启动 | `ext/FollowStellarStartupExt.kt`（面向第三方应用的启动广播） |
| 应用查询 | `query/ApplicationQueryHelper.kt` 与 `onTransact` 的 `getApplications` 分支 |
| 配置持久化 | `ConfigManager` 的 `loadFromManager` / `saveToManager`（改为进程内配置）；`StellarConfig` 精简为 `packages` |
| 无障碍授予 | `ManagerGrantHelper.grantAccessibilityService()` |
| 无用依赖 | server 的 gson、`serverLibs.gson` |
| 无用常量 | `ServerConstants.REQUEST_PERMISSION_ACTION`、`BINDER_TRANSACTION_getApplications`；`ktx/Handler.kt` 的 `workerHandler` |

### 3.5 构建配置 / 资源 / 文档 / 示例

| 对象 | 处理 |
|---|---|
| `settings.gradle` | 移除 `:demo`、`demoLibs`、`:shizuku-aidl`、`:shizuku-api`、悬空的 `:hidden-api-stub`；移除 ksp / materialthemebuilder 插件与 `com.github.jeziellago` 仓库；修复 `api.useLocal` 的空指针写法 |
| `manager/build.gradle` | 移除 ksp 插件与 room / okhttp / compose-markdown / gson / work / appiconloader 依赖 |
| `manager.versions.toml` | 同步精简（`core` bundle 收敛为 appcompat + coreKtx + coreSplashscreen） |
| 多语言 | 仅保留默认英文 + `values-zh-rCN`，删除 ar / es / fr / ja / pt-rBR / ru / zh-rHK / zh-rTW |
| 字符串 | 删除 191 条随功能失效的字符串（中英文同步），348 → 153 条 |
| 无障碍资源 | 删除 `res/xml/accessibility_service_config.xml`、失效样式（GrantPermissions / ShortcutExecutor）、失效字符串 |
| 文档 | 删除 `INTEGRATION_GUIDE.md`、`INTEGRATION_GUIDE_en.md`；重写 `README.md`、`README_en.md`、`AGENTS.md` |
| CI | 删除 `publish.yml`（JitPack 发布）、`sync-release-gitee.yml`；保留 `manager-ci.yml`、`manager-release.yml` |
| 示例 | 父工程不再 include `:demo`；**未修改 `api/` 子模块仓库**（demo 源码保留在子模块中） |

---

## 4. 关键决策与依据

| # | 决策 | 依据 |
|---|---|---|
| D1 | Root 与 ADB 两条**手动**启动路径全部保留 | 用户决策 |
| D2 | 开机自启 / 无障碍 / 通知重试 / 开机脚本 / 进程守护全部删除 | 用户选择「全部删除」 |
| D3 | Shizuku 兼容层彻底移除（含模块与 Binder 分发链路改写） | 用户选择「彻底移除」 |
| D4 | `api/` 子模块**只去引用、不改仓库** | 用户选择「仅父工程去引用」 |
| D5 | 语言精简为 en + zh-rCN；CI 只留 Manager 构建 | 用户选择「精简」 |
| D6 | **AIDL 契约中的 `isShizukuCompatEnabled` / `setShizukuCompatEnabled` / `isDaemonEnabled` / `setDaemonEnabled` 保留为无害桩** | `api/aidl/.../IStellarService.aidl` 在子模块中，本分支不改子模块；Stub 抽象方法必须实现 |
| D7 | **`rikka/rish` 三个 Java 类从被删的 `shizuku/api` 迁入 `server/src/main/java/rikka/rish/`** | `ProcessManager.newPtyProcess` 与 `RemotePtyProcessHolder` 依赖 `RishHost`/`RishConfig`/`RishConstants`；PTY/shell 属核心能力，且 `librish.so` 仍由 manager JNI 构建 |

---

## 5. 验证情况与残留风险

### 已完成（静态）

- ✅ 已删符号**全量引用扫描**：AppsScreen / TerminalScreen / SettingsScreen / LogsScreen / AuthorizationManager / CommandShortcut* / AppDatabase / BootScriptManager / BootCompleteReceiver / SelfStarterService / StellarAccessibilityService / AdbStartWorker / BootStartNotifications 等 → **0 悬空引用**
- ✅ **import 目标校验**：manager / server 内部 import 无指向已删包（`ui.features.apps|terminal|settings`、`domain`、`authorization`、`shortcut`、`db`、`startup.*`、`util.update`、`server.shizuku|daemon|ext|query` 均无命中）
- ✅ **资源一致性**：字符串「引用但未定义 = 0」；中英文条目同步；XML 良构（ElementTree 解析通过）
- ✅ **构建脚本一致性**：`managerLibs.*` / `serverLibs.*` 别名全部存在于精简后的 toml；无 `demoLibs` / `:shizuku-*` / `hidden-api-stub` 残留
- ✅ **AIDL 契约**：`StellarService` 实现了 `IStellarService` 的全部方法

### 未完成 / 风险

| # | 项 | 说明 |
|---|---|---|
| R1 | **未做编译验证** | 本机无可用编译环境（无 JDK 21 / Gradle 依赖缓存），以上均为静态扫描结论。**首次构建请重点检查**：`server/src/main/java/rikka/rish/` 是否被正确编译、`ClientRecord` / `ClientManager` 是否残留未用 import |
| R2 | 残留未用 import | 部分改写文件可能存在「已删逻辑遗留的 import」（Kotlin 仅告警不报错），首次编译后可按 warning 清理 |
| R3 | `:demo` 源码仍在子模块 | 按决策 D4 保留；如需彻底删除，须在 `Stellar-API` 仓库提交并更新父仓库 submodule 指针 |
| R4 | 数据迁移 | 删除 Room 后，旧版数据库文件与旧设置项（BootMode 等）不再被读取，属预期行为 |
| R5 | 第三方集成能力 | 本分支已不向第三方应用授予任何权限（`PermissionEnforcer` 对非管理器一律拒绝）；`api/` SDK 仍在但仅管理器自身使用 |

---

## 6. 建议的后续动作

1. 在具备 JDK 21 + Android SDK（NDK 29）的环境执行 `./gradlew :manager:assembleRelease`，按 warning 清理残留 import。
2. 真机验证两条启动路径（Root / ADB 无线）与密码切换的事务回滚（可在写入过程中断连测试「无法确认」分支）。
3. 如确认不再需要 PTY / 交互式 shell，可进一步删除 `rikka/rish` 与 `RemotePtyProcessHolder`、`ProcessManager.newPtyProcess`（需同步改 `newPtyProcess` 为抛异常实现）。
