<div align="center">

# 📡 DissPair

### Bluetooth RFCOMM Auditor

**Bluetooth Classic security research tool targeting unauthenticated RFCOMM port exposure and resource exhaustion vulnerabilities.**

[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Kali%20Linux-blue?style=flat-square)](.)
[![Python](https://img.shields.io/badge/python-3.6%2B-blue?style=flat-square)](.)
[![Framework](https://img.shields.io/badge/framework-Kivy-cyan?style=flat-square)](.)
[![CVE](https://img.shields.io/badge/CVE-2025--13834%20%7C%202025--13328-red?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

## 📖 Overview

DissPair is a highly specialised, pure-Python Android application built using the **Kivy** framework. It is designed for security researchers and hardware auditors to map, test, and exploit Bluetooth Classic RFCOMM layer vulnerabilities — specifically **Resource Exhaustion (DoS)** conditions (CVE-2025-13328) and **unauthenticated port exposures** (CVE-2025-13834).

Unlike standard Bluetooth tools, DissPair directly interfaces with the Android Baseband via **Java Native Interface (JNI) reflection**. It bypasses SDP (Service Discovery Protocol) to brutally sweep channels 1–30, identifying cloaked, unauthenticated, and vulnerable RFCOMM ports.

A companion **Kali Linux CLI tool** (`disspair_kali.py`) is also included for desktop-based auditing using native Python Bluetooth sockets — no third-party libraries required.

---

## 🗂️ Repository Structure

```
disspair/
├── main.py               # Primary Kivy application + Bluetooth baseband logic
├── disspair_kali.py      # Kali Linux CLI companion tool
├── buildozer.spec        # Android NDK/SDK compilation constraints + permissions
├── disspair_logo.png     # App icon, splash screen, and UI branding asset
└── README.md
```

> All four files must be present in the same directory before running `buildozer android debug`.

---

## 📱 Android App

### Direct Installation (Pre-Built)

1. Download `disspair.apk` from the [Releases](../../releases) tab
2. On your Android device, allow your browser or file manager to **Install unknown apps**
3. Open the app and grant the requested **Location** and **Nearby Devices** permissions
   > These permissions are mandatory for Bluetooth scanning on Android 12+

### Build from Source

#### 1. Install System Dependencies

> Ubuntu, Debian, or Kali Linux are strongly recommended.

```bash
sudo apt update
sudo apt install -y git zip unzip openjdk-17-jdk python3-pip autoconf libtool \
  pkg-config zlib1g-dev libncurses5-dev libncursesw5-dev libtinfo5 cmake \
  libffi-dev libssl-dev
```

#### 2. Install Python Dependencies

> ⚠️ **Critical:** You **must** use Cython `0.29.36`. Newer versions (3.x) cause `pyjnius` compilation to fail with legacy `long` variable errors.

```bash
pip install buildozer
pip install "Cython==0.29.36" wheel setuptools --break-system-packages
```

#### 3. Export Java Path

```bash
export JAVA_HOME=$(ls -d /usr/lib/jvm/java-17-openjdk* | head -n 1)
export PATH=$JAVA_HOME/bin:$PATH
```

#### 4. Compile

```bash
buildozer android debug
```

> First run will download the Android SDK and NDK (several GB). Allow **15–30 minutes** depending on your connection and CPU.

The compiled APK will be output to `bin/`.

---

## ⚔️ Android Usage Guide

### 1 — Scan for Targets

Tap **SCAN CLASSIC** to actively hunt for BR/EDR devices in range.

> Target devices must be in **discoverable / pairing mode** to appear during an active classic scan.

### 2 — Enumerate Attack Surface

Tap **ENUMERATE** next to your target device.

DissPair ignores SDP broadcasts and physically probes **channels 1 through 30**, mapping which ports require pairing and which are left **dangerously unauthenticated**.

### 3 — Silent Verification

Tap **CONNECT** to establish a silent L2CAP/RFCOMM link. This proves the port is actively listening without disrupting the target.

### 4 — Linux TTY Emulation

Tap **Linux TTY** to connect and blast the target with standard Linux ModemManager AT commands (`ATZ`, `AT+CGMI`). This simulates the crash behaviour seen when vulnerable devices are connected to Kali Linux.

### 5 — Resource Exhaustion Flood

- Adjust the **Payload Slider** (64 B → 64 KB)
- Tap **FLOOD** to execute a sustained, maximum-rate data stream into the target's buffer

Tests for watchdog panics and resource starvation limits (CVE-2025-13328 vector).

---

## 🐉 Kali Linux CLI Tool

The companion `disspair_kali.py` runs directly on any Kali/Debian system with BlueZ installed. It uses Python's native `AF_BLUETOOTH` / `BTPROTO_RFCOMM` socket — **no pip installs required**.

### Prerequisites

```bash
sudo apt install bluez          # hcitool, hciconfig, l2ping
sudo systemctl start bluetooth
sudo hciconfig hci0 up
```

### Commands

```bash
# Discover nearby Classic Bluetooth devices
sudo python3 disspair_kali.py scan

# Probe RFCOMM channels 1-30 on a target
sudo python3 disspair_kali.py sweep <MAC>

# Flood a specific open channel
sudo python3 disspair_kali.py flood <MAC> <channel>

# Sweep then interactively flood all open channels
sudo python3 disspair_kali.py auto  <MAC>
```

### Examples

```bash
sudo python3 disspair_kali.py scan
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF
sudo python3 disspair_kali.py flood AA:BB:CC:DD:EE:FF 3
sudo python3 disspair_kali.py flood AA:BB:CC:DD:EE:FF 3 --size 65536 --time 30
sudo python3 disspair_kali.py auto  AA:BB:CC:DD:EE:FF
```

### Flood Options

| Flag | Default | Description |
|------|---------|-------------|
| `--size` | `4096` | Payload size in bytes (max: 65536) |
| `--time` | unlimited | Stop flood after N seconds |
| `--verbose` | off | Show closed/timeout channels during sweep |
| `--timeout` | `2.0` | Per-channel connect timeout (seconds) |

---

## 🔬 How It Works

```
Discovery    →  hcitool scan / Android startDiscovery
               ↓
Channel Sweep →  createInsecureRfcommSocket(ch) × channels 1–30
               ↓  No SDP. No pairing. Raw connect attempt per channel.
Open Ports   →  Displayed with FLOOD button
               ↓
Flood        →  socket.send(payload) loop until device disconnects
               ↓
Crash Signal →  BrokenPipeError / ConnectionReset = CVE-2025-13328 triggered
```

**Key design decisions:**

- **No SDP** — SDP is bypassed entirely. Channels are probed by raw connect, not by querying the service registry. This finds hidden/unclaimed ports that SDP would never reveal.
- **Insecure sockets first** — `createInsecureRfcommSocket` is tried before `createRfcommSocket`, ensuring unauthenticated access is tested without triggering pairing dialogs.
- **ACL cache flush** (Android) — After a target crash/reboot, a 4-attempt retry ladder with JNI reflection clears Android's stale ACL cache so re-enumeration works without toggling Bluetooth.
- **Sequential sweep** (Kali CLI) — Parallel BT sockets over a single HCI adapter cause HCI congestion. Channels are probed sequentially (~2s per channel) for reliable results.

---

## ⚠️ Disclaimer

DissPair is intended **strictly for authorised security auditing, academic research, and the testing of devices you own.**

Exploiting RFCOMM vulnerabilities on devices, vehicles, or infrastructure **without explicit written consent is illegal** in most jurisdictions. The developers and contributors assume **no liability** for misuse, bricked hardware, or unauthorised access resulting from this tool.

---

<div align="center">
<sub>Built for security research · Classic BT only · Requires root / Android BLUETOOTH_SCAN permission</sub>
</div>
