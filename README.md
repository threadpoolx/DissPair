<div align="center">

#    DissPair CLI — Kali Edition

### Advanced Bluetooth Classic RFCOMM Auditor & Fuzzer

[![Platform](https://img.shields.io/badge/platform-Kali%20Linux%20%7C%20Debian%20%7C%20Ubuntu-blueviolet?style=flat-square)](.)
[![Python](https://img.shields.io/badge/python-3.6%2B-blue?style=flat-square)](.)
[![CVE](https://img.shields.io/badge/CVE-2025--13834%20%7C%202025--13328-red?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

##   Overview

DissPair CLI is a highly specialised Python command-line tool designed for security researchers to map, test, and exploit Bluetooth Classic RFCOMM layer vulnerabilities — specifically **Resource Exhaustion (DoS)** conditions (CVE-2025-13328) and **unauthenticated port exposures** (CVE-2025-13834).

While the Android APK relies on Java Reflection, this Linux CLI directly manipulates native Linux kernel sockets (`AF_BLUETOOTH`) and C-level memory structures (`setsockopt`) to strip away BlueZ's default pairing enforcements. This allows it to blindly brute-force RFCOMM channels and map cloaked attack surfaces.

---

##   Supported Platforms

| OS | Status | Notes |
|----|--------|-------|
| 🐧 **Kali Linux** | ✅ Recommended | Full kernel socket access, `BT_SECURITY_LOW` downgrade, active BR/EDR scanning, automated audio-sink severing |
| 🐧 **Ubuntu / Debian** | ✅ Supported | Same capabilities as Kali — ensure BlueZ is installed |

> **Virtual Machines:** Built-in laptop Bluetooth cannot be passed to a VM. You **must** use a **USB Bluetooth adapter** and pass it through (`VM → Removable Devices → Connect`).

---

##   Installation & Setup

### 1. Install System Dependencies

The tool uses native Linux Bluetooth utilities (bluetoothctl and hcitool) to interact with the host radio. Ensure your system's BlueZ stack is installed and up to date

```bash
sudo apt update
sudo apt install -y bluez rfkill
```

### 2. Set Up Python Virtual Environment

Recommended to avoid `PEP 668` system-package conflicts.

```bash
python3 -m venv disspair_env
source disspair_env/bin/activate
```

### 3. Install Python Dependencies

```bash
pip install bleak
```

---

##  Usage

> ⚠️ Raw RFCOMM socket manipulation requires elevated kernel privileges. You must run as `root`. Ensure your virtual environment is active before invoking `sudo`.

```bash
# Activate your venv
source disspair_env/bin/activate

# Run the tool
sudo $(which python3) disspair_kali.py
```

---

##   Attack Workflow

### Step 1 — Target Acquisition

- **Option 1** — Actively scan for discoverable BR/EDR targets in range
- **Option 3** — Pull targets directly from your local BlueZ pairing cache
- **Option 4** — Enter a target MAC address manually

### Step 2 — Channel Enumeration (Option 6)

DissPair bypasses SDP and brute-forces channels 1–30. If the target is paired, it automatically severs active audio streams (PulseAudio / PipeWire) to free up the target's L2CAP multiplexer before probing.

### Step 3 — Exploitation (Option 7)

| Action | Description |
|--------|-------------|
| **[1] CONNECT** | Establishes a silent L2CAP/RFCOMM link — proves the port is listening without crashing the target |
| **[2] Linux TTY** | Simulates the Kali `ModemManager` daemon by blasting a burst of Hayes AT commands (`ATZ`, `AT+CGMI`) — triggers parsers expecting audio control frames to panic |
| **[3] FLOOD** | Executes a paced, maximum-rate data stream into the target's buffer to test for watchdog panics and resource starvation (CVE-2025-13328 vector) |

---

##   Troubleshooting

If the script fails to scan or reports `No default Bluetooth adapter found`, your host radio is likely soft-blocked or asleep. Run the following to wake it up:

```bash
# 1. Unblock the radio from power-saving mode
sudo rfkill unblock bluetooth

# 2. Restart the BlueZ daemon
sudo systemctl restart bluetooth

# 3. Bring the Host Controller Interface up
sudo hciconfig hci0 up

# 4. Verify it is running (look for "UP RUNNING")
hciconfig -a
```

---

## ⚠️ Disclaimer

DissPair is intended **strictly for authorised security auditing, academic research, and the testing of devices you own.**

Exploiting RFCOMM vulnerabilities on devices, vehicles, or infrastructure **without explicit written consent is illegal** in most jurisdictions. The developers and contributors assume **no liability** for misuse, bricked hardware, or unauthorised access resulting from this tool.

---

<div align="center">
<sub>Bluetooth Classic only &nbsp;·&nbsp; Requires root &nbsp;·&nbsp; CVE-2025-13834 / CVE-2025-13328</sub>
</div>
