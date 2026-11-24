# DiPlay-Lynk03-2025 (2025 Lynk & Co 03 CarPlay Adaptation)

[简体中文](README.md) · [English](README.en.md) · [繁體中文 (Upstream)](README.zh-TW.md)

Adapted from upstream [DiPlay](https://github.com/shihabal3amri/DiPlay) (v0.2.22) specifically for the **2025 Lynk & Co 03** Android 11 head unit (Qualcomm SA8155 / DesaySV, product `geely_fx11`, model `CS11_A4`). Enables wired and wireless CarPlay directly on the head unit without a dongle.

![DiPlay home](site/assets/home.png)

## Current Status

| Feature / Transport | Status | Notes |
| --- | --- | --- |
| **Wired USB CarPlay** | ✅ Working (Recommended) | Verified 2560×1600 @ 30fps HEVC video, touch input, and continuous 48 kHz LPCM audio |
| **Wireless Wi-Fi Direct CarPlay** | ⚠️ Connected, **No Audio** | Group creation, connection, video, and touch work via the head unit's second Wi-Fi NIC, but **audio playback does not work yet** |
| **Lynk & Co Audio Focus** | ✅ Adapted | Configured for Geely/Lynk audio focus behavior to prevent self-eviction silence |
| **Lynk & Co OEM Car Button** | ✅ Adapted | Defaults the CarPlay "return to car" button to the `LYNK & CO` badge and `领克` label |
| **Steering Wheel Keys & Mic** | ✅ Adapted | Enables Geely/Lynk private wheel-key subscription and voice-communication mic uplink |

## Implementation Details

### 1. Wireless Wi-Fi Direct via the Head Unit's Second Wi-Fi NIC
- **Dual-Wi-Fi Hardware Architecture:** The 2025 Lynk & Co 03 head unit has a dual-Wi-Fi architecture. The vehicle's regular connectivity/hotspot does not use the separate Android-side NXP Wi-Fi radio (`wlan0` / `p2p0`), which is turned off by default.
- **Automatic Second-NIC Activation:** When a Geely/Lynk head unit (`GeelyHeadUnit`) is detected and Wi-Fi Direct mode is selected, the app automatically enables this separate Android-side NXP Wi-Fi NIC (trying `WifiManager.setWifiEnabled` first, and falling back to `svc wifi enable` over local ADB if restricted) to bring up the `p2p0` group without disrupting the head unit's primary network.
- **Interface-Bound NTP Clock:** Binds `NtpClock` to the active AirPlay session's local interface address to prevent timing-packet routing issues across multiple interfaces.
- **Hotspot State Compatibility:** Handles Geely/Lynk head units hiding hotspot AP state at the Android layer (`CarHotspotSettings`, `ManualHotspotInterfaces`, `ManualHotspotReadiness`).

### 2. Wired USB CarPlay: Synthesized IPv6 Neighbor Advertisement over NCM
- Android `tun0` interfaces are layer-3 NOARP devices, and the Qualcomm SA8155 Android 11 kernel does not emit ICMPv6 Neighbor Advertisements on `tun0`. Without a reply to its Neighbor Solicitation (`icmp6=135`), the iPhone cannot resolve the head unit's link-local address (`fe80::2`) and stalls at "Opening CarPlay…".
- `Ipv6NcmBridge` and `EthernetIpv6Codec` intercept `icmp6=135` Neighbor Solicitations targeting the host IPv6 address and reply directly with a synthesized `icmp6=136` Neighbor Advertisement, allowing wired USB CarPlay to connect reliably.

### 3. Lynk & Co Audio Focus & Head-Unit Integration
- **Audio Focus:** On Geely/Lynk head units, media audio focus and navigation-prompt audio focus are enabled by default, while duplicate media-key audio focus requests are disabled by default to prevent self-eviction in `ExtCarAudioFocus` / `com.geely.mediacenterservice`.
- **OEM Car Button:** Replaces the default "back to car" icon with the `LYNK & CO` badge (`ic_car_home_lynk.png`) and sets the default label to `领克`.
- **Wheel Keys & Microphone:** Automatically enables Geely/Lynk vendor wheel-key handling (`GeelyWheelKeys`) and voice-communication microphone uplink (`MicrophoneUplink`).

## Known Issues

- **No Audio over Wi-Fi Direct:** Wireless Wi-Fi Direct establishes a CarPlay session with working video and touch, but audio playback is currently broken (wireless `type=102` AAC-LC media stops after ~2.3 s / produces no audible output). **Use wired USB CarPlay for full audio and daily driving.**
- **Not Apple MFi Certified:** Like upstream DiPlay, this is an experimental open-source project and not an Apple-certified product. Compatibility with future iOS updates or other vehicles is not guaranteed.

## Quick Start

1. **Install on the head unit** (not the iPhone).
2. **Wired CarPlay (Recommended):** Connect the iPhone to the head unit's data USB port using a data-capable cable, open the app, and grant USB and required permissions.
3. **Wireless Wi-Fi Direct (Experimental):** Select Wi-Fi Direct in Settings. The app will attempt to enable the Android-side NXP Wi-Fi NIC automatically (if blocked by system permissions, authorize local network ADB so it can run `svc wifi enable`).
4. **Diagnostics:** To inspect issues, use **Settings → Diagnostics → Save diagnostic report**. Reports stay local unless you choose to share them.

Additional upstream documentation:
- [Install and connect](docs/INSTALL.md)
- [Build from source](docs/BUILD.md) — select the `mobile` module for the main app.
- [Compatibility and troubleshooting](docs/COMPATIBILITY.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

## Source, Credits & Licenses

- Adapted for the 2025 Lynk & Co 03 from upstream [DiPlay](https://github.com/shihabal3amri/DiPlay) (v0.2.22).
- Upstream DiPlay is based on [xcertplay](https://github.com/shilapi/xcertplay) (GPL-3.0), with home/settings UI adapted from [DiAuto](https://github.com/shihabal3amri/DiAuto) (AGPL-3.0). Licenses are included in `docs/licenses`; preserve these notices when distributing modifications.
- CarPlay and its icon belong to Apple Inc. Lynk & Co trademarks belong to their respective owners. No affiliation with or endorsement by Apple, Geely/Lynk & Co, or BYD is implied.

## Local Release Packaging

This repository excludes all accessory identity private keys and Android signing keys; standard source/CI builds omit runtime identity assets by default. See [Build from source](docs/BUILD.md) for local packaging details.
