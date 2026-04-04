<div align="center">

# DissPair

### Bluetooth Security Toolkit

**A Python-based educational tool for understanding Bluetooth Classic and BLE 
protocol behavior in controlled, authorized lab environments.**

[![Type](https://img.shields.io/badge/type-Educational%20Research-blueviolet?style=flat-square)](.)
[![Protocol](https://img.shields.io/badge/protocol-Bluetooth%20Classic%20%7C%20BLE-blue?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

> ⚠️ **Authorized Use Only**
> This tool is intended strictly for use on devices you own or have explicit 
> written permission to test. Unauthorized use against third-party devices may 
> violate local laws. The authors assume no liability for misuse.

---

## What is DissPair?

DissPair is a pure-Python Bluetooth learning tool built to help hardware 
security students and protocol researchers understand how Bluetooth Classic 
RFCOMM and BLE GATT stacks behave at a low level.

It is designed for use in **personal lab environments** — testing your own 
devices, understanding protocol fundamentals, and learning how Bluetooth 
service discovery and channel communication works under the hood.

---

## Platform Availability

<div align="center">

| | Platform | Branch | Best For |
|--|----------|--------|----------|
| 📱 | **Android** | [`APK`](../../tree/APK) | Field learning — explore Bluetooth environments from a mobile device. |
| 🐉 | **Kali Linux** | [`CLI`](../../tree/CLI) | Desktop study — terminal-based protocol analysis with minimal dependencies. |

</div>

> Each branch has its own `README.md` with setup instructions and usage guides.

---

## How It Works

Most Bluetooth tools rely on the Service Discovery Protocol (SDP) to list 
advertised services. DissPair supplements this by also attempting direct 
RFCOMM channel connections, helping you understand the difference between 
*advertised* services and *active* ones on your own hardware:
```text
Scan   →  Discover nearby Classic BT devices in your environment.
  ↓
Sweep  →  Probe channels 1–30 on your own device to map active vs. 
          advertised services.
  ↓
Map    →  Visualize which channels are open and what they accept.
  ↓
Analyze → Send structured test payloads to study how your device 
          handles boundary conditions and connection edge cases.
```

---

## Who Is This For?

- Students learning Bluetooth protocol internals
- Hardware hobbyists auditing their own devices
- Security researchers studying RFCOMM/GATT behavior in lab setups
- Developers building Bluetooth-enabled hardware who want to validate 
  their own implementations

---

## Legal & Ethical Use

Only use DissPair against:
- Devices you personally own
- Devices where you have obtained **explicit written authorization** from 
  the owner

Never use this tool in public spaces or against others' devices.
