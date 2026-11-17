# 0.6.0 验证

- 功能列表顶部新增安装投递包，系统文件选择器、最终名称确认、手环逐包授权、进度／取消、字体／图标提取与暂存提交。
- JVM：41 项通过、0 跳过，其中 9 项生产 Kotlin/C 互通（包括双资源安装、拒绝、允许与取消）。
- Pixel 10 Pro / API 37 模拟器：14 项通过、0 跳过；覆盖 Miuix / Material 3 两套界面和安装入口、确认文本、取消、运行状态及返回。
- `assembleDebug`、`lintDebug` 通过。lint 无错误；详细计数见 results.json。
- 测试容器使用 chaos-bandpack-web 的生产构建器生成，包含合成字体／图标数据；容器布局与命名规则来自真实工具，未执行包内Lua。
- 实体手机与手环的无线安装未在本轮实测。需要更新配套Chaos，先允许本次连接，再在手环确认指定包名。

产物保持默认 `app/build/outputs/apk/debug/app-debug.apk`，校验值见 results.json。
