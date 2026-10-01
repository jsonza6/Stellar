# Stellar 瘦身改造 · 范围说明书（Scope Statement）

> 文档编号：01-scope-statement
> 阶段：方案评审（本次仅产出文档，不改动任何代码）
> 作者：产品经理 许清楚
> 结论依据：直接读取仓库源码核实（非推测），关键落点均标注文件路径

---

## 1. 背景与目标

**一句话目标**：把 Stellar 从「通用 Shizuku 定制框架」收敛为一个**单一用途工具**——通过启动服务获得 shell/root 权限，并用该权限在系统设置中切换默认密码管理器（凭据提供者 / 自动填充服务）；删除一切与该用途无关的模块、UI 页面、文档与语言资源，使源码树与产品定位一致。

### 1.1 项目定位现状（核实）

| 项 | 现状 |
|---|---|
| 语言 | 100% Kotlin |
| 应用 ID | `roro.stellar.manager`（`manager/build.gradle`） |
| 当前宣传定位 | "Shizuku 的深度定制分支，通过 ADB 或 Root 权限为**应用**提供系统级 API 框架"（`README.md` / `AGENTS.md`） |
| 目标定位 | 仅服务单一用途：**启动特权服务 → 驱动密码管理器切换** |
| 导航现状 | `MainScreen` 枚举仅剩 `Home`、`PasswordSwitch` 两项（`manager/.../ui/navigation/routes/NavigationRoutes.kt`） |

### 1.2 本次改造要达成的结果

1. 产品能力面收敛到「启动服务 + 密码切换」两条，其余全部移除。
2. 源码树中不再残留不可达页面、无关模块、第三方集成文档与冗余语言资源。
3. 删除后项目仍能构建（在环境允许范围内），且密码切换的事务语义**零回归**。

---

## 2. 核心能力边界（In Scope / 保留）

### 2.1 能力 A：启动服务获取 shell/root 权限（Root + ADB 双路径）

> 用户已确认：**Root（libsu）与 ADB 无线配对两条路径都必须保留**。

| 环节 | 落点（包 / 文件） | 说明 |
|---|---|---|
| 首页入口 UI | `manager/.../ui/features/home/HomeScreen.kt`、`HomeViewModel.kt`、`HomeComponents.kt` | 服务状态卡、Root 启动卡、无线/有线 ADB 启动卡 |
| 二级启动页 | `manager/.../ui/features/manager/StarterScreen.kt` + `ManagerActivity.kt`（Starter 路由） | **可达**：由 `HomeScreen.onNavigateToStarter` → `ManagerActivity.createStarterIntent` 拉起；`AdbPairingService` 也复用 |
| Root 启动 | `manager/.../startup/command/Chid.kt`、`Starter.kt`；依赖 `managerLibs.libsuCore`；`manager/src/main/jni/chid.cpp`、`starter.cpp` | Root 下经 `libchid` 降权到 shell，再启动服务 |
| ADB 启动 | `manager/.../adb/`（`AdbClient`、`AdbPairingClient`、`AdbPairingService`、`AdbWirelessHelper`、`AdbMdns`、`AdbProtocol`、`AdbKey`、`AdbMessage`、`AdbException`）；`manager/.../startup/worker/AdbStarter.kt`、`AdbStartWorker.kt`；`manager/src/main/jni/adb_pairing.cpp` | 无线配对 + 连接 + 拉起服务 |
| 服务端（特权进程） | `server/` 模块整体 | 运行在 ADB/Root 进程内的服务端逻辑 |
| 启动关联组件 | `StellarApplication.kt`、`StellarManagerProvider.kt`、`receiver/StellarReceiver.kt`、`receiver/BootCompleteReceiver.kt`、`startup/service/SelfStarterService.kt`、`startup/notification/BootStartNotifications.kt` | 服务绑定、开机/自启动通道 |
| 客户端 SDK 依赖 | `:api`、`:provider`、`:aidl`、`:shared` 模块 | 管理器自身通过它们与 server 通信（`Stellar.pingBinder()` 等） |

### 2.2 能力 B：用该权限驱动密码管理器切换

> 核心语义（必须完整保留）：**事务式写入 + 读回校验 + 失败回滚**。

| 环节 | 落点（文件） | 要点 |
|---|---|---|
| UI | `manager/.../ui/features/passwordswitch/PasswordSwitchScreen.kt`、`PasswordSwitchViewModel.kt` | 选择凭据提供者 / 自动填充服务、应用、恢复上次配置、刷新 |
| 特权执行体 | `manager/.../passwordswitch/PasswordSwitchService.kt` | 作为 **UserService** 以 shell(uid 2000)/root 身份运行；**非**导出 Android Service |
| 目标系统键 | `manager/.../passwordswitch/Settings.kt` → `SettingKey` | 精确读写 `settings secure` 的三项：`credential_service`、`credential_service_primary`、`autofill_service` |
| 事务实现 | `Settings.kt` → `SettingsController.replace()` | ① 写前 `read()` 得到 `before` 快照；② 与调用方传入的 `expected` 比对，不一致则**拒绝写入**；③ 逐项写入并记录 `attempted`；④ 写后 `read()` 与 `desired` 比对；⑤ 不一致或异常时**逆序回滚**并对每项校验当前值；⑥ 返回"已恢复/无法确认"结论 |
| 命令执行 | `Settings.kt` → `ShellSettingsStore` + `ShellCommandRunner.kt` | 仅执行固定 argv：`/system/bin/settings --user <id> {get|put|delete} secure <key>`；**不拼接 shell 字符串**；带超时与输出长度上限 |
| 服务发现 | `ProviderCatalog.kt`、`ProviderCodec.kt` | 扫描当前用户下声明了凭据/自动填充服务的应用 |
| 跨进程接口 | `manager/src/main/aidl/roro/stellar/manager/passwordswitch/IPasswordSwitchService.aidl` | `read / apply / restore / listProviders / destroy` |
| 备份/恢复 | `PasswordSwitchViewModel`（`password_switch_backup` SharedPreferences） | 应用前保存快照，支持"恢复上次配置" |
| 运行框架依赖 | `:userservice` 模块（`StellarUserService`、`UserServiceArgs`） | 特权助手进程的启动与绑定 |
| 服务端支撑 | `server/.../userservice/`、`server/.../api/RemoteProcessHolder.kt` | UserService 生命周期与进程管理 |

> 注：`PasswordSwitchViewModel` 使用 `Process.myUid()/100000` 计算 Android 用户；**不要**使用 shell 的 user 0。此约束在改造中必须保持。

---

## 3. 明确排除项（Out of Scope / 删除）

> 以下为建议删除/改造清单，按类别分组。最终精确删除清单由架构师阶段产出。

### 3.1 不可达 UI 页面与关联代码

| 对象 | 路径 | 核实结论 |
|---|---|---|
| Apps 授权管理页 | `manager/.../ui/features/apps/AppsScreen.kt` | 未在 `MainScreen` 中，`MainActivity` 仅有**未使用的 import**；不可达 |
| Terminal 终端页 | `manager/.../ui/features/terminal/TerminalScreen.kt`、`TerminalViewModel.kt` | 不可达；`TerminalScreen` 内声明的 `CommandItem` 被 `CommandShortcutManager` 引用（需连带处理） |
| Settings 设置页 | `manager/.../ui/features/settings/SettingsScreen.kt` | 不可达；仅它引用更新检查、BootScriptManager、DB |
| Logs 服务日志页 | `manager/.../ui/features/manager/LogsScreen.kt` | `ManagerActivity.createLogsIntent` **全仓库无调用方**，Logs 路由不可达 |
| AppsViewModel | `manager/.../domain/apps/AppsViewModel.kt` | 仅供 AppsScreen 使用；但其中 `enum class AppType` 被 `AuthorizationManager`、`MainActivity` 引用 → 删除时需**保留/迁移 `AppType`** |
| MainActivity 死 import | `MainActivity.kt` 第 41/43/46/51/52 行 | `RequestPermissionActivity`、`AppsViewModel`、`AppsScreen`、`SettingsScreen`、`TerminalScreen` 均未使用 |

> ⚠️ **重要更正**：`StarterScreen.kt` / `ManagerActivity` 的 **Starter 路由是可达的**（HomeScreen 与 AdbPairingService 都会拉起），**不在删除范围**；仅 `Logs` 路由可删。

### 3.2 客户端授权（本分支不再提供）

| 对象 | 路径 | 说明 |
|---|---|---|
| 授权入口 Activity | `manager/.../authorization/RequestPermissionActivity.kt` | 未在 AndroidManifest 注册、全仓库无调用方；死代码 |
| 授权管理 | `manager/.../authorization/AuthorizationManager.kt` | `MainActivity.handlePendingSourceApp()` 已被改成"直接 clear，不弹授权"，仅剩 `AppType` 判定；随授权面收敛一并评估 |
| MainActivity 授权分支 | `MainActivity.kt` 的 `rememberSourceApp/handlePendingSourceApp/clearSourceApp` | 逻辑已空转，可精简 |

### 3.3 终端与命令快捷方式

| 对象 | 路径 |
|---|---|
| 终端 UI | `manager/.../ui/features/terminal/`（含 `CommandItem`） |
| 快捷方式 | `manager/.../shortcut/CommandShortcutActivity.kt`、`CommandShortcutManager.kt` |
| 命令执行数据层 | `manager/.../db/`（`AppDatabase`、`Daos.kt` 的 `CommandDao`、`Entities.kt`） |
| 跟随服务命令执行 | `StellarApplication.executeFollowCommands()`（依赖 `CommandDao`） |
| 开机脚本 | `manager/.../startup/boot/BootScriptManager.kt`（仅 SettingsScreen 引用） |

> 注：`db/` 中的 `LogDao`、`ConfigDao` 同样服务于已删页面，是否整包删除由架构师定；`ConfigDao` 被 SettingsScreen 使用。

### 3.4 Shizuku 兼容层（跨 server 与独立模块，牵连最广）

| 对象 | 路径 / 说明 |
|---|---|
| 兼容层模块 | `shizuku/aidl`、`shizuku/api`（`settings.gradle` 中 `:shizuku-aidl`、`:shizuku-api`） |
| server 兼容实现 | `server/.../shizuku/`（`ShizukuServiceIntercept`、`ShizukuApiConstants`、`ShizukuCallbackFactory`、`ShizukuPermissionNotifier`、`ShizukuServiceCallback`、`ShizukuUserServiceAdapter`、`ShizukuUserServiceRecord`、`RishServiceImpl`） |
| 被牵连的 server 代码 | `BinderSender.kt`、`binder/BinderDistributor.kt`、`ClientManager.kt`、`ClientRecord.kt`、`ConfigManager.kt`（`isShizukuCompatEnabled`/`setShizukuCompatEnabled`） |
| 构建依赖 | `server/build.gradle` 第 26–27 行 `project(':shizuku-aidl')`、`project(':shizuku-api')` |
| 资源开关 | `StellarSettings.SHIZUKU_COMPAT_ENABLED` |
| 文档 | `README.md`「Shizuku 兼容层」「降权激活」等章节 |

> ⚠️ 删除兼容层**不是删目录那么简单**：需同步改写 server 的 Binder 分发链路（`BinderSender`/`BinderDistributor`/`ClientManager`/`ClientRecord`/`ConfigManager`），并保证管理器自身与 server 的通信不受影响。这是本次改造**风险最高**的一项。

### 3.5 应用更新检查

| 对象 | 路径 |
|---|---|
| 更新实现 | `manager/.../util/update/`（`ApkDownloader.kt`、`AppUpdate.kt`、`UpdateUtils.kt`） |
| 依赖项 | `managerLibs.okhttp`（网络）、可能含 `composeMarkdown` |
| 权限 | `AndroidManifest.xml` 的 `android.permission.INTERNET`（确认仅更新用途后再删） |
| CI/发布自动化 | `.github/workflows/publish.yml`、`sync-release-gitee.yml`（视是否需要保留发布流程而定） |

### 3.6 示例应用（api 子模块内）

| 对象 | 路径 |
|---|---|
| Demo 示例 | `api/demo/`（`DemoFunctions`、`DemoUserService`、`MainActivity`、各 UI 等），及 `settings.gradle` 中 `:demo`、`demoLibs` 版本目录 |

### 3.7 第三方集成文档

| 对象 | 路径 |
|---|---|
| 集成指南 | `INTEGRATION_GUIDE.md`、`INTEGRATION_GUIDE_en.md` |
| 子模块文档 | `api/README.md`（面向第三方集成） |
| README 相关章节 | `README.md` / `README_en.md` 中「集成 Stellar 到你的应用」「快速开始」「相关链接」等 |
| AI 指引 | `AGENTS.md`、`CLAUDE.md`（建议**保留但重写**为瘦身后的架构，而非删除） |

### 3.8 多余语言资源

| 保留 | 删除 |
|---|---|
| `res/values`（默认英文）、`res/values-zh-rCN` | `values-zh-rHK`、`values-zh-rTW`、`values-ar`、`values-es`、`values-fr`、`values-ja`、`values-pt-rBR`、`values-ru` |

> 同时需清理 `AGENTS.md` 中"多语言：英/简中/繁中(TW/HK)/法/俄/阿/西"的过时描述。

---

## 4. 约束与假设

| # | 约束 / 假设 | 依据 |
|---|---|---|
| C1 | **Root 与 ADB 双启动路径必须同时可用**，不得只保留其一 | 用户决策 1 |
| C2 | **不得破坏密码切换的事务回滚语义**（写入前比对、读回校验、逆序回滚、无法确认时的提示） | 核心能力定义 |
| C3 | `api/` 是**独立 git 仓库**（submodule `Stellar-API`，当前 commit `e22b3a0`）。对其内部（如 `demo/`）的删除必须：① 在子模块仓库内提交；② 更新父仓库 submodule 指针。**不能只改父仓库** | `git submodule status` |
| C4 | 删除 `shizuku/` 必须同步改写 `server/` 的 Binder 分发与 `server/build.gradle` 依赖，否则编译失败 | `server/build.gradle`、`BinderSender.kt` |
| C5 | 删除 `AppType` 所在文件时，`AppType` 被 `AuthorizationManager`/`MainActivity` 引用，需保留或迁移 | Grep 核实 |
| C6 | `TerminalScreen` 的 `CommandItem` 被 `CommandShortcutManager` 引用，删除顺序需保证无悬空引用 | Grep 核实 |
| C7 | 构建环境 **缺 NDK**：`D:\DevHome\Android` 下无 `ndk/`、无 `cmake/`（仅 `build-tools/36.0.0`、`37.0.0`）。而 `manager` 需要 NDK 29 + CMake 编译 JNI（`starter/chid/adb_pairing/rish`）→ **本地无法完成 `:manager:assembleDebug` 的完整构建验证** | 实测目录 |
| C8 | `settings.gradle` 包含 `:hidden-api-stub`，指向 `api/hidden-api-stub`，但该目录**不存在**（全仓库无匹配）。属既有悬空引用，需在改造中一并核实/修复 | `find` 核实 |
| C9 | 版本基线以根 `build.gradle` 为准：`compileSdk=37 / minSdk=24 / targetSdk=37 / JVM 21 / NDK 29.0.13113456`；`AGENTS.md` 中的 `compileSdk 36` 描述已过时 | 读文件 |
| C10 | 本次**只产出文档、不动代码**，用户确认后方进入执行阶段 | 用户决策 4 |

---

## 5. 验收标准（Acceptance Criteria）

| # | 标准 | 核验方式 |
|---|---|---|
| AC1 | 全仓库**无悬空引用**：删除后不存在指向已删符号/模块的 import 或 gradle 依赖 | 全量编译（环境允许时）；否则对每个删除项做 Grep 引用扫描，结果为空 |
| AC2 | 底部导航/导航图**仅剩两个页面**：Home、PasswordSwitch | 读 `NavigationRoutes.kt` 的 `MainScreen` 枚举 = {Home, PasswordSwitch}；`MainActivity` 的 `NavHost` 仅注册 `home`、`password_switch` 两条 `navigation(...)` |
| AC3 | 密码切换**语义完整保留**：读、应用、恢复、写前比对、读回校验、失败逆序回滚、无法确认提示 | 代码审查 `SettingsController.replace()` 六个环节齐全；`PasswordSwitchViewModel` 的 backup/restore 路径保留；AIDL `read/apply/restore/listProviders` 保留 |
| AC4 | 语言资源**仅剩默认英文 + `values-zh-rCN`** | 列 `manager/src/main/res/values*` 目录，仅 `values`、`values-zh-rCN`（+`values-night` 主题） |
| AC5 | `api/demo` 示例应用与 `:demo`/`demoLibs` 声明移除；子模块改动已提交 | 父仓库 `settings.gradle` 无 `:demo`；`api` 子模块 `git log` 有对应删除提交；父仓库 submodule 指针已更新 |
| AC6 | Shizuku 兼容层模块与 server 内实现移除，且 server 仍可独立编译 | `settings.gradle` 无 `:shizuku-*`；`server/build.gradle` 无 `:shizuku-*` 依赖；`server/.../shizuku/` 删除；`BinderSender`/`BinderDistributor` 等已改写且无 `moe.shizuku`/`roro.stellar.server.shizuku` 引用 |
| AC7 | Root 与 ADB 两条启动路径**均保留**（对应代码未被误删） | `startup/command/Chid.kt`、`Starter.kt`、`adb/` 包、`startup/worker/AdbStarter.kt` 均在；`HomeScreen` 的 Root/无线/有线三张启动卡仍在 |
| AC8 | 第三方集成文档与更新检查移除 | 仓库无 `INTEGRATION_GUIDE*.md`；`util/update/` 删除；`README` 相关章节改写为单一用途定位 |
| AC9 | 构建可执行，或在环境受限时**明确说明限制** | 有 NDK 环境时执行 `./gradlew :manager:assembleDebug`、`:server:assemble`；无 NDK 时至少完成 Gradle 配置阶段（`./gradlew projects`/`tasks`）与静态引用扫描，并在交付说明中记录 C7 限制 |
| AC10 | 文档与实际一致 | 更新后的 `README.md`/`README_en.md`/`AGENTS.md` 不再宣称"Shizuku 兼容层/第三方集成/多语言/应用内更新"等已删能力 |

---

## 6. 待确认问题

| # | 问题 | 备选方案 / 影响 |
|---|---|---|
| Q1 | **开机自启动（Boot）相关代码是否保留？** 涉及 `BootCompleteReceiver`、`SelfStarterService`、`BootStartNotifications`、`BootStartActionReceiver`、`StellarSettings.BootMode`、`StellarAccessibilityService`。它们属于"启动服务"的延伸通道，但用户只强调 Root + ADB 两条**手动**启动路径 | A. 保留（启动体验完整）／B. 删除（更纯粹，但需处理 `AndroidManifest` 与 `StellarSettings`）。**影响删除范围大小** |
| Q2 | **无障碍服务 `StellarAccessibilityService` 是否保留？** 它是 BootMode.ACCESSIBILITY 的载体，也与"自动拉起"相关 | 保留 / 删除。若 Q1 选删，此项大概率连带删 |
| Q3 | **`:provider`、`:api`、`:aidl`、`:shared`、`:userservice` 是否需进一步瘦身？** 它们是管理器与 server 通信的必需依赖，但其内部（如 `api/README.md`、第三方集成示例）可能仍有无关内容 | 仅删 demo + 文档／深入裁剪 API 面。后者风险高，建议**本期不动** |
| Q4 | **`api` 子模块的改动如何落地？** 是否允许在 `Stellar-API` 仓库提交删除 `demo/` 并更新父仓库指针？还是希望保留 demo 仅在父工程不引用？ | 需用户确认对独立仓库的提交权限与流程（关联 C3） |
| Q5 | **CI/发布工作流是否保留？** `.github/workflows/` 下有 4 个（含 `publish.yml`、`sync-release-gitee.yml` 发布/同步） | 保留发布能力／仅保留 manager CI／全删。影响 `build.gradle` 的签名与产物逻辑 |
| Q6 | **`AGENTS.md` / `CLAUDE.md` 处置**：保留并重写为瘦身后架构说明，还是删除？ | 建议保留重写（对后续 AI/开发者有价值） |
| Q7 | **删除后是否要求"可发布产物"？** 当前 `:manager` 依赖 NDK 编译 JNI，本机无 NDK。是否需要补充/安装 NDK 以完成真实构建验证？ | 影响 AC9 的验证强度 |

---

### 附：核实过的关键文件清单（供架构师直接复用）

- `settings.gradle`、`build.gradle`、`local.properties`、`.gitmodules`
- `manager/build.gradle`、`server/build.gradle`
- `manager/src/main/AndroidManifest.xml`
- `manager/.../MainActivity.kt`、`ui/features/manager/ManagerActivity.kt`
- `manager/.../ui/navigation/routes/NavigationRoutes.kt`
- `manager/.../ui/features/home/HomeScreen.kt`、`HomeViewModel.kt`
- `manager/.../ui/features/passwordswitch/PasswordSwitchScreen.kt`、`PasswordSwitchViewModel.kt`
- `manager/.../passwordswitch/PasswordSwitchService.kt`、`Settings.kt`、`ShellCommandRunner.kt`
- `manager/.../StellarApplication.kt`、`db/Daos.kt`、`StellarSettings.kt`
- `server/.../BinderSender.kt`、`binder/BinderDistributor.kt`、`ClientManager.kt`、`ClientRecord.kt`、`ConfigManager.kt`
- `README.md`、`AGENTS.md`、`api/settings.gradle`、`gradle/libs.versions.toml`、`manager/manager.versions.toml`
