# ChaosConnect Android 协议接入

当前实现目标：Chaos-Module 的 **CCN1 v1 文件桥**，核对来源为 `supervisor/bridge/chaos_connect.c`、`chaos_files.c`、`bridge.c` 和 `docs/24-FILE-BRIDGE.md`。协议实现使用独立编写的 Kotlin，没有移植设备端原生代码或固件地址。

## 已实现与当前边界

- 16 字节 CCN1 头、Little Endian、CRC32、请求／响应／事件。
- 小米 WearPacket 已定义的 OTHER=0、thirdparty_app=22、message_content=9 承载，包名 `ChaosConnect`，发送时 fingerprint 为空。
- HELLO、协商的数据块／路径／窗口、PING、开发者授权及设备停用事件。
- 目录分页、STAT、创建／删除目录、删除、复制、重命名、上传、下载、取消和链路回显。
- 最大 8 块在途，上传逐个校验 WRITE 的偏移与累计 CRC，全部确认后才 COMMIT；下载按偏移顺序输出，支持乱序应答。
- 上传最大 32 MiB；InputStream / OutputStream 不由协议层关闭。传输失败或取消时尝试 ABORT／CLOSE_READ；断链由设备租约完成最终清理。
- 连接前缀、承载 request ID、CCN1 request ID、opcode 和 CRC 一起验证。损坏包、其他命名空间与陈旧连接包不能完成当前请求。
- 心跳间隔通常 3 秒，等待授权时 1 秒；心跳请求 10 秒超时，普通操作 15 秒超时。超时断开，不自动重放可能有副作用的请求。请求编号在同一会话不复用。

**0.3.0新增标准蓝牙SPP/RFCOMM承载**，使用公开Android API进行发现、系统配对和连接；手环专用UUID为 `43434e31-0001-4a10-8000-4348414f5301`，不依赖小米认证/SAR。每条流记录为u32LE长度+完整WearPacket，支持分片/粘包，超限或队列错误显式断开。旧 `AuthenticatedTransport` 仍可接已认证载波；生产环境无模拟peer。

Android HELLO带 `CCA1`，检查设备能力位0x08，然后通过GET_ACCESS(0x42)查询连接授权和权限。连接默认等待手环确认（最多60秒），确认后才能使用文件API。手环设置独立控制读取、写入、删除和重命名、开发者模式；设备控制可读取和修改手环端Chaos设置。蓝牙配对成功不会跳过手环连接确认。

实体手机与手环的无线连接、吞吐、耗电尚未实测。前端文件管理/远程操控页尚未加入，文件API供后续页面使用；当前页面提供普通蓝牙连接、状态与权限展示和开发者模式请求。网页／BTPAN HTTP 功能未接入。

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `protocol/CcnCodec.kt` | 二进制帧、CRC、边界检查、Little Endian、UTF-8 路径 |
| `protocol/WearEnvelope.kt` | 正式 protobuf 承载、长度／varint／重复字段检查 |
| `protocol/ChaosTransport.kt` | 已认证链路接口及回调适配器，队列溢出显式失败 |
| `protocol/ChaosSession.kt` | 握手、关联请求、窗口、心跳、权限和生命周期 |
| `protocol/ChaosFiles.kt` | 文件 API、窗口传输、校验、提交与取消 |
| `ui/ShellViewModel.kt` | 双 UI 共享状态、承载接入点、本地向量校验 |

## 承载接入约定

标准蓝牙路径由 `BluetoothTransport` 管理安全RFCOMM socket。对于可选的小米载波适配器，下述约定仍要求先完成真实小米认证，再提供**解密、重组后的完整WearPacket protobuf**；不能把BLE notify片段直接交给本层，也不假设可以直接打开AstroBox内部会话。

```kotlin
val transport = AuthenticatedTransport(
    maximumPacketBytes = negotiatedPacketCapacity,
    sender = { protobuf ->
        // 在真实已认证 Xiaomi SARv2/L2 PB 链路上按顺序入队。
        // 入队失败必须抛出异常；该挂起操作应支持取消。
        authenticatedCarrier.enqueueWearPacket(protobuf)
    },
    closer = {
        // 解绑本次 CCN1 接收订阅、取消其发送队列。
        // 共享载波的实现负责区分自己的订阅与其他服务的所有权。
        authenticatedCarrier.detachChaosConnect()
    },
)
viewModel.attachAuthenticatedTransport(transport)

// 真实载波收包回调：
transport.receive(decodedWearPacket)
// 真实链路异常：
transport.failed(cause)
```

上面的 `authenticatedCarrier` 是待接入的载波 API 示例，并非本项目已经存在的库。`AuthenticatedTransport` 接入真实回调后，ViewModel才开始HELLO；收到合法HELLO、PING、GET_ACCESS且手环授予本次连接后才显示连接成功。

文件 API 使用 `viewModel.files`，仅握手完成后有值。`setDeveloper(true)` 返回非零 token 表示**等待设备确认**；开发者模式最终状态以设备 PING 为准。`setDeveloper(false)` 取消授权／撤销模式。普通文件操作在等待确认时被阻止。

## 固定互通向量

`CcnCodec.HELLO_VECTOR` 与 `PACKET_VECTOR` 对应模块 `docs/CHAOSCONNECT-V1.hex`：request ID 为 `0x1234`，packet ID 为 `0x12341234`。应用入口：**我的 → 设置 → ChaosConnect 协议 → 校验协议向量**。

该检查只验证本地编码与共享向量一致，**不代表已经连接设备，也不是无线测速**。

## 测试

```sh
# 纯 Kotlin 编解码／会话测试；未指定模块目录时生产 C 互通测试会标为跳过。
./gradlew testDebugUnitTest

# 指向用户本地模块仓库，启用生产 C 双向互通测试：
CHAOS_MODULE_ROOT=/path/to/Chaos-Module ./gradlew testDebugUnitTest --rerun-tasks

./gradlew assembleDebug assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest
```

生产 C 互通测试需要 Python 3 和 clang。测试脚本 `scripts/ccn_peer.py` 在临时目录编译模块的真实 codec、文件引擎及其 POSIX 测试 VFS，通过 stdin/stdout 与 Android 项目的生产 Kotlin 协议代码互通；不修改或重新构建固件，不启动网络服务。

测试包含：C / Kotlin 两层编码往返、256 KiB + 19 B 文件、短写、UTF-8 目录、复制／移动／删除、提前 EOF 与提交失败保留原文件、开发者允许／拒绝／等待、错误前缀／opcode、乱序响应、8 块窗口、停用通知与请求超时不重放。

真机认证、无线数据传输、带宽和功耗尚未验证。

## Android连接权限扩展

GET_ACCESS请求空载荷，应答16字节：schema=1、Android标志、连接策略、连接授权状态（各u8），
权限mask u32LE、确认token u32LE、granted u8、保留0三个字节。
连接策略0允许/1拒绝/2询问；授权状态0无/1等待/2允许/3拒绝/4超时。
权限位1读取、2写入、4删除和重命名、8开发者、16设备控制。
手机只查询，不能设置手环策略。授权完成前仅HELLO、PING、GET_ACCESS、DISCONNECT(0x43)可用。

Android本地检查防止无权限操作误触；手环在请求执行、复制循环和提交前独立检查。
手环的原始SPP来源被强制标记为Android，谎发旧插件空HELLO也不会绕过授权。
开发者权限打开后仍需原开发者模式策略许可；不因连接被允许自动启用开发者模式。

`SessionPhase.AWAITING_APPROVAL`表示载波已接通但尚不可操作；`files`在CONNECTED后才有值。
会话确认和开发者确认是独立token，最终结果以手环查询为准。断开、停用、重开撤销会话授权。
