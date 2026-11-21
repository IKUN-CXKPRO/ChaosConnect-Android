# Third-party notices

ChaosConnect ports the frontend components from InstallerX Revived as explicitly
requested by the user. Source copyright and license headers are preserved.
The root LICENSE contains GPL-3.0. Apache-2.0 helper files retain that license.
The project also consumes the following public libraries.

| Component | License | Upstream |
| --- | --- | --- |
| InstallerX Revived frontend | GPL-3.0-only, with Apache-2.0 helper portions | https://github.com/wxxsfxyzm/InstallerX-Revived |
| MaterialKolor | MIT (utilities include Apache-2.0 portions) | https://github.com/jordond/MaterialKolor |
| AndroidX (Compose, Material 3, Activity, Lifecycle, Room, DataStore) | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Miuix UI / blur / nav 0.9.4 | Apache-2.0 | https://github.com/compose-miuix-ui/miuix |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | https://github.com/JetBrains/kotlin |

Miuix: Copyright 2024–2026 compose-miuix-ui contributors. Library sources are not
vendored into this repository. The project uses the published Maven artifacts.

The Chaos logo was supplied by the user from their local Chaos-Module project and
copied with their explicit permission. This notice does not assign its ownership
or grant a new license to it.

Full Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

InstallerX Revived: Copyright (C) 2023–2026 iamr0s and InstallerX Revived contributors.
The ported drag animation and gesture code also credits KernelSU contributors.
Liquid helpers credit compose-miuix-ui contributors and Kyant0/AndroidLiquidGlass.
See each file under ui/port for the original license and attribution headers.
The source manifest is docs/installerx-port.json.

## Chaos 投递包制作

`bandpack/` 移植自 [chaos-bandpack](https://github.com/WenHuaYiYang/chaos-bandpack)，
源版本和文件摘要见 `bandpack/SOURCE.json`，按 AGPL-3.0 提供；许可全文保存在
`bandpack/LICENSE`，也随 APK 内置，并可在制作页的开源许可中查看。
本次改动包括库模块接入、宿主导航及限定 10 Pro 制作目标。原有版权和第三方许可保留。
主项目原有 GPL-3.0 部分仍保留原许可；与该 AGPL 模块组合发布时须遵守其对应义务。
字体、MingCute 图标等各自的声明保存在 `bandpack/THIRD_PARTY_NOTICES.md` 和
`bandpack/third_party/` 中。
