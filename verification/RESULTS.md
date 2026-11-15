# ChaosConnect 0.3.0 验证结果

日期：2026-11-15。设备：Android API 37 Pixel 10 Pro 模拟器。

- `assembleDebug`：通过，产物 `app/build/outputs/apk/debug/app-debug.apk`。
- `assembleDebugAndroidTest`：通过。
- `lintDebug`：0 errors，13 warnings（依赖新版本提示、移植代码 API 级别分支及样式／资源建议）。
- 协议 JVM 测试：**14 项通过，0 跳过**。
- Android 仪器测试：**5 项通过**（两套 UI、主题持久化、即时／松手切页、悬浮／普通导航及协议状态／向量检查）。
- 保留0.2界面视觉基线截图；本轮0.3通过仪器回归，截图不代表真实无线连接。

## 协议测试

- `com.chaosconnect.app.protocol.ProductionInteropTest`：4 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.SessionTest`：4 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.AndroidAccessTest`：2 tests，0 failures，0 errors，0 skipped。
- `com.chaosconnect.app.protocol.CodecTest`：4 tests，0 failures，0 errors，0 skipped。

`ProductionInteropTest` 用 Kotlin 生产协议实现与 Chaos-Module 生产 C 编解码／VFS 文件引擎互通，使用真实临时文件。传输适配器通过测试进程 stdin/stdout 传送 protobuf，不能作为蓝牙无线链路证据。

已验证 256 KiB + 19 B 上传／下载、短写、CRC／偏移确认、目录和文件操作、提交失败保留原内容，以及开发者授权等待／允许／拒绝／撤销。

## 实际边界

Android标准蓝牙搜索、系统配对、SPP连接与手环授权已接入；原生固件接口和生产C/Kotlin协议互通已验证。实体手机与手环的配对、无线传输、吞吐和功耗未验证；设备控制位仅预留，文件管理/遥控前端未加入。生产APK不含测试peer或模拟设备。网页／HTTP未接入。

## APK

- SHA-256：`694a4be190c44270f5036e00720d20d700e2d77d478f53f11517504a3e4e64e4`
- 大小：41,444,439 bytes。

本轮新增连接等待/确认、只读默认、操作权限、C文件引擎撤权互通；详细机读记录见 `permission-results.json`。
