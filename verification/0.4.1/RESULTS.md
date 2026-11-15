# 0.4.1 设置与关于入口恢复

- 主页、文件管理、连接：右上角恢复带文字的“设置”按钮。
- 连接页底部：恢复“设置、主题、关于 ChaosConnect”直接入口。
- 原设置、主题、关于、协议页与返回栈保留，两套 UI 均接入真实路由。
- `assembleDebug assembleDebugAndroidTest` 通过，`git diff --check` 通过。
- 5 项 ShellFlowTest 全部通过；覆盖两种风格下三个新增文字入口、文件页设置入口、主题切换与返回、Dock 与设置持久化。

APK：app/build/outputs/apk/debug/app-debug.apk，版本 0.4.1 / code 6。
