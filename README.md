<div align="center">

# DissPair CLI — Kali Edition

### Bluetooth Protocol Analysis & Learning Toolkit

[![Platform](https://img.shields.io/badge/platform-Kali%20Linux%20%7C%20Debian%20%7C%20Ubuntu-blueviolet?style=flat-square)](.)
[![Python](https://img.shields.io/badge/python-3.6%2B-blue?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

> ⚠️ **Authorized Use Only**
> This tool is intended strictly for use on devices you personally own or 
> have explicit written permission to test. Unauthorized use against 
> third-party devices may violate local and international law. The authors 
> assume no liability for misuse.

---

## Overview

DissPair CLI is a Python command-line tool for students and hardware 
researchers who want to understand how Bluetooth Classic RFCOMM and BLE 
GATT protocols behave at a low level on their own hardware.

It uses native Linux kernel Bluetooth sockets (`AF_BLUETOOTH`) to interact 
directly with the BlueZ stack, allowing you to observe real protocol 
behavior without relying solely on higher-level abstractions.

---

## Supported Platforms

| OS | Status | Notes |
|----|--------|-------|
| 🐧 **Kali Linux** | ✅ Recommended | Full kernel socket access and native BR/EDR scanning |
| 🐧 **Ubuntu / Debian** | ✅ Supported | Same capabilities — ensure BlueZ is installed |

> **Virtual Machines:** Built-in laptop Bluetooth cannot be passed to a VM.
> You **must** use a **USB Bluetooth adapter** and pass it through via your 
> VM's removable devices menu.

---

## Installation & Setup

### 1. Install System Dependencies
```bash
sudo apt update
sudo apt install -y bluez rfkill
```

### 2. Set Up Python Virtual Environment
```bash
python3 -m venv disspair_env
source disspair_env/bin/activate
```

### 3. Install Python Dependencies
```bash
pip install bleak
```

---

## Usage

> Raw RFCOMM socket operations require elevated privileges on Linux.
```bash
# Activate your venv
source disspair_env/bin/activate

# Run the tool
sudo $(which python3) disspair_kali.py
```

---

## Features & Workflow

### Step 1 — Device Discovery

| Option | Description |
|--------|-------------|
| **[1] Scan Classic** | Actively discover BR/EDR devices in range via hcitool or bluetoothctl |
| **[2] Scan BLE** | Passive BLE advertisement radar using Bleak |
| **[3] Load Paired** | Pull devices from your local BlueZ pairing cache |
| **[4] Manual Entry** | Enter a target MAC address directly |

### Step 2 — Enumeration

| Option | Description |
|--------|-------------|
| **Classic RFCOMM** | Probes channels 1–30 on your device to identify which are actively accepting connections, supplementing what SDP advertises |
| **BLE GATT** | Connects and reads the full GATT service/characteristic table from your BLE device |

### Step 3 — Protocol Interaction

| Action | Description |
|--------|-------------|
| **[1] Connect** | Opens and holds a silent RFCOMM connection to verify a channel is live |
| **[2] AT Commands** | Sends a sequence of standard Hayes AT modem commands — useful for studying how your device's serial profile responds |
| **[3] Stress Test** | Transmits a sustained data stream to observe how your device handles connection load and buffer behavior |
| **BLE Read** | Reads the current value of a GATT characteristic on your BLE device |
| **BLE Write** | Writes a value (string or hex) to a writable GATT characteristic |

---

## Troubleshooting

If the tool reports no Bluetooth adapter found:
```bash
# Unblock the radio
sudo rfkill unblock bluetooth

# Restart BlueZ
sudo systemctl restart bluetooth

# Bring the interface up
sudo hciconfig hci0 up

# Verify it is running (look for "UP RUNNING")
hciconfig -a
```

---

## Who Is This For?

- Students learning Bluetooth protocol internals
- Hardware developers validating their own Bluetooth implementations
- Security researchers studying RFCOMM and GATT behavior in lab environments
- Hobbyists exploring the Bluetooth stack on their own devices

---

## Legal & Ethical Use

Only use DissPair against:
- Devices you personally own
- Devices where you have **explicit written authorization** from the owner

Never use this tool in public spaces or against devices belonging to others.
The authors assume no liability for misuse or any resulting damage.

---

<div align="center">
<sub>Bluetooth Learning Toolkit — CLI Edition</sub>
</div>
