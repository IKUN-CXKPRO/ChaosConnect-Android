# Android 0.5.1 验证

- 构建基于原始提交 `c134ed9`，使用仓库已提交的 AGP 9.4.1；未带入本地工具链降级及其他历史文档修改。
- 底栏：主页、连接、功能列表、我的；悬浮 Dock 264×56dp，图标 20dp。主页卡片与功能列表均能进入文件管理、NSH终端，返回保留来源。
- 测速入口：我的 → 设置 → 连接测速。持续运行，无测试时间／数据量上限，手动停止；切换应用内页面仍继续。预热不计数，按校验通过的回显有效载荷统计，显示 KiB/s、字节数与耗时，不写入手环文件。
- JVM：35 项通过、0 跳过，其中 6 项为生产 C / Kotlin 互通。
- 模拟器：13 项通过、0 跳过，Pixel 10 Pro / API 37，覆盖双设计系统、四项 Dock、主页卡片、功能页返回、设置持久化、连接确认、文件列表和测速按钮／数据显示。
- 持续测试使用虚拟时钟，超过 180 秒、8 MiB、65535 个 ECHO 请求后仍运行；验证停止后不继续提交、活动写入／预热中取消不破坏连接及回显损坏处理。另验证 65536 次连接状态轮询不耗尽文件操作编号。
- `assembleDebug` 通过，lint 0 错误、13 条警告。
- 首页图片：`miuix-home.png`、`md3-home.png`，来自模拟器界面测试。测速 UI 测试使用明确的测试统计，不代表实机测速结果。
- 实体无线吞吐、功耗及手机／手环真实 NSH 尚未在本轮测量；主机 C 互通不代表蓝牙性能。

命令（JDK 使用 Android Studio 自带 JBR）：

```sh
CHAOS_MODULE_ROOT=/Users/ikun_cxkpro/Projects/Chaos-Module ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

APK：`ChaosConnect-0.5.1-debug.apk`，42065109 字节。

SHA-256：`399bbac13c04398924c5839c2073dd929ee8928c81ec4ce1d8a86591179951ea`。
