<div align="center">

<img src="disspair_logo.png" alt="DissPair Logo" width="160"/>

# DissPair

### Bluetooth Security Toolkit

**A specialised security research tool for mapping and testing Bluetooth Classic and BLE-related vulnerabilities.**

[![CVE](https://img.shields.io/badge/CVE-2025--13834%20%7C%202025--13328-red?style=flat-square)](.)
[![Type](https://img.shields.io/badge/type-Security%20Research-blueviolet?style=flat-square)](.)
[![Protocol](https://img.shields.io/badge/protocol-Bluetooth%20Classic%20RFCOMM-blue?style=flat-square)](.)
[![License](https://img.shields.io/badge/license-Research%20Use%20Only-orange?style=flat-square)](.)

</div>

---

## What is DissPair?

DissPair is a pure-Python Bluetooth security auditing tool that targets the **RFCOMM layer** of Bluetooth Classic. It bypasses the standard Service Discovery Protocol (SDP) entirely, instead directly probing channels 1–30 via raw socket connections to identify unauthenticated, hidden, and vulnerable RFCOMM ports.

It was built to research and reproduce two vulnerability classes found in Bluetooth Classic firmware:

| CVE | Type | Description |
|-----|------|-------------|
| **CVE-2025-13834** | Unauthenticated Exposure | RFCOMM channels accepting connections without pairing |
| **CVE-2025-13328** | Resource Exhaustion (DoS) | Buffer overflow via oversized RFCOMM payload flood |

---

## Choose Your Platform

DissPair is available in two independent flavours. Pick the branch for your environment:

<br>

<div align="center">

| | Platform | Branch | Best For |
|--|----------|--------|----------|
| 📱 | **Android** | [`APK`](../../tree/APK) | Field auditing — scan and attack from your phone |
| 🐉 | **Kali Linux** | [`CLI`](../../tree/CLI) | Desktop research — terminal-based, zero dependencies |

</div>

<br>

> Each branch contains its own full `README.md` with setup instructions, build steps, and usage guide.

---

## How It Works

Standard Bluetooth tools rely on SDP to discover services. DissPair ignores SDP completely and physically attempts an RFCOMM connection on every channel number:

```
Scan  →  Discover nearby Classic BT devices
  ↓
Sweep →  Connect to channels 1–30 directly (no SDP, no pairing required)
  ↓
Map   →  Identify which channels are open and accepting connections
  ↓
Flood →  Send oversized payloads to test for CVE-2025-13328 buffer overflow
```

This approach finds **cloaked ports** that SDP would never reveal, and confirms unauthenticated access at the socket level rather than relying on service advertisements.

---

## ⚠️ Disclaimer

DissPair is intended **strictly for authorised security auditing, academic research, and the testing of devices you own.**

Exploiting Bluetooth vulnerabilities on devices, vehicles, or infrastructure **without explicit written consent is illegal** in most jurisdictions. The developers and contributors assume **no liability** for misuse, bricked hardware, or unauthorised access resulting from this tool.

---

<div align="center">
<sub>Bluetooth Classic only &nbsp;·&nbsp; RFCOMM channels 1–30 &nbsp;·&nbsp; CVE-2025-13834 / CVE-2025-13328</sub>
</div>
