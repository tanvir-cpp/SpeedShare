<div align="center">

# ⚡ SpeedShare

**Ultra-Fast, Lightweight Peer-to-Peer Local Network File Sharing for Windows & Android**

[![Platform](https://img.shields.io/badge/Platforms-Windows%20%7C%20Android-blue.svg?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/)
[![Version](https://img.shields.io/badge/Version-v1.1.0-blueviolet?style=for-the-badge)](https://github.com/)
[![Speed](https://img.shields.io/badge/Throughput-Direct%20TCP%20Socket-00F0FF?style=for-the-badge&logo=speedtest&logoColor=black)](https://github.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](LICENSE)
[![Size](https://img.shields.io/badge/Windows%20App-~240%20KB-orange?style=for-the-badge)](https://github.com/)
[![Size](https://img.shields.io/badge/Android%20APK-~1.6%20MB-brightgreen?style=for-the-badge)](https://github.com/)

<br/>

<p align="center">
  <b>SpeedShare</b> is an open-source, ultra-lightweight, high-performance file sharing ecosystem designed for instant zero-configuration device discovery and maximum throughput direct socket file transfer across your local Wi-Fi or Ethernet network.
</p>

</div>

---

## ✨ Features

- 🚀 **Ultimate Transfer Speed**: Direct TCP streaming with `TcpNoDelay = true` and 2MB socket buffers delivering line-rate Gigabit & Wi-Fi throughput without cloud relay bottlenecks.
- 📡 **Zero-Configuration Instant Discovery**: Automatic multi-adapter peer discovery using UDP broadcast (`255.255.255.255:53317`) and active subnet beaconing. No Bluetooth pairing or IP typing required.
- 🔒 **Privacy & Approval Gated**: The recipient gets a prominent prompt with sender device name, IP address, file count, and total size before any bytes transfer.
- 📦 **Minimal Binary Footprint**:
  - **Windows App**: **~240 KB** (.NET 10 WPF native glassmorphic UI).
  - **Windows Setup Installer**: **~440 KB** (1-click installer and uninstaller).
  - **Android Release APK**: **~1.6 MB** (Kotlin + Jetpack Compose Material 3).
- ♾️ **Unlimited File Sizes**: 64-bit chunked streaming supports files of any size (50 MB, 10 GB, 100 GB+) with constant $O(1)$ memory usage.
- 🎨 **Modern Dark Aesthetic**: Sleek obsidian & cyan glassmorphism UI, real-time live speedometers in **MB/s** and **Gbps**, animated radar scanner, and dynamic category badges (`VIDEO`, `IMAGE`, `AUDIO`, `ARCHIVE`, `DOC`, `CODE`).
- 📱 **Interactive Android Onboarding**: 3-step modern onboarding experience on mobile.

---

## 🏗️ Architecture & Protocol

```
+-----------------------------------------------------------------------------+
|                               SpeedShare Architecture                        |
+-----------------------------------------------------------------------------+

    [ Step 1: Auto-Discovery (UDP Port 53317) ]
    Windows PC <==== Multi-Adapter Subnet & Global Broadcast ====> Android Device
    {"type":"BEACON", "deviceId":"...", "deviceName":"...", "port":53318}

    [ Step 2: Handshake & Approval (TCP Port 53318) ]
    Sender ------------------ TRANSFER_REQUEST ------------------> Receiver
                                                          (User Prompt: Accept / Decline)
    Sender <----------------- TRANSFER_ACCEPT ------------------- Receiver

    [ Step 3: High-Speed Streaming (TCP Port 53318) ]
    Sender === Raw Binary Payload (1MB Chunks, 2MB Sockets) ====> Receiver
    (Calculates live MB/s, ETA, %)                   (Writes to Downloads/SpeedShare)

    [ Step 4: Completion Confirmation ]
    Sender <---------------- TRANSFER_COMPLETE ------------------ Receiver
```

For full protocol details, see [`protocol/PROTOCOL.md`](protocol/PROTOCOL.md).

---

## ⚡ Performance Benchmark

Direct raw socket protocol verification suite transferring a 25 MB test payload:

```
==================================================
 SPEEDSHARE PROTOCOL VERIFICATION SUITE
==================================================
[Step 1] Broadcasting UDP Discovery Beacon...
   [+] UDP Beacon broadcast successful!

[Step 2] Testing High-Speed TCP Transfer (25 MB payload)...
   [+] Transferred: 25 MB in 0.124 seconds
   [+] Measured Throughput: 201.61 MB/s (1612.90 Mbps)
   [+] Transfer Status: TRANSFER_COMPLETE
==================================================
```

---

## 📥 Installation

### 🪟 Windows (Option 1: 1-Click GUI Installer)
1. Download `SpeedShare-Setup.exe` from the [Latest Release](https://github.com/).
2. Run the installer and click **Install Now**.
3. Launch SpeedShare from your **Desktop** or **Start Menu**.

### 🪟 Windows (Option 2: PowerShell Script)
```powershell
pwsh -ExecutionPolicy Bypass -File .\Install-SpeedShare.ps1
```

### 📱 Android
1. Download `SpeedShare-v1.1.0.apk` from the [Latest Release](https://github.com/).
2. Tap to install, or install via ADB:
   ```bash
   adb install -r SpeedShare-v1.1.0.apk
   ```

---

## 🛠️ Building from Source

### Prerequisites
- [.NET 10 SDK](https://dotnet.microsoft.com/)
- [Java JDK 17+](https://www.oracle.com/java/) & [Android SDK](https://developer.android.com/)

### Build Windows App
```powershell
cd windows
dotnet publish -c Release -r win-x64 --self-contained false -o publish
```

### Build Windows Installer
```powershell
cd windows-installer
dotnet publish -c Release -r win-x64 --self-contained false -o dist-installer
```

### Build Android APK
```bash
cd android
./gradlew assembleRelease
```
The release APK is generated at `android/app/build/outputs/apk/release/app-release.apk`.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
