# ChaosConnect

全新 Android 原生应用，当前版本为 **0.3.0：普通蓝牙连接 + 手环连接确认与权限**。

## 已实现

- 蓝牙列表同时接受 `Xiaomi Smart Band` 名称前缀及完整名称 `Chaos-Module`，已配对设备、搜索结果和连接检查采用同一规则。

- 首页、设备、我的三个标签；添加设备、设置、主题、关于二级页面。
- 独立的 **Miuix / Material 3** 页面树，运行中切换，导航状态保留。
- 悬浮玻璃 Dock：背景采样、模糊、边缘折射与高光、弹簧指示器；点击、按住滑动连续切页、可选松手切页和触感反馈。
- 横向 Pager 缓存相邻页面；连续改变目标时保留滚动速度。
- Miuix Nav 默认页面过渡与系统预见性返回。
- 默认 Miuix、跟随系统深浅色、开启动态取色、关闭纯黑。支持九种调色板、2021 / 2025 色彩规范、悬浮／普通底栏和模糊开关。主题色、纯黑、Dock 手势及触感设置由 DataStore 保存。
- 共享 ViewModel / StateFlow，Room 设备表以空数据启动。
- 指定 Chaos Logo 用于应用内展示和自适应启动图标。

CCN1 的帧、Protobuf 承载、会话、权限、文件操作及窗口传输已实现，并与模块生产 C 代码完成互通测试。参见 [协议接入说明](docs/CHAOSCONNECT.md)。

已接入 **Android公开RFCOMM接口 + 手环专用SPP服务**，不依赖小米私有认证载波。添加设备页支持附近设备权限、普通蓝牙搜索／系统配对和专用UUID连接。手环确认本次连接后才建立可操作的CCN1会话、保存真实设备；默认仅授予读取文件，写入、删除／重命名、开发者模式在手环单独设置，设备控制位为后续功能预留。

使用：手环 ChaosConnect → 发起蓝牙配对；手机添加设备 → 搜索手环 → 系统配对 → 手环允许本次连接。手机也可在系统蓝牙设置保持可见，让手环扫描并点选后发起配对。

已通过生产C互通和模拟器界面测试，**实体手机与手环无线配对、吞吐和功耗尚未实测**。文件API已提供，文件管理／远程控制前端页面尚未加入；当前界面提供连接、权限状态和开发者请求入口。生产环境没有模拟设备或虚构的连接成功数据。网页／HTTP服务未接入。

## 构建与运行

需要 JDK 21、Android SDK 37、Android SDK Build Tools；运行设备为 **Android 13 / API 33 及以上**，与 Miuix 0.9.4 玻璃库的最低版本一致。

```sh
./gradlew assembleDebug
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

在 Android Studio 中打开本项目根目录，设置 Gradle JDK 为 21 或更高，完成同步后运行 `app`。本地 `local.properties` 配置 `sdk.dir`，该文件不提交。

APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 工程结构

```text
app/src/main/java/com/chaosconnect/app/
  data/               DataStore 设置、Room 数据库
  protocol/           CCN1 帧、标准蓝牙承载、会话权限和文件 API
  ui/ShellViewModel   两套 UI 共享的状态、设置队列与协议接入
  ui/ShellNavigation 导航栈、Pager、滚动速度（位于主题分支之上）
  ui/ShellApp         路由与主标签容器
  ui/theme/           全局主题协调器
  ui/components/      Chaos 图形、Logo 和 Dock 内容适配
  ui/port/            InstallerX Revived 前端组件及许可头
  ui/page/md3/        Material 3 卡片、按钮、开关及页面
  ui/page/miuix/      Miuix 卡片、按钮、开关及页面
```

业务状态与设置只有一份。两套页面只使用各自设计系统的组件；共享的导航、Canvas 图形、文本和 Dock 使用 Compose Foundation。切换整个设计系统时，导航栈和 Pager 由主题分支外的状态持有。

## UI 移植与资源来源

根据用户后续明确要求，界面改为从本地 InstallerX Revived 源码移植。来源目录：

```text
/Users/ikun_cxkpro/Downloads/InstallerX-Revived-main
```

已移植／适配：

- `FloatingBottomBar`、`DampedDragAnimation`、`InteractiveHighlight` 与手势检测。
- `CombinedBackdrop`、`Lens`、`InnerShadow`、`Vibrancy` 玻璃效果。
- MD3 `SegmentedColumn`、`BaseWidget`、`RadioButtonWidget`、`SwitchWidget`、`ListItemMotion` 和相关布局。
- Miuix 开关控件；Scaffold、大标题、卡片和设置布局按照原前端接入。
- Material 3 Expressive 动效、字体、MaterialKolor 配色、色彩渐变和保留内容的主题协调方式。

ChaosConnect 的页面内容、设备数据层与应用入口独立实现，没有引入安装器、Root、Shizuku、APK 解析等业务。原始文件路径与哈希见 [移植清单](docs/installerx-port.json)。

源项目依赖 Miuix 私有仓库快照 `0.9.4-9d4b9c85-SNAPSHOT`，本工程适配公开稳定版 **0.9.4**：Backdrop 缩放参数类型和 RuntimeShader 支持函数已适配；快照版 EdgeStroke 改为稳定版玻璃高光；保留原底栏的双层内容采样、色散折射、拉伸、拖拽与按压效果。快照版新增的 OS4 GlassNavigationBar 未引入，当前移植的是源项目既有的 FloatingBottomBar 路径。导航使用相同的 Miuix Nav 默认过渡与预见性返回。

界面移植代码按 **GPL-3.0-only** 保留原版权与许可证，根目录 [LICENSE](LICENSE) 为 GPLv3 全文。Apache-2.0 的玻璃辅助文件保留各自许可头。发布本移植版本时需随包提供相应源码与许可信息。

Logo 按用户授权复制：

```text
/Users/ikun_cxkpro/Projects/Chaos-Module/tools/chaos.png
→ app/src/main/res/drawable-nodpi/chaos_logo.png
```

主要依赖：Kotlin / Jetpack Compose、Material 3 **1.5.0-rc01**、Miuix **0.9.4**、MaterialKolor **5.0.1**（与 Miuix 稳定版配套）、Room、DataStore、Coroutines。详见 `app/build.gradle.kts` 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 验证

协议测试覆盖编解码、请求关联、窗口、超时及真实 C 文件引擎互通。仪器测试覆盖：双风格的主标签与二级页面、主题切换时保留当前路由、Activity 重建后保留外观、按住 Dock 未松手连续切页、关闭即时切页后松手提交，以及真实设备表保持空状态。

手动检查入口：**我的 → 设置 → 主题**。切换 Miuix / Material 3、深浅色、动态取色、主题色和纯黑背景；回到主标签测试 Dock。

当前验证结果见 [verification/RESULTS.md](verification/RESULTS.md)。
