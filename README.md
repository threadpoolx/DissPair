<div align="center">

# DissPair

### Bluetooth Security Toolkit

**A Python-based educational tool for understanding Bluetooth Classic and BLE 
protocol behavior in controlled, authorized lab environments.**

Note: In case this repository is not operational, you can find a backup repository at [Codeberg](https://codeberg.org/threadpoolx/DissPair)

[![Type](https://img.shields.io/badge/Type-Research-blue?style=flat-square)](https://amoebatech.gitbook.io/amoebatech-docs/technical-blogs/rfcomm-security-from-virtual-serial-to-attack-surface-with-disspair)
[![CVE](https://img.shields.io/badge/CVE-2026–31280-red?style=flat-square)](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2025-13834)
[![Protocol](https://img.shields.io/badge/Protocol-Bluetooth%20Classic%20%7C%20BLE-blue?style=flat-square)](https://www.bluetooth.com/)
[![License](https://img.shields.io/badge/License-Research%20Use%20Only-orange?style=flat-square)](https://opensource.org/licenses/)

</div>

---

> ⚠️ **Authorized Use Only**
> This tool is intended strictly for use on devices you own or have explicit 
> written permission to test. Unauthorized use against third-party devices may 
> violate local laws.
>
> ⚠️**Potentially Harmful Capabilities & Risk Disclosure**
>
> This tool is strictly intended for educational purposes, security  research, and authorized security auditing. It contains features that can cause operational disruption to target hardware if used improperly:
>
> 1. RFCOMM Hardware Flooding: The "Flood" module intentionally injects dense byte streams into targeted Bluetooth channels. On vulnerable, legacy, or unpatched Bluetooth stacks, this can cause buffer overflows, resulting in the target device freezing, kernel panics, or complete Denial of Service (DoS).
>
> 2. Payload Injection & State Manipulation: The ability to inject raw AT commands (e.g., HFP manipulation) and OBEX payloads can alter the operational state of target devices, potentially causing unauthorized call manipulation or disrupting audio gateways.
>
> 3. GATT Interaction: Unauthenticated reading/writing of BLE characteristics may expose sensitive plaintext data or alter IoT device configurations.
>
> The developer assumes no liability for misuse or damage caused by this software. Use exclusively on hardware you own or have explicit consent to audit.

---

## What is DissPair?

DissPair is a pure-Python Bluetooth learning tool built to help hardware 
Security students and protocol researchers understand how Bluetooth Classic 
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

## Who Is This For?

- Students learning Bluetooth protocol internals
- Hardware hobbyists auditing their own devices
- Security researchers studying RFCOMM/GATT behavior in lab setups
- Developers building Bluetooth-enabled hardware who want to validate 
  their own implementations

---
## Security and Abuse Reporting

If you need to report a security vulnerability, malicious activity, or third-party abuse related to this tool, please see our [Security Policy](https://github.com/threadpoolx/DissPair/blob/main/Security.md) for contact details and responsible disclosure guidelines.

---
## Legal & Ethical Use

Only use DissPair against:
- Devices you personally own
- Devices where you have obtained **explicit written authorization** from 
  the owner

Never use this tool in public spaces or against others' devices without consent.
