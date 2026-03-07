<div align="center">
<img src="disspair_logo.png" alt="DissPair Logo" width="150" />

<h1>⚡ DissPair (Android APK)</h1>
<p><b>Advanced Bluetooth Classic RFCOMM Auditor & Fuzzer</b></p>
</div>

📖 Overview

DissPair is a highly specialized, pure-Python Android application built using the Kivy framework. It is designed for security researchers and hardware auditors to map, test, and exploit Bluetooth Classic RFCOMM layer vulnerabilities—specifically Resource Exhaustion (DoS) conditions (like CVE-2025-13328) and unauthenticated port exposures.

Unlike standard Bluetooth tools, DissPair directly interfaces with the Android Baseband via Java Native Interface (JNI) reflection. It bypasses SDP (Service Discovery Protocol) to brutally sweep channels 1-30, identifying cloaked, unauthenticated, and vulnerable RFCOMM ports.

🗂️ Repository Structure

To successfully build this APK, ensure your directory contains the following files:

main.py - The primary Kivy application and Bluetooth Baseband logic.

buildozer.spec - The Android NDK/SDK compilation constraints and permission arrays.

disspair_logo.png - The UI branding, app icon, and splash screen asset.

🛠️ Build Environment Setup (Linux Recommended)

If you want to compile the APK from the source code, you must set up a Buildozer environment. Ubuntu, Debian, or Kali Linux are strongly recommended.

1. Install System Dependencies

The Android NDK requires specific C-compilers and Java Development Kits. Open your terminal and run:

sudo apt update
sudo apt install -y git zip unzip openjdk-17-jdk python3-pip autoconf libtool pkg-config zlib1g-dev libncurses5-dev libncursesw5-dev libtinfo5 cmake libffi-dev libssl-dev


2. Install Python Dependencies (CRITICAL STEP)

Buildozer relies on Cython to translate Python code into Android-native C code. You MUST use Cython version 0.29.36. Newer versions (3.x) will cause pyjnius compilation to fail with legacy long variable errors.

pip install buildozer
pip install "Cython==0.29.36" wheel setuptools --break-system-packages


3. Export Java Path

Ensure Buildozer uses JDK 17:

export JAVA_HOME=$(ls -d /usr/lib/jvm/java-17-openjdk* | head -n 1)
export PATH=$JAVA_HOME/bin:$PATH


🏗️ Compiling the APK

Once your environment is set up, navigate to the repository directory containing main.py and buildozer.spec and run:

buildozer android debug


Note: The first time you run this command, it will download the Android SDK and NDK (several gigabytes). This process can take 15 to 30 minutes depending on your internet connection and CPU.

Once compilation is complete, the ready-to-install APK will be located in the newly generated bin/ directory.

📱 Direct Installation (Pre-Built)

If you prefer not to compile the app yourself, you can download the pre-compiled .apk file from the Releases tab of this repository.

Download the disspair.apk file to your Android device.

When prompted, allow your browser or file manager to "Install unknown apps".

Open the app and grant the requested Location and Nearby Devices permissions (Mandatory for Android 12+ Bluetooth scanning).

⚔️ Usage Guide

Scan for Targets: * Tap SCAN CLASSIC to actively hunt for BR/EDR devices.

Note: Target devices must be discoverable (pairing mode) to be seen during an active classic scan.

Enumerate Attack Surface: * Tap ENUMERATE next to your target.

DissPair will ignore SDP broadcasts and physically probe Channels 1 through 30, mapping out which ports require pairing and which are left dangerously unauthenticated.

Silent Verification: * Tap CONNECT to establish a silent L2CAP/RFCOMM link. This proves the port is actively listening without crashing the target.

Linux TTY Emulation: * Tap Linux TTY to immediately connect and blast the target with standard Linux ModemManager AT Commands (ATZ, AT+CGMI). This perfectly simulates the crash behavior often seen when connecting vulnerable devices to Kali Linux.

Resource Exhaustion (Flood): * Adjust the Payload Slider (64B to 64KB).

Tap FLOOD to execute a paced, 100% CPU data stream into the target's buffer to test for watchdog panics and resource starvation limits.

⚠️ Disclaimer

DissPair is intended strictly for authorized security auditing, academic research, and the testing of devices you own. Exploiting RFCOMM vulnerabilities on devices, vehicles, or infrastructure without explicit consent is illegal. The developers and contributors assume no liability for misuse, bricked hardware, or unauthorized access resulting from this tool.
