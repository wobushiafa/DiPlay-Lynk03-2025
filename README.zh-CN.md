# DiPlay-Lynk03-2025（2025 款领克 03 CarPlay 适配版）

[简体中文](README.md) · [English](README.en.md) · [繁體中文（上游）](README.zh-TW.md)

本项目基于上游 [DiPlay](https://github.com/shihabal3amri/DiPlay)（v0.2.22），专为 **2025 款领克 03** 车机（高通 SA8155 / 德赛西威 Android 11，代号 `geely_fx11` / 型号 `CS11_A4`）进行专项适配，无需 CarPlay 盒子即可在车机上运行有线及无线 CarPlay。

![DiPlay 主界面](site/assets/home.png)

## 当前适配状态

| 功能 / 连接方式 | 当前状态 | 说明 |
| --- | --- | --- |
| **有线 USB CarPlay** | ✅ 可正常使用（推荐） | 画面（实测 2560×1600 @ 30fps HEVC）、触控、48 kHz LPCM 持续音频均正常 |
| **无线 Wi-Fi Direct CarPlay** | ⚠️ 已打通连接，**声音暂不可用** | 已通过车机另一张独立网卡实现建组、连接、画面与触控，但**目前存在声音无法播放的问题** |
| **领克音频焦点适配** | ✅ 已适配 | 自动适配吉利/领克音频焦点机制，避免自抢焦点导致静音 |
| **领克 OEM 返回车机按钮** | ✅ 已适配 | CarPlay 桌面默认显示 `LYNK & CO` 图标与「领克」标签 |
| **方向盘按键与麦克风** | ✅ 已适配 | 启用领克/吉利私有方向盘按键订阅与通话音频源麦克风上行 |

## 核心实现细节

### 1. 无线 Wi-Fi Direct：调用车机另一张独立网卡
- **双 Wi-Fi 硬件架构**：2025 款领克 03 车机具备双网卡架构，系统日常联网/热点并不使用 Android 侧的另一张 NXP Wi-Fi 网卡（`wlan0` / `p2p0`），且该网卡默认处于关闭状态。
- **自动启用独立网卡**：检测到领克/吉利车机（`GeelyHeadUnit`）后，当切换到或使用 Wi-Fi Direct 模式时，应用会自动开启车机 Android 侧的这张独立 Wi-Fi 网卡（优先调用 `WifiManager.setWifiEnabled`，若系统限制则自动回退通过本地 ADB 执行 `svc wifi enable`）来创建 `p2p0` 群组，不占用车机原本的联网通道。
- **多网卡时钟绑定**：将 `NtpClock` 绑定至当前 AirPlay 会话所在的网卡地址，避免多网卡环境下时钟同步报文路由异常。
- **热点状态兼容**：兼容吉利/领克车机在 Android 层隐藏热点 AP 状态的情况（`CarHotspotSettings` / `ManualHotspotInterfaces` / `ManualHotspotReadiness`）。

### 2. 有线 USB CarPlay：NCM 桥接层合成 IPv6 邻居通告
- Android 的 `tun0` 属于三层 NOARP 设备，高通 SA8155 Android 11 内核不会在 `tun0` 上主动回复 iPhone 发来的 IPv6 邻居请求（ICMPv6 Neighbor Solicitation, `type=135`），导致 iPhone 无法解析车机链路本地地址（`fe80::2`），连接一直卡在“正在打开 CarPlay…”。
- 本项目在 NCM 桥接层（`Ipv6NcmBridge` / `EthernetIpv6Codec`）直接拦截针对车机地址的 `icmp6=135` 请求并合成 `icmp6=136`（Neighbor Advertisement）响应回传，彻底打通有线 USB CarPlay。

### 3. 领克车机音频焦点与系统集成
- **音频焦点**：检测到领克/吉利车机后，默认开启媒体与导航提示音音频焦点，并默认关闭媒体按键的重复音频焦点申请，避免触发 `ExtCarAudioFocus` / `com.geely.mediacenterservice` 自抢焦点静音。
- **OEM 车标与名称**：默认将 CarPlay 应用列表中的“返回车机”图标替换为 `LYNK & CO` 图标（`ic_car_home_lynk.png`），名称设为 `领克`。
- **硬件按键与麦克风**：通过 `GeelyHeadUnit` 自动启用领克/吉利私有方向盘按键服务（`GeelyWheelKeys`）与通话音频源麦克风上行（`MicrophoneUplink`）。

## 已知问题

- **Wi-Fi Direct 声音无法播放**：目前无线 Wi-Fi Direct 连接成功后画面与触控均正常，但音频仍无法正常播放（无线 `type=102` AAC-LC 媒体流在约 2.3 秒后停发 / 无声音输出）。**日常使用请优先选择有线 USB CarPlay。**
- **非 Apple MFi 认证**：与上游 DiPlay 一致，本项目为实验性开源实现，并非 Apple 认证产品，不保证兼容所有未来 iOS 版本或其他车型。

## 安装与使用简述

1. **安装位置**：将编译或下载的 APK 安装在**车机**上（非 iPhone）。
2. **有线 CarPlay（推荐）**：使用支持数据传输的 USB 线连接车机数据口与 iPhone，打开应用并授予 USB 及相关权限即可连接。
3. **无线 Wi-Fi Direct（测试中）**：在设置中选择 Wi-Fi Direct 模式，应用会自动尝试开启车机 Android 侧独立 Wi-Fi 网卡（若系统拒绝直接开启，需提前授权本地网络 ADB 以便执行 `svc wifi enable`）。
4. **诊断日志**：如遇到问题，可在 **设置 → 诊断 → 保存诊断报告** 导出本地日志排查（日志不会自动上传）。

更多通用文档（继承自上游 DiPlay）：
- [安装与连接](docs/INSTALL.md)
- [从源码构建](docs/BUILD.md)（主应用请选择 `mobile` 模块）
- [兼容性与故障排查](docs/COMPATIBILITY.md)
- [隐私与诊断报告](docs/PRIVACY.md)
- [第三方声明与许可](docs/THIRD_PARTY_NOTICES.md)

## 致谢与开源协议

- 本项目基于 [DiPlay](https://github.com/shihabal3amri/DiPlay)（v0.2.22）进行 2025 款领克 03 专属适配。
- 上游 DiPlay 基于 [xcertplay](https://github.com/shilapi/xcertplay)（GPL-3.0），主页与设置界面改编自 [DiAuto](https://github.com/shihabal3amri/DiAuto)（AGPL-3.0），相关许可协议保留在 `docs/licenses` 中。分发修改版本时请遵守并保留对应开源许可声明。
- CarPlay 及其图标归 Apple Inc. 所有，领克（LYNK & CO）商标归其各自权利人所有；本项目与 Apple、吉利/领克或比亚迪均无官方关联。

## 本地发布打包说明

本 Git 仓库与源码包不包含任何配件认证私钥或 Android 签名密钥，普通源码/CI 构建默认不打包运行时身份资产；本地发布构建需自行指定外部资产目录，详见 [从源码构建](docs/BUILD.md)。
