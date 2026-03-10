<div align="center">

#   DissPair APK

### Bluetooth Security Toolkit

**Bluetooth Security toolkit targeting various Bluetooth Classic and BLE vulnerabilities.**

[![Platform](https://img.shields.io/badge/platform-Android-blue?style=flat-square)](.)
[![Python](https://img.shields.io/badge/python-3.6%2B-blue?style=flat-square)](.)
[![Framework](https://img.shields.io/badge/framework-Kivy-cyan?style=flat-square)](.)
[![CVE](https://img.shields.io/badge/CVE-2025--13834%20%7C%202025--13328-red?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

##   Overview

DissPair APK is a highly specialised, pure-Python Android application built using the **Kivy** framework. It is designed for security researchers and hardware auditors to map, test, and exploit Bluetooth Classic and BLE vulnerabilities.

Unlike standard Bluetooth tools, DissPair directly interfaces with the Android Baseband via **Java Native Interface (JNI) reflection**.

---

##   Repository Structure

```
disspair/
├── main.py               # Primary Kivy application + Bluetooth baseband logic
├── buildozer.spec        # Android NDK/SDK compilation constraints + permissions
├── disspair_logo.png     # App icon, splash screen, and UI branding asset
├── requirements.txt      # Python build dependencies (Buildozer, Cython 0.29.36)
└── README.md
```

> All four files must be present in the same directory before running `buildozer android debug`.

---

##   Installation

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

#### 2. Set Up Python Virtual Environment & Dependencies

> ⚠️ **Critical:** To avoid conflicting with your system's default Python packages, build inside an isolated virtual environment. Buildozer relies on Cython to translate Python code into Android-native C code. You **must** use Cython `0.29.36` — newer versions (3.x) cause `pyjnius` compilation to fail with legacy `long` variable errors.

```bash
# Create a virtual environment
python3 -m venv disspair_env

# Activate it
source disspair_env/bin/activate

# Install all dependencies from requirements.txt
pip install -r requirements.txt
```

>   Ensure your virtual environment is active — you'll see `(disspair_env)` in your terminal prompt — whenever you run `buildozer`.

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

##   Usage Guide

### 1 — Scan for Targets

Tap **SCAN CLASSIC** to actively hunt for BR/EDR devices in range.

> Target devices must be in **discoverable / pairing mode** to appear during an active classic scan.

### 2 — Enumerate Attack Surface

Tap **ENUMERATE** next to your target device.

DissPair ignores SDP broadcasts and physically probes **channels 1 through 30**, mapping which ports require pairing and which are left **dangerously unauthenticated**.

### 3 — Silent Verification

Tap **CONNECT** to establish a silent L2CAP/RFCOMM link. This proves the port is actively listening without disrupting the target.

### 4 — Linux TTY Emulation

Tap **Linux TTY** to connect and blast the target with standard Linux ModemManager AT commands (`ATZ`, `AT+CGMI`). This simulates the crash behaviour seen when vulnerable devices are connected to a Linux host.

### 5 — Resource Exhaustion Flood

- Adjust the **Payload Slider** (64 B → 64 KB)
- Tap **FLOOD** to execute a sustained, maximum-rate data stream into the target's buffer

Tests for watchdog panics and resource starvation limits (CVE-2025-13328 vector).

---

##   How It Works

```
Discovery    →  Android startDiscovery (Classic BR/EDR)
               ↓
Channel Sweep →  createInsecureRfcommSocket(ch) × channels 1–30
               ↓  No SDP. No pairing. Raw connect attempt per channel.
Open Ports   →  Displayed with FLOOD button
               ↓
Flood        →  Continuous write loop until device disconnects
               ↓
Crash Signal →  Disconnection mid-flood = CVE-2025-13328 triggered
```

---

## ⚠️ Disclaimer

DissPair is intended **strictly for authorised security auditing, academic research, and the testing of devices you own.**

Exploiting vulnerabilities on devices, vehicles, or infrastructure **without explicit written consent is illegal** in most jurisdictions. The developers and contributors assume **no liability** for misuse, bricked hardware, or unauthorised access resulting from this tool.

---

<div align="center">
<sub>Built for security research · Requires Android BLUETOOTH_SCAN permission</sub>
</div>
