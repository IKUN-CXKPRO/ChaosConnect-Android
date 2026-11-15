# ChaosConnect 0.3.1 验证结果

日期：2026-11-15。设备：Android API 37 Pixel 10 Pro 模拟器。

- `assembleDebug`：通过，产物 `app/build/outputs/apk/debug/app-debug.apk`。
- `assembleDebugAndroidTest`：通过。
- `lintDebug`：0 errors，13 warnings（依赖新版本提示、移植代码 API 级别分支及样式／资源建议）。
- 协议 JVM 测试：**17 项通过，0 跳过**。
- Android 仪器测试：**6 项通过**（两套 UI、主题持久化、即时／松手切页、悬浮／普通导航及协议状态／向量检查）。
- 保留0.2界面视觉基线截图；本轮0.3.1通过仪器回归，截图不代表真实无线连接。

## 协议测试

- `com.chaosconnect.app.protocol.ProductionInteropTest`：4 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.SessionTest`：4 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.AndroidAccessTest`：3 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.CodecTest`：4 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.ui.ConnectionStatusTest`：2 tests，0 failures，0 errors，0 skipped。

`ProductionInteropTest` 用 Kotlin 生产协议实现与 Chaos-Module 生产 C 编解码／VFS 文件引擎互通，使用真实临时文件。传输适配器通过测试进程 stdin/stdout 传送 protobuf，不能作为蓝牙无线链路证据。

已验证 256 KiB + 19 B 上传／下载、短写、CRC／偏移确认、目录和文件操作、提交失败保留原内容，以及开发者授权等待／允许／拒绝／撤销。

## 实际边界

Android标准蓝牙搜索、系统配对、SPP连接与手环授权已接入；原生固件接口和生产C/Kotlin协议互通已验证。实体手机与手环的配对、无线传输、吞吐和功耗未验证；设备控制位仅预留，文件管理/遥控前端未加入。生产APK不含测试peer或模拟设备。网页／HTTP未接入。

## APK

- SHA-256：`6cce1a31072e188db1345ce8f2c44595f18a128c0cecd6608e8073d1df63f3ce`
- 大小：41,460,823 bytes。

本轮新增连接等待/确认、只读默认、操作权限、C文件引擎撤权互通；详细机读记录见 `permission-results.json`。

## 0.3.1 已确认仍显示等待的修复

根因：配对页仅显示 `BluetoothState.message`，该字段在 socket.connect 成功时被写成
“请在手环确认”，未订阅后续 `GET_ACCESS` 授权结果。协议页和操作门禁实际上已经 CONNECTED。
现在两套 UI 通过相同的 presentation 从实时 SessionState 读取连接状态；扫描／socket
连接阶段使用蓝牙提示，连接、拒绝和断开后使用当前会话状态。重新连接清理旧会话显示。
开发者模式授权改为明确的独立标签，避免与连接确认混淆。

新增 JVM 回归通过自动心跳验证等待→允许→撤权，并注入旧载波提示确认其不能覆盖实时状态；
原生生产 C/Kotlin 互通也验证批准后的显示。
新增 `PairingConsentUiTest` 在真实 MainActivity/ViewModel/ChaosSession 上运行，
测试 peer 仅位于 androidTest：两套风格均覆盖等待、无需手动 ping 的确认更新、
开发者申请不降为等待连接、以及撤权断开。原有5项仪器测试全部通过。

这些验证覆盖状态同步和界面，不模拟无线；实体手机与手环需安装此 APK 后复测。
本项只需更新 Android App，手环授权策略与协议布局保持兼容。

## 0.4.0 页面重写

最新三页与扫描筛选验证见 [0.4.0 验证结果](0.4.0/RESULTS.md)：20 项 JVM 测试、10 项 UI 测试通过，编译及 Lint 通过。
