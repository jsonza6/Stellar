# Stellar（精简分支）

> 单一用途工具：**通过 Root 或 ADB 启动特权服务获得 shell 权限，并用该权限切换系统的默认密码管理器**（凭据提供者 / 自动填充服务）。

Language: [English](README_en.md) | 中文

---

## 这是什么

这是一个从 Stellar（Shizuku 的深度定制分支）裁剪而来的**自用精简版本**。它只做两件事：

1. **启动服务，取得 shell / root 权限** —— 两条手动路径：
   - **Root**：经 `libsu` + `libchid` 降权到 shell 后启动服务。
   - **ADB 无线调试**：应用内完成配对（mDNS 发现 + 配对 + 连接）后拉起服务。
2. **用该权限驱动密码管理器切换** —— 读写 `settings secure` 中的
   `credential_service`、`credential_service_primary`、`autofill_service` 三项，
   并具备**事务语义**：写前快照比对、逐项写入、读回校验、失败逆序回滚。

界面只有两个页面：**启动（Home）** 与 **密码切换（PasswordSwitch）**。

## 构建

```bash
# 构建 debug APK
./gradlew :manager:assembleDebug

# 构建 release APK（需要根目录 signing.properties，缺失时回退 debug 签名）
./gradlew :manager:assembleRelease

# 仅构建特权服务端逻辑
./gradlew :server:assemble
```

Release 产物输出到 `out/apk/`，mapping 输出到 `out/mapping/`。

签名配置（`signing.properties`，不入库）：`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PASSWORD`。

## 模块结构

```
manager/    Android 应用（UI + 启动器 + ADB 协议栈 + 密码切换），applicationId: roro.stellar.manager
server/     特权服务端逻辑，运行在 ADB / Root 进程中
api/        客户端 SDK 子模块（独立仓库 Stellar-API）
  ├── aidl/        AIDL 接口（IStellarService / IRemoteProcess / IRemotePtyProcess ...）
  ├── api/         客户端 API 入口（Stellar.kt / StellarHelper.kt）
  ├── provider/    StellarProvider（ContentProvider，接收服务端 Binder）
  ├── shared/      共享常量（StellarApiConstants）
  └── userservice/ UserService 框架（密码切换的特权执行体由此启动）
```

## 核心数据流

1. **启动**：`manager/startup/` 经 Root（`Chid` 降权）或 `manager/adb/` 经 ADB 拉起 `server`。
2. **Binder 分发**：`server/binder/BinderDistributor` + `server/BinderSender` 通过 ContentProvider
   把 Binder 投递给管理器（`manager/StellarManagerProvider`）。
3. **客户端连接**：`api/provider/StellarProvider` 接收 Binder → `api/api/Stellar.kt` 封装调用。
4. **密码切换**：`PasswordSwitchViewModel` 通过 `StellarUserService` 以 shell / root 身份启动
   `PasswordSwitchService`，后者经 AIDL 完成读写与回滚。

## 与上游的差异（本分支已移除）

- Shizuku 兼容层（`shizuku/` 模块与 `server/.../shizuku/`）
- 第三方应用客户端授权（不再弹出授权界面，不再向第三方授予权限）
- 应用内更新检查、终端、命令快捷方式、Apps 授权管理页、设置页、日志页
- 开机自启 / 无障碍保活 / 通知重试 / 开机脚本 / 进程守护
- 除英文与简体中文外的多语言资源

> AIDL 契约中仍保留 `isShizukuCompatEnabled` / `setShizukuCompatEnabled` / `isDaemonEnabled` /
> `setDaemonEnabled` 等方法，服务端以无害桩实现，以保证与 `api/` 子模块的 AIDL 一致。

## 许可证

见 [LICENSE](LICENSE)。
