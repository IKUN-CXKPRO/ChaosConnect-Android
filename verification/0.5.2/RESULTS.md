# Android 0.5.2 Dock 验证

参照用户给出的 InstallerX Revived 截图放大悬浮底栏，保留主页、连接、功能列表、我的四项导航。

- Dock 从 264×56dp 改为 312×64dp，单项最小宽度 76dp，选中胶囊 56dp 高。
- 图标 20→24dp，文字 10→11sp；拖拽按压缩放、选中采样层及指示器同步适配。
- 玻璃底色不透明度由 40% 改为深色 18%／浅色 22%；普通模糊由 65% 改为深色 35%／浅色 45%。保留背景采样、折射、高光与实时切页。
- `assembleDebug`、`lintDebug` 通过，lint 0 错误、13 条警告。
- Pixel 10 Pro / API 37 模拟器 13 项界面回归全部通过，无跳过，覆盖两种设计系统、Dock 尺寸、按住滑动切页、松手切页、原生底栏切换与各功能路由。
- 截图已检查深色玻璃与浅色模糊的显示，未见标签截断或指示器错位。截图为模拟器界面，颜色随系统取色与主题变化。
- 独立构建使用已提交的 AGP 9.4.1，未合入本地工具链降级或历史文档修改。

截图：

- [Miuix 浅色](miuix-home.png)
- [Miuix 深色](miuix-home-dark.png)
- [Material 3 浅色](md3-home.png)
- [Material 3 深色](md3-home-dark.png)

验证命令（JDK 为 Android Studio 自带 JBR）：

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest
```

APK：`ChaosConnect-0.5.2-debug.apk`，41690199 字节。

SHA-256：`2712585be35754f0e430a7e6c5b248fa9b121ea3498942b7bc93f4d39f744fc2`。
