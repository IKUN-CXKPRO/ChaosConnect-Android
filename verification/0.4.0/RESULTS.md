# ChaosConnect 0.4.0 页面重写验证

日期：2026-11-15。Android API 37 模拟器。

- `assembleDebug`、`assembleDebugAndroidTest`：通过。
- `testDebugUnitTest`：20 tests，0 failures，0 skipped（设置 CHAOS_MODULE_ROOT，包含模块生产 C 互通）。
- `lintDebug`：0 errors，13 warnings。
- Android 仪器回归：10 tests 全部通过，结果见 `instrumentation.txt`。
- `git diff --check`：通过。

本次覆盖主页实时名称／连接状态、三标签跳转、Miuix / MD3 双主题、悬浮／普通导航、Dock 拖动、主题持久化、名称过滤，以及经测试专用协议 peer 驱动的授权后目录自动加载、17 项分页、进入／返回文件夹、STAT 文件信息、断开清空。

设备列表：无名、空白、MAC 地址占位、其他品牌／型号、大小写不匹配和前导空格均不接受；以 `Xiaomi Smart Band` 开头才显示。扫描、已配对记录、主动连接校验和最终显示层使用相同规则。测试 peer 只存在于 androidTest，生产 APK 不内置虚拟设备。

文件页目前提供浏览和文件信息，上传／下载等协议 API 保留但尚未提供页面按钮。真实无线扫描结果、配对和实体手环文件访问仍需真机验收。本次只更新 Android App，无需修改模块。

初轮集成测试发现滚动目录项被折叠顶栏遮挡；已将内容的顶部 inset 放到滚动视口外，重新通过完整 10 项 UI 测试。

APK SHA-256：`5fb27eb4bc7f4f457bc92fce136370673485a097b5d395d183b35116d2033c73`。
