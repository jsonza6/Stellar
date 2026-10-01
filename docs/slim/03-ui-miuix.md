# 界面改造为 Miuix（HyperOS / MIUI）· 说明

> 文档编号：03-ui-miuix
> 参考：**SukiSU 管理器**的界面结构（底部导航 + 分组卡片 + 状态标签）
> 验证：本地编译 + GitHub Actions 真实构建（**Manager CI #11 → success**）

---

## 1. 结果

manager 的 UI 从 **Material 3** 整体迁移到 **Miuix（HyperOS / MIUI 设计语言）**。

| 项 | 结果 |
|---|---|
| 主题 | `MiuixTheme` + `ThemeController`（替换 `MaterialTheme`） |
| 导航 | `NavigationBar` / `NavigationRail` / `TopAppBar` / `SmallTopAppBar` 全部换 Miuix |
| 图标 | 统一 `MiuixIcons.*`，**不再使用任何 Material 图标** |
| 对话框 | `StellarDialog` → Miuix `WindowDialog`（独立窗口层） |
| Material 3 残留 | **0**（全模块 grep 无 `androidx.compose.material3`） |
| 构建验证 | 本地 `:manager:compileReleaseKotlin` ✅、`:manager:assembleRelease`（含 R8）✅、CI #11 ✅ |

---

## 2. 版本决策（最关键的一环）

Miuix 是 Compose Multiplatform 库，对工具链有硬性要求，形成一条约束链：

| Miuix | 依赖 Compose | Kotlin 元数据 | 要求 AGP |
|---|---|---|---|
| 0.9.4 | 1.12.0 | 2.4.0 | **≥ 9.1.0** |
| 0.9.3 / 0.9.2 | 1.11.1 | 2.4.0 | ≥ 8.6.0（但需 Kotlin 2.4） |
| **0.9.1（采用）** | **1.11.0** | **2.3.0** | **≥ 8.6.0** |
| 0.9.0 | 1.10.3 | 2.3.0 | ≥ 8.6.0 |

- 选 **0.9.4** 会连带把 AGP 从 8.13.2 推到 9.1.0（还要升 Gradle），风险与收益不成比例。
- 最终选 **Miuix 0.9.1 + Kotlin 2.3.21**，**AGP / Gradle / Compose BOM 全部不动**。

> 结论：改 Miuix 版本前，先看它的 `kotlin-stdlib` 版本、`org.jetbrains.compose.foundation`
> 版本，以及 AAR 里 `META-INF/com/android/build/gradle/aar-metadata.properties` 的
> `minAndroidGradlePluginVersion`。

0.9.1 与 0.9.4 的 API 差异（本分支已按 0.9.1 编码）：
`NavigationBarItem` / `NavigationRailItem` **没有** `colors` / `badge` 参数；
`NavigationRail` **没有** `expanded` 重载；**没有** `Home` 图标（改用 `Play`）；
没有 Badge / Tooltip / BreadcrumbBar。

---

## 3. 具体改动

### 3.1 依赖与工具链

- `settings.gradle`：Kotlin `2.2.0 → 2.3.21`（`org.jetbrains.kotlin.plugin.compose` 同步）
- `manager.versions.toml`：新增 `miuix` bundle（`miuix-ui-android` / `-preference` / `-icons` 0.9.1）
- 移除：`capsule`（Kyant0，被 Miuix squircle 取代）、`material-icons-extended`、`ui-tooling-preview`
- `settings.gradle` 的 jitpack `includeGroup("com.github.Kyant0")` 一并移除

### 3.2 主题（含一次返工）

**第一版走错了**：沿用了旧的 `dynamicColor = true`，映射到 `ColorSchemeMode.Monet*`，
取的是**系统 Material You 壁纸色**——组件虽然是 Miuix 的，配色却还是 MD3，
所以第一版"看起来还是 MD3"。已改为直接用 Miuix 自带调色板：

```kotlin
ThemeController(colorSchemeMode = when (themeMode) {
    LIGHT -> ColorSchemeMode.Light
    DARK  -> ColorSchemeMode.Dark
    AUTO  -> ColorSchemeMode.System
})
```

Miuix 自带调色板（来自 Miuix `Colors.kt`）：

| 角色 | 浅色 | 深色 |
|---|---|---|
| 页面底色 `surface` | `#F7F7F7` | `#000000` |
| 卡片 `surfaceContainer` | `#FFFFFF` | `#242424` |
| 主色 `primary` | `#3482FF`（HyperOS 蓝） | `#277AF7` |
| 提示容器 `tertiaryContainer` | `#EAF2FF` | `#2B3B54` |
| 饱和填充 `primaryContainer` | `#5D9BFF` | `#338FE4` |

**同时清掉全部 MD3 泄漏点**：

- `themes.xml` 不再继承 `Theme.Material3.DayNight.NoActionBar`，改为
  `android:Theme.Material.Light.NoActionBar`（night 用 `android:Theme.Material.NoActionBar`）
- 新增 `values/colors.xml` + `values-night/colors.xml` 的 `app_window_background`
  （浅色 `#F7F7F7` / 深色 `#000000`），替换启动图原来的 M3 底色 `#FEF7FF` / `#1C1B1F`
- 删除 `StellarApplication` 里的 `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO)`
  （对 `ComponentActivity` 是空操作，且与深色模式冲突）
- 移除依赖：`com.google.android.material`、`androidx.appcompat`、`androidx.compose.material3`

**语义色用法修正**（第一版用错，会显得不像 Miuix）：

| 位置 | 错误用法 | 修正 |
|---|---|---|
| 服务状态卡 | `primaryContainer` 当大卡片底（Miuix 里它是饱和填充蓝 `#5D9BFF`，过重） | 运行中 `tertiaryContainer`；未运行 `surfaceContainer` |
| 入口卡片图标底座 | `primary` 蓝图标 + `primaryContainer` 饱和蓝底（几乎看不见） | `onPrimaryContainer`（白）图标 + `primaryContainer` 底 = HyperOS 蓝色圆角方块 |
| 时间线 WARNING | `secondary`（浅色 `#E6E6E6`，几乎不可见） | 新增应用自定义 `StellarColors` 琥珀色 |
| 时间线运行中圆点 | 圆点是主色，`CircularProgressIndicator` 默认前景色也是 `primary`（蓝底蓝圈） | 前景改 `onPrimary` + 30% 轨道 |

Miuix 调色板没有 warning（琥珀）语义色，而启动流程会用 `WARNING` 表达
「端口未检测到 / 需要配对」，因此新增 `ui/theme/SemanticColors.kt`：

```kotlin
object StellarColors {
    // 浅色 #FFF3E0 / #7A4F00 / #E08A00；深色 #33270D / #F5C77E / #F0B354
    val warningContainer: Color
    val onWarningContainer: Color
    val warningAccent: Color
}
```

深浅判定取当前 Miuix 主题背景的 `luminance()`，这样 `ThemeMode` 覆盖系统深色时也正确。

保留 `ThemeMode`（浅色/深色/跟随系统）与状态栏图标明暗同步；顺带移除了已废弃的
`window.statusBarColor` 写法（边到边模式由 `enableEdgeToEdge` 处理）。

### 3.3 导航外壳

- `TopAppBarProvider` 现在提供 `MiuixScrollBehavior`（`LocalTopAppBarScrollBehavior`），
  页面用 `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` 与顶栏联动
- `MainActivity` 用 Miuix `Scaffold` + `NavigationBar`（竖屏）/ `NavigationRail`（横屏）
- `NavigationRoutes` 的图标改为 `MiuixIcons.Play` / `MiuixIcons.Lock`

### 3.4 页面

| 页面 | 改法 |
|---|---|
| **Home** | Miuix `Scaffold` + `LazyColumn`；状态卡用高亮 `Card`，启动入口用「图标底座 + 标题 + 说明 + 按钮」的 `ActionCard`，分组标题用 `SmallTitle` |
| **PasswordSwitch** | `BasicComponent` 承载每一行（标题/摘要/前导图标/尾随动作），选择器改 `WindowBottomSheet` + Miuix `TextField` 搜索，确认弹窗走 `StellarDialog` |
| **StarterScreen** | 时间线结构保留（圆点 + 连接线 + 卡片），**ViewModel 逻辑一行未动**，仅替换 UI 组件与图标 |

### 3.5 清理

- 删除旧设计系统：`ui/theme/Shape.kt`、`ui/theme/Spacing.kt`、`ui/components/G2RoundedCorners.kt`
- `ThemePreferences` 去掉已无入口的 `StartPage` 与两个 display-name 辅助函数
- 字符串：新增 `home_start_section` / `action_refresh`；删除失效的 `theme_light|dark|auto`、`nav_apps`、`nav_terminal`
  （最终 150 条，中英完全对齐，无未引用项）

---

## 4. 构建验证

| 阶段 | 命令 / 运行 | 结果 |
|---|---|---|
| Kotlin 编译 | `./gradlew :manager:compileReleaseKotlin` | ✅ |
| Release + R8 | `./gradlew :manager:assembleRelease`（本地临时禁用 native 构建） | ✅ |
| 完整 APK | GitHub Actions **Manager CI #11**（`884b9d6`） | ✅ **success**，含 NDK JNI + R8 + 资源收缩 |

### 过程中修掉的一个真实构建失败

CI #10 在 `minifyReleaseWithR8` 失败：

```
Missing class androidx.window.sidecar.SidecarDeviceState
  (referenced from ... SidecarDeviceState ...DistinctElementSidecarCallback...)
```

Miuix 经 `material3-window-size-class` 间接引入 `androidx.window`，其 `sidecar.*`
只在带 WindowManager sidecar 的设备上存在，release 混淆阶段被判定为缺类。
修复：`proguard-rules.pro` 增加 `-dontwarn androidx.window.sidecar.**`，
并顺手删掉已失效的 Shizuku / 守护进程 keep 规则。

---

## 5. 已知遗留

1. **PasswordSwitchScreen 的文案仍是硬编码中文**。本次只做视觉迁移，没动文案；
   在默认英文语言下会显示中文，需要单独一轮把硬编码文案抽成 `stringResource`。
2. **视觉效果未在真机确认**。本地与 CI 只能证明「能编译、能打包」；
   布局细节（卡片间距、时间线对齐、动态取色）建议装到设备上看一眼。
3. `MiuixIcons` 0.9.1 没有 `Home` / `Warning` / `Stop` 等图标，已用 `Play` / `Info` /
   `Blocklist` / `Close` 等替代；若在意语义可换用 0.9.4（需先升 AGP）。
