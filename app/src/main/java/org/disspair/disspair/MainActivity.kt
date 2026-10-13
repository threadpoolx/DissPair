package org.disspair.disspair

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import org.disspair.disspair.ble.AuditOptions
import org.disspair.disspair.ble.AuditReport
import org.disspair.disspair.ble.CharAudit
import org.disspair.disspair.ble.GattAudit
import org.disspair.disspair.ble.GattOps
import org.disspair.disspair.ble.GattProps
import org.disspair.disspair.ble.GattResult
import org.disspair.disspair.ble.GattStatus
import org.disspair.disspair.ble.toHex
import org.disspair.disspair.ble.BleUuids
import org.disspair.disspair.gap.AdvInspectorOverlay
import org.disspair.disspair.gap.AdvStore
import org.disspair.disspair.ui.DarkColors
import org.disspair.disspair.ui.LightColors
import org.disspair.disspair.ui.Pal
import org.disspair.disspair.ui.ThemeMode
import org.disspair.disspair.ui.ThemePrefs
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// --- Data Models ---
data class DeviceItem(val name: String, val mac: String, val type: String, val rssi: Int, val deviceClass: Int? = null, val hasSpp: Boolean = false)
data class ChannelUI(val channel: Int, val authType: String, val isSecure: Boolean)

data class BleServiceUI(
    val uuid: String,
    val name: String,
    val characteristics: MutableList<GattCharUI>
)

data class GattCharUI(
    val uuid: String, val name: String, val properties: String,
    val canRead: Boolean,
    /** WRITE with response (0x08) */
    val canWrite: Boolean,
    /** NOTIFY (0x10) or INDICATE (0x20) */
    val canNotify: Boolean,
    val charObj: BluetoothGattCharacteristic, var lastValue: MutableState<String> = mutableStateOf(""),
    /** WRITE_NO_RESPONSE (0x04) */
    val canWriteNoResp: Boolean = false,
    val notifying: MutableState<Boolean> = mutableStateOf(false),
    val audit: MutableState<CharAudit?> = mutableStateOf(null),
)

enum class ScanMode { IDLE, CLASSIC, BLE }

// --- Helpers ---
fun parseColorSafe(hexColor: String): Color {
    val hexStr = if (hexColor.startsWith("#")) hexColor else "#$hexColor"
    return try {
        Pal.adapt(Color(android.graphics.Color.parseColor(hexStr)))
    } catch (e: Exception) {
        e.printStackTrace()
        Pal.c.textSecondary
    }
}

fun decodeHfpFeatures(maskStr: String): List<String> {
    val features = mutableListOf<String>()
    try {
        val mask = maskStr.toInt()
        if ((mask and 1) != 0) features.add("Three-way Calling")
        if ((mask and 2) != 0) features.add("Echo Cancel / Noise Reduction")
        if ((mask and 4) != 0) features.add("Voice Recognition")
        if ((mask and 8) != 0) features.add("In-band Ring Tone")
        if ((mask and 16) != 0) features.add("Voice Tag Attach")
        if ((mask and 32) != 0) features.add("Call Reject")
        if ((mask and 64) != 0) features.add("Enhanced Call Status")
        if ((mask and 128) != 0) features.add("Enhanced Call Control")
        if ((mask and 256) != 0) features.add("Extended Error Codes")
        if ((mask and 512) != 0) features.add("Codec Negotiation")
    } catch(e: Exception) {}
    return features
}

fun getDeviceClassTag(deviceClass: Int?): String {
    if (deviceClass == null) return "❓ UNKNOWN"
    return when (deviceClass) {
        BluetoothClass.Device.PHONE_SMART, BluetoothClass.Device.PHONE_CELLULAR, BluetoothClass.Device.PHONE_CORDLESS -> "📱 PHONE"
        BluetoothClass.Device.COMPUTER_LAPTOP, BluetoothClass.Device.COMPUTER_DESKTOP, BluetoothClass.Device.COMPUTER_SERVER -> "💻 PC"
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET, BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE, BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "🎧 AUDIO"
        BluetoothClass.Device.WEARABLE_WRIST_WATCH, BluetoothClass.Device.WEARABLE_JACKET, BluetoothClass.Device.WEARABLE_GLASSES -> "⌚ WEARABLE"
        BluetoothClass.Device.TOY_VEHICLE, BluetoothClass.Device.TOY_CONTROLLER -> "🎮 TOY"
        BluetoothClass.Device.HEALTH_BLOOD_PRESSURE, BluetoothClass.Device.HEALTH_THERMOMETER -> "⚕️ HEALTH"
        else -> {
            when (deviceClass and 0x1F00) {
                BluetoothClass.Device.Major.PHONE -> "📱 PHONE"
                BluetoothClass.Device.Major.COMPUTER -> "💻 PC"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "🎧 AUDIO"
                BluetoothClass.Device.Major.WEARABLE -> "⌚ WEARABLE"
                BluetoothClass.Device.Major.HEALTH -> "⚕️ HEALTH"
                BluetoothClass.Device.Major.TOY -> "🎮 TOY"
                BluetoothClass.Device.Major.PERIPHERAL -> "⌨️ PERIPHERAL"
                else -> "🛠️ OTHER"
            }
        }
    }
}

object BleNames {
    fun resolve(uuid: String) = BleUuids.name(uuid) ?: "Characteristic"
}

fun hexToAsciiSafe(hexStr: String): String {
    if (hexStr == "Empty" || hexStr.startsWith("ERR")) return hexStr
    return try {
        hexStr.chunked(2).map { it.toInt(16).toChar() }.joinToString("") { if (it in ' '..'~') it.toString() else "." }
    } catch (e: Exception) {
        e.printStackTrace()
        hexStr
    }
}

private fun Modifier.customScalePadding(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.padding(((1 - scale) * 10).dp)
)

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var currentScanMode by mutableStateOf(ScanMode.IDLE)
    private var systemLogs = mutableStateListOf<Pair<String, String>>()
    private var foundDevices = mutableStateListOf<DeviceItem>()
    private var showAuthModal by mutableStateOf(true)
    private var showPairedDevices by mutableStateOf(false)
    private var showUnknownDevices by mutableStateOf(true)
    private var activeAnalysisDevice by mutableStateOf<DeviceItem?>(null)
    private var showRenameDialog by mutableStateOf(false)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var activeAdvDevice by mutableStateOf<DeviceItem?>(null)
    /** Every BLE advertisement seen while scanning, per device (GAP inspector). */
    private val advStore = AdvStore()

    private var activeRfcommSocket: BluetoothSocket? = null
    private var activeFloodJob: Job? = null

    private var isReceiverRegistered = false
    private var classicScanCount = 0

    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.entries.all { it.value }) initBluetooth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestPermissions()
        themeMode = ThemePrefs.load(this)
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        Pal.c = if (resolveDark(themeMode, systemDark)) DarkColors else LightColors
        setContent {
            val dark = resolveDark(themeMode, isSystemInDarkTheme())
            SideEffect {
                if (Pal.c.isDark != dark) Pal.c = if (dark) DarkColors else LightColors
                // Dark status/nav bar icons on light backgrounds, light icons on dark ones
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val scheme = if (dark) {
                darkColorScheme(primary = Color(0xFF0F61B7), onPrimary = Color.White)
            } else {
                lightColorScheme(primary = Color(0xFF0F61B7), onPrimary = Color.White)
            }
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = Pal.c.background, contentColor = Pal.c.text) {
                    if (showAuthModal) {
                        AuthModal(onConfirm = { showAuthModal = false; runStartupDiagnostics() }, onExit = { finish() })
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            MainScreen()
                            activeAdvDevice?.let { device ->
                                AdvInspectorOverlay(
                                    mac = device.mac,
                                    fallbackName = device.name,
                                    store = advStore,
                                    scanning = currentScanMode == ScanMode.BLE,
                                    onClose = { activeAdvDevice = null },
                                    onCopy = { copyText("DissPair advertisement report", it) },
                                    onShare = { shareText("DissPair advertisement report - ${device.mac}", it) },
                                    onAnalyseGatt = {
                                        if (currentScanMode == ScanMode.BLE) toggleBleScan()
                                        activeAdvDevice = null
                                        activeAnalysisDevice = device
                                    },
                                )
                            }
                            activeAnalysisDevice?.let { device ->
                                if (device.type == "BLE") {
                                    BleAnalysisOverlay(device, onClose = { activeAnalysisDevice = null })
                                } else {
                                    ClassicAnalysisOverlay(device, onClose = {
                                        activeFloodJob?.cancel();
                                        try { activeRfcommSocket?.close() } catch(e: Exception) { e.printStackTrace() }
                                        activeAnalysisDevice = null
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        logMsg("[+] Copied to clipboard", "39FF14")
    }

    private fun shareText(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try { startActivity(Intent.createChooser(send, "Share report")) } catch (e: Exception) { logMsg("[-] Share failed", "FF2D55") }
    }

    private fun cycleTheme() {
        themeMode = themeMode.next()
        ThemePrefs.save(this, themeMode)
        logMsg("[*] Theme: ${themeMode.label}", "00E5FF")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        if (isReceiverRegistered) {
            try { unregisterReceiver(receiver) } catch(e: Exception) { e.printStackTrace() }
            isReceiverRegistered = false
        }
        classicScanCount = 0
        activeFloodJob?.cancel()
        try { activeRfcommSocket?.close() } catch(e: Exception) { e.printStackTrace() }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionLauncher.launch(perms)
    }

    private fun initBluetooth() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter
        loadPairedDevices()
    }

    private fun runStartupDiagnostics() {
        try {
            val btEnabled = bluetoothAdapter?.isEnabled ?: false
            val locManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locEnabled = try { locManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
            logMsg("--- System Diagnostics ---", "00E5FF")
            logMsg(if (btEnabled) "[OK] Bluetooth: ON" else "[!!] Bluetooth: OFF", if (btEnabled) "39FF14" else "FF2D55")
            logMsg(if (locEnabled) "[OK] Location: ON" else "[!!] Location: OFF", if (locEnabled) "39FF14" else "FF2D55")
        } catch (e: Exception) {
            logMsg("[!] Diagnostics error: ${e.message}", "FF2D55")
        }
    }

    private fun logMsg(msg: String, hexColor: String = "7a7a9a") {
        mainScope.launch {
            systemLogs.add(msg to hexColor)
            if (systemLogs.size > 500) systemLogs.removeAt(0)
        }
    }

    private fun checkHardwareStatus(): Boolean {
        val btEnabled = bluetoothAdapter?.isEnabled ?: false
        val locManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!btEnabled) logMsg("[!!] Bluetooth Radio is OFF", "FF2D55")
        if (!locEnabled) logMsg("[!!] Location Services are OFF (Scan likely to fail)", "FF2D55")

        return btEnabled
    }

    private fun loadPairedDevices() {
        mainScope.launch(Dispatchers.IO) {
            val bonded = bluetoothAdapter?.bondedDevices ?: return@launch
            val bondedMacs = bonded.map { it.address }

            withContext(Dispatchers.Main) {
                foundDevices.removeAll { it.type == "PAIRED" && it.mac !in bondedMacs }
            }

            bonded.forEach { d ->
                val btClass = d.bluetoothClass?.deviceClass
                val uuids = d.uuids
                val hasSpp = uuids?.any { u -> u.uuid.toString().contains("00001101", ignoreCase = true) } ?: false

                withContext(Dispatchers.Main) {
                    if (foundDevices.none { it.mac == d.address }) {
                        foundDevices.add(DeviceItem(d.name ?: "Unknown", d.address, "PAIRED", 0, btClass, hasSpp))
                    }
                }
            }
        }
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        if (it.type == BluetoothDevice.DEVICE_TYPE_LE) return@let

                        mainScope.launch {
                            val existingIndex = foundDevices.indexOfFirst { d -> d.mac == it.address && d.type == "Classic" }
                            val btClass = it.bluetoothClass?.deviceClass
                            val uuids = it.uuids
                            val hasSpp = uuids?.any { u -> u.uuid.toString().contains("00001101", ignoreCase = true) } ?: false

                            if (existingIndex == -1) {
                                foundDevices.add(DeviceItem(it.name ?: "Unknown", it.address, "Classic", rssi, btClass, hasSpp))
                                logMsg("[+] Classic Target: ${it.address}", "00E5FF")
                                if (uuids == null) { try { it.fetchUuidsWithSdp() } catch (e: Exception) { e.printStackTrace() } }
                            } else {
                                val old = foundDevices[existingIndex]
                                foundDevices[existingIndex] = old.copy(rssi = rssi, deviceClass = old.deviceClass ?: btClass, hasSpp = old.hasSpp || hasSpp)
                                if (uuids == null && !old.hasSpp) { try { it.fetchUuidsWithSdp() } catch (e: Exception) { e.printStackTrace() } }
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val parcelUuids = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, android.os.ParcelUuid::class.java)
                    } else {
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.map { it as android.os.ParcelUuid }?.toTypedArray()
                    }

                    if (device != null && parcelUuids != null) {
                        val hasSpp = parcelUuids.any { it.uuid.toString().contains("00001101", ignoreCase = true) }
                        if (hasSpp) {
                            mainScope.launch {
                                val idx = foundDevices.indexOfFirst { d -> d.mac == device.address }
                                if (idx != -1) foundDevices[idx] = foundDevices[idx].copy(hasSpp = true)
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (currentScanMode == ScanMode.CLASSIC) {
                        mainScope.launch {
                            delay(1000)
                            if (currentScanMode == ScanMode.CLASSIC) {
                                if (classicScanCount < 5) {
                                    classicScanCount++
                                    bluetoothAdapter?.startDiscovery()
                                } else {
                                    currentScanMode = ScanMode.IDLE
                                    logMsg("[*] Classic scan limit reached. Stopped.", "FFA500")
                                    if (isReceiverRegistered) {
                                        isReceiverRegistered = false
                                        try { unregisterReceiver(this@MainActivity.receiver) } catch(e: Exception) { e.printStackTrace() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            advStore.update(result)
            val device = result.device
            val rssi = result.rssi
            val btClass = device.bluetoothClass?.deviceClass
            val hasSpp = result.scanRecord?.serviceUuids?.any { it.uuid.toString().contains("00001101", ignoreCase = true) } ?: false

            mainScope.launch {
                val existingIndex = foundDevices.indexOfFirst { d -> d.mac == device.address && d.type == "BLE" }
                if (existingIndex == -1) {
                    foundDevices.add(DeviceItem(device.name ?: "Unknown", device.address, "BLE", rssi, btClass, hasSpp))
                    logMsg("[+] BLE Target: ${device.address}", "39FF14")
                } else {
                    val old = foundDevices[existingIndex]
                    foundDevices[existingIndex] = old.copy(rssi = rssi, deviceClass = old.deviceClass ?: btClass, hasSpp = old.hasSpp || hasSpp)
                }
            }
        }
    }

    private fun toggleClassicScan() {
        if (currentScanMode == ScanMode.CLASSIC) {
            currentScanMode = ScanMode.IDLE
            bluetoothAdapter?.cancelDiscovery()
            if (isReceiverRegistered) {
                isReceiverRegistered = false
                try { unregisterReceiver(receiver) } catch(e: Exception) { e.printStackTrace() }
            }
        } else {
            if (!checkHardwareStatus()) return
            if (currentScanMode == ScanMode.BLE) toggleBleScan()

            loadPairedDevices()

            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothDevice.ACTION_UUID)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
            }
            classicScanCount = 0
            bluetoothAdapter?.startDiscovery()
            currentScanMode = ScanMode.CLASSIC
            logMsg("Classic Scan Loop started...", "00E5FF")
        }
    }

    private fun toggleBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (currentScanMode == ScanMode.BLE) {
            scanner?.stopScan(leScanCallback)
            currentScanMode = ScanMode.IDLE
        } else {
            if (!checkHardwareStatus()) return
            if (currentScanMode == ScanMode.CLASSIC) toggleClassicScan()

            loadPairedDevices()

            // Low-latency so advertising intervals can be measured; non-legacy so extended
            // advertisements (BT 5) are reported too, when the controller supports them.
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter?.isLeExtendedAdvertisingSupported == true) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }.build()
            scanner?.startScan(null, settings, leScanCallback)
            currentScanMode = ScanMode.BLE
            logMsg("BLE Scan started...", "39FF14")
        }
    }

    @Composable
    fun MainScreen() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(id = R.drawable.disspair_logo), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Pal.c.cyan)) { append("Diss") }
                            withStyle(style = SpanStyle(color = Pal.c.red)) { append("Pair") }
                        }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Bluetooth Analysis Toolkit", color = Pal.c.textMuted, fontSize = 10.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cycleTheme() }) {
                        Icon(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.DarkMode
                            },
                            contentDescription = "Theme: ${themeMode.label} (tap to change)",
                            tint = Pal.c.cyan
                        )
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Local Device", tint = Pal.c.cyan)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(systemLogs.size) { if (systemLogs.isNotEmpty()) listState.animateScrollToItem(systemLogs.size - 1) }
            Box(modifier = Modifier.fillMaxWidth().weight(0.30f).background(Pal.c.console, RoundedCornerShape(8.dp)).border(1.dp, Pal.c.border, RoundedCornerShape(8.dp)).padding(6.dp)) {
                LazyColumn(state = listState) {
                    items(systemLogs) { logPair: Pair<String, String> ->
                        Text(text = logPair.first, color = parseColorSafe(logPair.second), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            val classicBtnColor = if(currentScanMode == ScanMode.CLASSIC) Color.Red else Color(0xFF0F61B7)
            val bleBtnColor = if(currentScanMode == ScanMode.BLE) Color.Red else Color(0xFF008C51)

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { toggleClassicScan() }, colors = ButtonDefaults.buttonColors(containerColor = classicBtnColor), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).height(44.dp)) { Text(if(currentScanMode == ScanMode.CLASSIC) "STOP" else "SCAN CLASSIC", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(modifier = Modifier.width(6.dp))
                Button(onClick = { toggleBleScan() }, colors = ButtonDefaults.buttonColors(containerColor = bleBtnColor), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).height(44.dp)) { Text(if(currentScanMode == ScanMode.BLE) "STOP" else "SCAN BLE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TARGET DEVICES", color = Pal.c.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    IconButton(onClick = { foundDevices.removeAll { it.type != "PAIRED" }; advStore.clear() }, modifier = Modifier.size(28.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Pal.c.danger, modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Unknown", color = Pal.c.textMuted, fontSize = 9.sp)
                    Switch(checked = showUnknownDevices, onCheckedChange = { showUnknownDevices = it }, modifier = Modifier.customScalePadding(0.7f).padding(horizontal = 0.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Paired", color = Pal.c.textMuted, fontSize = 9.sp)
                    Switch(checked = showPairedDevices, onCheckedChange = { showPairedDevices = it; loadPairedDevices() }, modifier = Modifier.customScalePadding(0.7f))
                }
            }

            val displayDevices = foundDevices.filter {
                (it.type != "PAIRED" || showPairedDevices) &&
                        (showUnknownDevices || it.name != "Unknown")
            }

            LazyColumn(modifier = Modifier.weight(0.70f)) {
                items(displayDevices, key = { it.mac + it.type }) { device: DeviceItem ->
                    DeviceCard(
                        device = device,
                        onAnalyze = {
                            if(currentScanMode != ScanMode.IDLE) { if(currentScanMode == ScanMode.CLASSIC) toggleClassicScan() else toggleBleScan() }
                            activeAnalysisDevice = device
                        },
                        onAdv = if (device.type == "BLE") ({ activeAdvDevice = device }) else null,
                        onUnpair = {
                            try {
                                val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)
                                btDevice?.javaClass?.getMethod("removeBond")?.invoke(btDevice)
                                logMsg("[*] Force Unpair command sent to ${device.mac} (May be blocked on Android 14+)", "FFA500")
                                mainScope.launch {
                                    delay(1000)
                                    loadPairedDevices()
                                }
                            } catch(e: Exception) {
                                logMsg("[-] Failed to execute unpair: ${e.message}", "FF2D55")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
            }
            if (showRenameDialog) {
                RenameDeviceDialog(
                    currentName = bluetoothAdapter?.name ?: "",
                    onDismiss = { showRenameDialog = false },
                    onConfirm = { newName ->
                        try {
                            bluetoothAdapter?.name = newName
                            logMsg("[*] Device name changed to: $newName", "00E5FF")
                        } catch (e: Exception) {
                            logMsg("[-] Failed to change name: ${e.message}", "FF2D55")
                        }
                        showRenameDialog = false
                    }
                )
            }
        }
    }

    @Composable
    fun RenameDeviceDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
        var textVal by remember { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.surface,
            title = { Text("Rename Local Device", color = Pal.c.text) },
            text = {
                TextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    placeholder = { Text("Enter new name...") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { onConfirm(textVal) }) { Text("SAVE") }
            },
            dismissButton = {
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Pal.c.neutral)) { Text("CANCEL") }
            }
        )
    }

    @Composable
    fun DeviceCard(device: DeviceItem, onAnalyze: () -> Unit, onUnpair: (() -> Unit)? = null, onAdv: (() -> Unit)? = null) {
        Row(modifier = Modifier.fillMaxWidth().background(Pal.c.surface, RoundedCornerShape(10.dp)).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, color = Pal.c.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(getDeviceClassTag(device.deviceClass), color = Pal.c.textSubtle, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.border(1.dp, Pal.c.textMuted, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Text(text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Pal.c.textStrong)) { append(device.mac) }
                    append(" | ")
                    withStyle(style = SpanStyle(color = when(device.type){
                        "Classic" -> Pal.c.cyan
                        "BLE" -> Pal.c.green
                        else -> Pal.c.orange
                    })) { append(device.type) }
                }, fontSize = 10.sp)
                Text("RSSI: ${device.rssi} dBm", color = if(device.rssi > -70) Pal.c.green else Pal.c.orange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (device.type == "PAIRED") {
                    Button(onClick = { onUnpair?.invoke() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFC1037)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text("UNPAIR", fontSize = 9.sp) }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (onAdv != null) {
                    Button(onClick = onAdv, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008C51)), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text("ADV", fontSize = 9.sp) }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Button(onClick = onAnalyze, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F61B7)), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text("ANALYSE", fontSize = 9.sp) }
            }
        }
    }

    @Composable
    fun AuthModal(onConfirm: () -> Unit, onExit: () -> Unit) {
        AlertDialog(onDismissRequest = onExit, containerColor = Pal.c.surface, title = { Text("Authorized Audit Mode", color = Pal.c.cyan) }, text = { Text("By proceeding, you confirm ownership or testing rights for the target hardware.", color = Pal.c.text) }, confirmButton = { Button(onClick = onConfirm) { Text("I CONFIRM") } }, dismissButton = { Button(onClick = onExit) { Text("EXIT") } })
    }

    @Composable
    fun ClassicPayloadDialog(channel: ChannelUI, onDismiss: () -> Unit, onSend: (String, ByteArray) -> Unit) {
        val payloads: List<Pair<String, ByteArray>> = listOf(
            "AT Command: Connect" to "ATZ\r\n".toByteArray(),
            "PBAP/MAP: OBEX Connect (0x80)" to byteArrayOf(0x80.toByte(), 0x00.toByte(), 0x07.toByte(), 0x10.toByte(), 0x00.toByte(), 0x20.toByte(), 0x00.toByte())
        )

        var customText by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showHelpDialog by remember { mutableStateOf(false) }

        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor = Pal.c.surface,
                title = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AT Commands Guide", color = Pal.c.cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showHelpDialog = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Pal.c.text)
                        }
                    }
                },
                text = {
                    Column {
                        Text("Common commands to test:\n", color = Pal.c.textSecondary, fontSize = 12.sp)
                        Text("• ATZ  (Ping/Reset)", color = Pal.c.text, fontSize = 12.sp)
                        Text("• ATD1234567890;  (Dial number)", color = Pal.c.text, fontSize = 12.sp)
                        Text("• ATA  (Answer call)", color = Pal.c.text, fontSize = 12.sp)
                        Text("• AT+CIND?  (Get device status)", color = Pal.c.text, fontSize = 12.sp)
                        Text("• AT+VGS=15  (Max volume)", color = Pal.c.text, fontSize = 12.sp)
                        Text("• AT+BRSF=0  (HFP handshake)", color = Pal.c.text, fontSize = 12.sp)
                        Text("\nNote: \\r\\n is auto-appended to your input.", color = Pal.c.textSecondary, fontSize = 10.sp)
                    }
                },
                confirmButton = {}
            )
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.surface,
            title = { Text("Select Payload (Ch ${channel.channel})", color = Pal.c.cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Predefined Payloads", color = Pal.c.textSecondary, fontSize = 10.sp)
                    payloads.forEach { p ->
                        Button(
                            onClick = { onSend(p.first, p.second); onDismiss() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF228DC4)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) { Text(p.first, fontSize = 12.sp) }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Pal.c.neutral))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom Payload", color = Pal.c.textSecondary, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { showHelpDialog = true },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text("(?) Help", color = Pal.c.cyan, fontSize = 10.sp)
                        }
                    }

                    TextField(
                        value = customText,
                        onValueChange = { customText = it; errorMsg = null },
                        placeholder = { Text("Enter AT command...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = Pal.c.danger, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val formattedAscii = if (!customText.endsWith("\r\n")) "$customText\r\n" else customText
                                    val b = formattedAscii.toByteArray()

                                    if (b.isEmpty() || customText.isBlank()) {
                                        errorMsg = "ERR: Payload is empty"
                                        return@Button
                                    }
                                    onSend("Custom Entry", b)
                                    onDismiss()
                                } catch(e: Exception) {
                                    errorMsg = "ERR: Failed to process payload"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF008C51)),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("SEND CUSTOM", fontSize = 10.sp) }
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Pal.c.neutral)) { Text("CANCEL") } }
        )
    }

    // --- CLASSIC OVERLAY ---
    @Composable
    fun ClassicAnalysisOverlay(device: DeviceItem, onClose: () -> Unit) {
        var modalLogs = remember { mutableStateListOf<Pair<String, String>>() }
        val channels = remember { mutableStateListOf<ChannelUI>() }
        var isFlooding by remember { mutableStateOf<Int?>(null) }

        var isProbingActiveUI by remember { mutableStateOf(true) }
        val isProbingFlag = remember { AtomicBoolean(true) }

        var extendedProbed by remember { mutableStateOf(false) }
        var resetTrigger by remember { mutableIntStateOf(0) }

        var payloadDialogChannel by remember { mutableStateOf<ChannelUI?>(null) }
        val scope = rememberCoroutineScope()

        fun log(m: String, c: String = "7a7a9a") {
            scope.launch {
                modalLogs.add(m to c)
                if (modalLogs.size > 500) modalLogs.removeAt(0)
            }
        }

        LaunchedEffect(resetTrigger) {
            isProbingActiveUI = true
            isProbingFlag.set(true)
            log("[*] RFCOMM Stack Probe (1-15)...", "00E5FF")
            val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)

            withContext(Dispatchers.IO) {
                val isBonded = btDevice?.bondState == BluetoothDevice.BOND_BONDED

                for (ch in 1..15) {
                    if (!isProbingFlag.get()) break
                    var success = false
                    var sock: BluetoothSocket? = null
                    try {
                        sock = if (isBonded) {
                            btDevice!!.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(btDevice, ch) as BluetoothSocket
                        } else {
                            btDevice!!.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType).invoke(btDevice, ch) as BluetoothSocket
                        }
                        sock.connect()
                        success = true
                    } catch (e: Exception) {
                    } finally {
                        try { sock?.close() } catch (e: Exception) { e.printStackTrace() }
                    }

                    if (success) {
                        val authStr = if (isBonded) "Paired (Secure)" else "Unpaired (Insecure)"
                        withContext(Dispatchers.Main) { channels.add(ChannelUI(ch, authStr, isBonded)) }
                        log("[+] Found Ch $ch ($authStr)", "39FF14")
                    }
                }
                if (isProbingFlag.get()) {
                    log("[*] Scan Sequence (1-15) Finalised.", "00E5FF")
                    withContext(Dispatchers.Main) { isProbingActiveUI = false }
                    isProbingFlag.set(false)
                }
            }
        }

        // Shared by the X button and the system back gesture
        fun closeOverlay() {
            isProbingActiveUI = false
            isProbingFlag.set(false)
            activeFloodJob?.cancel()
            try { activeRfcommSocket?.close() } catch(e: Exception) {}
            onClose()
        }
        BackHandler { closeOverlay() }

        Box(modifier = Modifier.fillMaxSize().background(Pal.c.overlay)) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("CLASSIC ANALYSIS", color = Pal.c.cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${device.name} | ${device.mac}", color = Pal.c.textSecondary, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isProbingActiveUI && !extendedProbed) {
                            Button(onClick = {
                                extendedProbed = true
                                isProbingActiveUI = true
                                isProbingFlag.set(true)
                                scope.launch {
                                    log("[*] RFCOMM Stack Probe (16-30)...", "00E5FF")
                                    val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)
                                    withContext(Dispatchers.IO) {
                                        val isBonded = btDevice?.bondState == BluetoothDevice.BOND_BONDED

                                        for (ch in 16..30) {
                                            if (!isProbingFlag.get()) break
                                            var success = false
                                            var sock: BluetoothSocket? = null
                                            try {
                                                sock = if (isBonded) {
                                                    btDevice!!.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(btDevice, ch) as BluetoothSocket
                                                } else {
                                                    btDevice!!.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType).invoke(btDevice, ch) as BluetoothSocket
                                                }
                                                sock.connect()
                                                success = true
                                            } catch (e: Exception) {
                                            } finally {
                                                try { sock?.close() } catch (e: Exception) { e.printStackTrace() }
                                            }

                                            if (success) {
                                                val authStr = if (isBonded) "Paired (Secure)" else "Unpaired (Insecure)"
                                                withContext(Dispatchers.Main) { channels.add(ChannelUI(ch, authStr, isBonded)) }
                                                log("[+] Found Ch $ch ($authStr)", "39FF14")
                                            }
                                        }
                                        if (isProbingFlag.get()) {
                                            log("[*] Scan Sequence (16-30) Finalised.", "00E5FF")
                                            withContext(Dispatchers.Main) { isProbingActiveUI = false }
                                            isProbingFlag.set(false)
                                        }
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Pal.c.neutral), modifier = Modifier.height(28.dp).padding(end = 4.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("PROBE 16-30", fontSize = 8.sp) }
                        }

                        IconButton(onClick = {
                            isProbingActiveUI = false
                            isProbingFlag.set(false)
                            extendedProbed = false
                            activeFloodJob?.cancel()
                            try { activeRfcommSocket?.close() } catch(e: Exception) {}
                            isFlooding = null
                            channels.clear(); modalLogs.clear(); resetTrigger++
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Pal.c.cyan) }
                        IconButton(onClick = { closeOverlay() }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Pal.c.text) }
                    }
                }
                LazyColumn(modifier = Modifier.weight(0.5f)) {
                    items(channels) { ch ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Pal.c.surfaceAlt, RoundedCornerShape(8.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("Ch ${ch.channel}", color = Pal.c.text, fontSize = 13.sp); Text(ch.authType, color = Pal.c.textSecondary, fontSize = 9.sp) }
                            Row {
                                Button(onClick = { payloadDialogChannel = ch }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C19B3)), modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("PAYLOADS", fontSize=9.sp) }

                                Spacer(modifier = Modifier.width(4.dp))

                                val floodColor = if (isFlooding == ch.channel) Color.Red else Color(0xFF0F61B7)

                                Button(onClick = {
                                    if(isFlooding == ch.channel) {
                                        activeFloodJob?.cancel()
                                        try { activeRfcommSocket?.close() } catch(e: Exception) {}
                                        isFlooding = null
                                        log("[*] Flood Manually Stopped.", "00E5FF")
                                    } else {
                                        if (activeFloodJob?.isActive == true) return@Button
                                        isFlooding = ch.channel
                                        log("[!] Starting Hardware Flood...", "FF2D55")
                                        activeFloodJob = scope.launch(Dispatchers.IO) {
                                            try {
                                                val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)
                                                val methodName = if(ch.isSecure) "createRfcommSocket" else "createInsecureRfcommSocket"
                                                activeRfcommSocket = btDevice!!.javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(btDevice, ch.channel) as BluetoothSocket
                                                activeRfcommSocket!!.connect()
                                                val stream = activeRfcommSocket!!.outputStream
                                                val payload = ByteArray(2048) { 'X'.code.toByte() }
                                                while(isActive) {
                                                    stream.write(payload)
                                                    delay(2)
                                                }
                                            } catch(e: Exception) {
                                                if (isActive) {
                                                    log("[-] Flood stopped. Device crashed or rejected stream.", "FF2D55")
                                                }
                                            } finally {
                                                try { activeRfcommSocket?.close() } catch (e: Exception) { e.printStackTrace() }
                                                withContext(NonCancellable + Dispatchers.Main) { isFlooding = null }
                                            }
                                        }
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = floodColor), modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if(isFlooding==ch.channel) "STOP" else "FLOOD", fontSize=9.sp) }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val mState = rememberLazyListState()
                LaunchedEffect(modalLogs.size) { if(modalLogs.isNotEmpty()) mState.animateScrollToItem(modalLogs.size - 1) }
                Box(modifier = Modifier.fillMaxWidth().weight(0.4f).background(Pal.c.console, RoundedCornerShape(8.dp)).border(1.dp, Pal.c.border, RoundedCornerShape(8.dp)).padding(6.dp)) {
                    LazyColumn(state = mState) {
                        items(modalLogs) { l: Pair<String, String> ->
                            Text(text = l.first, color = parseColorSafe(l.second), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }

            if (payloadDialogChannel != null) {
                val currentCh = payloadDialogChannel!!
                ClassicPayloadDialog(
                    channel = currentCh,
                    onDismiss = { payloadDialogChannel = null },
                    onSend = { payloadName, payloadBytes ->
                        scope.launch(Dispatchers.IO) {
                            var sock: BluetoothSocket? = null
                            var timeoutJob: Job? = null
                            var timedOut = false
                            try {
                                log("[*] Sending payload ($payloadName) on Ch ${currentCh.channel}...", "00E5FF")
                                val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)
                                val methodName = if(currentCh.isSecure) "createRfcommSocket" else "createInsecureRfcommSocket"
                                sock = btDevice!!.javaClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(btDevice, currentCh.channel) as BluetoothSocket

                                sock.connect()
                                val outStream = sock.outputStream
                                val inStream = sock.inputStream

                                delay(200)
                                val greetingBytes = inStream.available()
                                var isHfpLocked = false

                                if (greetingBytes > 0) {
                                    val gBuffer = ByteArray(greetingBytes)
                                    inStream.read(gBuffer)
                                    val gHex = gBuffer.joinToString("") { "%02X".format(it) }
                                    val gAscii = hexToAsciiSafe(gHex)

                                    if (gAscii.contains("BRSF")) {
                                        isHfpLocked = true
                                        log("[!] Target Handshake Request Captured.", "FFA500")
                                        val features = decodeHfpFeatures(gAscii.substringAfter("BRSF").filter { it.isDigit() })
                                        if (features.isNotEmpty()) {
                                            log("    [+] Target Features: ${features.size} found", "39FF14")
                                        }
                                    } else {
                                        log("[!] Target Greeting Captured:", "FFA500")
                                        log("    ASCII: $gAscii", "FFA500")
                                    }
                                }

                                // --- NEW: AUTO-NEGOTIATION BYPASS ---
                                if (isHfpLocked) {
                                    log("[*] Auto-negotiating HFP bypass...", "00E5FF")
                                    outStream.write("AT+BRSF=0\r\n".toByteArray())
                                    outStream.flush()
                                    delay(300)

                                    val clearBytes = inStream.available()
                                    if (clearBytes > 0) {
                                        inStream.read(ByteArray(clearBytes))
                                    }
                                    log("[+] Port unlocked. Injecting payload...", "39FF14")
                                }

                                outStream.write(payloadBytes)
                                outStream.flush()

                                timeoutJob = launch {
                                    delay(1500)
                                    timedOut = true
                                    try { sock?.close() } catch (e: Exception) {}
                                }

                                val buffer = ByteArray(1024)
                                val bytesRead = inStream.read(buffer)
                                timeoutJob.cancel()

                                if (bytesRead > 0) {
                                    val hexResp = buffer.take(bytesRead).joinToString("") { "%02X".format(it) }
                                    val asciiResp = hexToAsciiSafe(hexResp)

                                    if (asciiResp.contains("BRSF")) {
                                        log("[+] Payload RX HEX:", "39FF14")
                                        log("    $hexResp", "7A7A9A")
                                        val features = decodeHfpFeatures(asciiResp.substringAfter("BRSF").filter { it.isDigit() })
                                        if (features.isNotEmpty()) {
                                            log("    [+] Decoded HFP Features:", "39FF14")
                                            features.forEach { f -> log("      - $f", "39FF14") }
                                        }
                                    } else {
                                        log("[+] Payload RX ASCII: $asciiResp", "39FF14")
                                        log("    Payload RX HEX: $hexResp", "7A7A9A")
                                    }
                                } else {
                                    log("[-] No data returned to payload", "FFA500")
                                }
                            } catch (e: IOException) {
                                if (timedOut) {
                                    log("[-] Read timeout (Target did not reply)", "FFA500")
                                } else {
                                    log("[-] Connection dropped by target.", "FF2D55")
                                }
                            } catch(e: Exception) {
                                timeoutJob?.cancel()
                                if (!timedOut) {
                                    log("[-] Payload Delivery Failed: ${e.message}", "FF2D55")
                                }
                            } finally {
                                try { sock?.close() } catch(e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                )
            }
        }
    }

    // --- BLE OVERLAY ---
    @Composable
    fun BleAnalysisOverlay(device: DeviceItem, onClose: () -> Unit) {
        val modalLogs = remember { mutableStateListOf<Pair<String, String>>() }
        val services = remember { mutableStateListOf<BleServiceUI>() }
        var gattConn: BluetoothGatt? by remember { mutableStateOf(null) }
        var charToWrite by remember { mutableStateOf<GattCharUI?>(null) }
        var isConnected by remember { mutableStateOf(false) }
        var resetTrigger by remember { mutableIntStateOf(0) }
        var showHex by remember { mutableStateOf(true) }
        var mtu by remember { mutableIntStateOf(23) }
        var isAuditing by remember { mutableStateOf(false) }
        var auditJob by remember { mutableStateOf<Job?>(null) }
        var showAuditOptions by remember { mutableStateOf(false) }
        var lastReport by remember { mutableStateOf<AuditReport?>(null) }

        val ops = remember { GattOps() }
        val activeSessionId = remember { AtomicInteger(0) }

        val scope = rememberCoroutineScope()
        fun log(m: String, c: String = "7a7a9a") {
            scope.launch {
                modalLogs.add(m to c)
                if (modalLogs.size > 500) modalLogs.removeAt(0)
            }
        }

        fun findChar(c: BluetoothGattCharacteristic): GattCharUI? {
            services.forEach { s ->
                s.characteristics.forEach {
                    if (it.charObj.uuid == c.uuid && it.charObj.instanceId == c.instanceId) return it
                }
            }
            return null
        }

        fun teardown() {
            auditJob?.cancel()
            auditJob = null
            isAuditing = false
            ops.cancelPending()
            ops.gatt = null
        }

        val currentGatt by rememberUpdatedState(gattConn)
        DisposableEffect(Unit) {
            onDispose {
                val g = currentGatt
                activeSessionId.incrementAndGet()
                teardown()
                try { g?.disconnect() } catch(e: Exception) { e.printStackTrace() }
                try { g?.close() } catch(e: Exception) { e.printStackTrace() }
            }
        }

        LaunchedEffect(resetTrigger) {
            val mySessionId = activeSessionId.incrementAndGet()

            log("[*] Initiating GATT Handshake...", "39FF14")
            val btDevice = bluetoothAdapter?.getRemoteDevice(device.mac)
            val callback = object : BluetoothGattCallback() {
                private fun live() = activeSessionId.get() == mySessionId

                private fun handleRead(char: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
                    if (!live()) return
                    ops.complete(GattOps.key(GattOps.READ, char.uuid), GattResult(status, value))
                    if (status == 0) scope.launch { findChar(char)?.lastValue?.value = value.toHex() }
                }

                private fun handleChanged(char: BluetoothGattCharacteristic, value: ByteArray?) {
                    if (!live()) return
                    val hex = value.toHex()
                    scope.launch {
                        findChar(char)?.lastValue?.value = hex
                        log("[NOTIFY] ${char.uuid.toString().take(8)}: $hex", "00E5FF")
                    }
                }

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (!live()) {
                        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            try { gatt.close() } catch(e: Exception) { e.printStackTrace() }
                        }
                        return
                    }

                    val connected = (newState == BluetoothProfile.STATE_CONNECTED)
                    if (connected) {
                        ops.gatt = gatt
                    } else {
                        ops.cancelPending()
                        ops.gatt = null
                        try { gatt.javaClass.getMethod("refresh").invoke(gatt) } catch(e: Exception) {}
                        try { gatt.close() } catch(e: Exception) {}
                    }

                    scope.launch(Dispatchers.Main) {
                        isConnected = connected
                        if (connected) {
                            gatt.discoverServices()
                            log("[+] Connected", "39FF14")
                        } else {
                            auditJob?.cancel()
                            isAuditing = false
                            mtu = 23
                            log("[-] Disconnected (${GattStatus.name(status)})", "FF2D55")
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    scope.launch(Dispatchers.Main) {
                        if (!live()) return@launch

                        log("[+] GATT Enumeration Complete. Found ${gatt.services.size} Services.", "39FF14")

                        gatt.services.forEach { s ->
                            val uuidStr = s.uuid.toString().lowercase()
                            // 16-bit UUIDs live on the standard Bluetooth Base UUID
                            val is16Bit = uuidStr.endsWith("-0000-1000-8000-00805f9b34fb")
                            val sName = if (is16Bit) "General" else "Customized"

                            val chars = s.characteristics.map { c ->
                                val p = c.properties
                                GattCharUI(
                                    uuid = c.uuid.toString(),
                                    name = BleNames.resolve(c.uuid.toString()),
                                    properties = GattProps.describe(p),
                                    canRead = p and GattProps.READ != 0,
                                    canWrite = p and GattProps.WRITE != 0,
                                    canNotify = p and (GattProps.NOTIFY or GattProps.INDICATE) != 0,
                                    charObj = c,
                                    canWriteNoResp = p and GattProps.WRITE_NO_RESPONSE != 0,
                                )
                            }
                            services.add(BleServiceUI(s.uuid.toString(), sName, chars.toMutableList()))
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    // API 33+ calls both overloads; only handle the legacy one on older releases.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleRead(char, char.value, status)
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    handleRead(char, value, status)
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                    if (!live()) return
                    ops.complete(GattOps.key(GattOps.WRITE, char.uuid), GattResult(status))
                }

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handleChanged(char, char.value)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                    handleChanged(char, value)
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (!live()) return
                    ops.complete(GattOps.key(GattOps.DESC_WRITE, descriptor.uuid), GattResult(status))
                }

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (!live() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                    ops.complete(GattOps.key(GattOps.DESC_READ, descriptor.uuid), GattResult(status, descriptor.value))
                }

                override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
                    if (!live()) return
                    ops.complete(GattOps.key(GattOps.DESC_READ, descriptor.uuid), GattResult(status, value))
                }

                override fun onMtuChanged(gatt: BluetoothGatt, newMtu: Int, status: Int) {
                    if (!live()) return
                    ops.complete(GattOps.key(GattOps.MTU, null), GattResult(status, mtu = newMtu))
                }
            }
            withContext(Dispatchers.Main) {
                gattConn = btDevice?.connectGatt(this@MainActivity, false, callback, BluetoothDevice.TRANSPORT_LE)
                ops.gatt = gattConn
            }
        }

        fun runAudit(options: AuditOptions) {
            val g = gattConn ?: return
            val btDevice = g.device
            isAuditing = true
            services.forEach { s -> s.characteristics.forEach { it.audit.value = null } }
            log("[*] GATT access audit started (read=${options.testRead} sub=${options.testSubscribe} echoWrite=${options.echoWrite})", "FFB000")
            auditJob = scope.launch {
                try {
                    val audit = GattAudit(ops, btDevice, { BleNames.resolve(it) }, { m, c -> log(m, c) })
                    val report = audit.run(g.services, options, mtu) { result ->
                        services.forEach { s ->
                            s.characteristics.forEach {
                                if (it.uuid == result.charUuid && s.uuid == result.serviceUuid) it.audit.value = result
                            }
                        }
                    }
                    lastReport = report
                    log("[+] Audit complete: ${report.exposedCount}/${report.results.size} usable without pairing", if (report.exposedCount > 0) "FF2D55" else "39FF14")
                    if (report.bondChangedDuringAudit) log("[!] Bond state changed during audit - re-run unbonded to confirm", "FFB000")
                } catch (e: CancellationException) {
                    log("[-] Audit cancelled", "FFB000")
                    throw e
                } catch (e: Exception) {
                    log("[-] Audit failed: ${e.message}", "FF2D55")
                } finally {
                    isAuditing = false
                }
            }
        }

        fun copyReport(r: AuditReport) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("DissPair GATT audit", r.toText()))
            log("[+] Report copied to clipboard", "39FF14")
        }

        fun shareReport(r: AuditReport) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "DissPair GATT audit - ${r.deviceMac}")
                putExtra(Intent.EXTRA_TEXT, r.toText())
            }
            try { startActivity(Intent.createChooser(send, "Share audit report")) } catch (e: Exception) { log("[-] Share failed", "FF2D55") }
        }

        val busy = !isConnected || isAuditing

        // Shared by the X button and the system back gesture
        fun closeOverlay() {
            val g = gattConn
            gattConn = null
            activeSessionId.incrementAndGet()
            teardown()
            try { g?.disconnect() } catch(e: Exception) {}
            onClose()
        }
        BackHandler { closeOverlay() }

        Box(modifier = Modifier.fillMaxSize().background(Pal.c.overlay)) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("GATT ANALYSIS", color = if(isConnected) Pal.c.green else Pal.c.textSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${device.name} | ${device.mac} | MTU $mtu", color = Pal.c.textSecondary, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HEX", color = Pal.c.textMuted, fontSize = 9.sp)
                        Switch(checked = showHex, onCheckedChange = { showHex = it }, modifier = Modifier.customScalePadding(0.7f).padding(horizontal = 0.dp))

                        IconButton(onClick = {
                            val g = gattConn
                            gattConn = null
                            activeSessionId.incrementAndGet()
                            teardown()
                            try { g?.disconnect() } catch(e: Exception) {}
                            services.clear()
                            charToWrite = null
                            modalLogs.clear()
                            lastReport = null
                            isConnected = false
                            mtu = 23
                            resetTrigger++
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Pal.c.green) }
                        IconButton(onClick = { closeOverlay() }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Pal.c.text) }
                    }
                }

                // --- Toolbar ---
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallAction("MTU 517", Color(0xFF0F61B7), enabled = !busy) {
                        scope.launch {
                            val r = ops.requestMtu(517)
                            if (r.ok) mtu = r.mtu
                            log("[MTU] request 517 -> ${if (r.ok) "negotiated ${r.mtu}" else r.statusName}", if (r.ok) "39FF14" else "FF2D55")
                        }
                    }
                    if (isAuditing) {
                        SmallAction("STOP", Color(0xFFB71C1C), enabled = true) { auditJob?.cancel() }
                    } else {
                        SmallAction("AUDIT", Color(0xFFB36B00), enabled = !busy && services.isNotEmpty()) { showAuditOptions = true }
                    }
                    lastReport?.let { r ->
                        SmallAction("COPY", Color(0xFF2E7D32), enabled = true) { copyReport(r) }
                        SmallAction("SHARE", Color(0xFF2E7D32), enabled = true) { shareReport(r) }
                    }
                }

                LazyColumn(modifier = Modifier.weight(0.6f)) {
                    services.forEach { s ->
                        item { Text(s.name.uppercase(), color = Pal.c.green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
                        items(s.characteristics) { char: GattCharUI ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Pal.c.surfaceAlt, RoundedCornerShape(8.dp)).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(char.name, color = Pal.c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${char.uuid.take(18)}  [${char.properties}]", color = Pal.c.textSecondary, fontSize = 9.sp)
                                        char.audit.value?.let { a ->
                                            Text(
                                                (if (a.exposed) "UNPAIRED ACCESS  " else "") + a.shortLine(),
                                                color = if (a.exposed) Pal.c.red else Pal.c.slate,
                                                fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if(char.lastValue.value.isNotEmpty()) {
                                            val displayTxt = if(showHex) "Hex: ${char.lastValue.value}" else "ASCII: ${hexToAsciiSafe(char.lastValue.value)}"
                                            Text(displayTxt, color = Pal.c.green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if(char.canRead) SmallAction("READ", Color(0xFF0F61B7), enabled = !busy) {
                                        scope.launch {
                                            val r = ops.read(char.charObj)
                                            log("[READ] ${char.uuid.take(8)}: ${if (r.ok) r.value.toHex() else r.statusName}", if (r.ok) "39FF14" else "FF2D55")
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    if(char.canWrite || char.canWriteNoResp) SmallAction("WRITE", Color(0xFF8C19B3), enabled = !busy) {
                                        charToWrite = char
                                    }
                                    if(char.canNotify) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val on = char.notifying.value
                                        SmallAction(if (on) "UNSUB" else "SUB", if (on) Color(0xFF00838F) else Color(0xFF00505A), enabled = !busy) {
                                            scope.launch {
                                                val r = ops.setSubscription(char.charObj, !on)
                                                if (r.ok) char.notifying.value = !on
                                                log("[CCCD] ${char.uuid.take(8)} ${if (!on) "subscribe" else "unsubscribe"} -> ${r.statusName}", if (r.ok) "39FF14" else "FF2D55")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val mState = rememberLazyListState()
                LaunchedEffect(modalLogs.size) { if(modalLogs.isNotEmpty()) mState.animateScrollToItem(modalLogs.size - 1) }
                Box(modifier = Modifier.fillMaxWidth().weight(0.3f).background(Pal.c.console, RoundedCornerShape(8.dp)).border(1.dp, Pal.c.border, RoundedCornerShape(8.dp)).padding(6.dp)) {
                    LazyColumn(state = mState) {
                        items(modalLogs) { l: Pair<String, String> ->
                            Text(text = l.first, color = parseColorSafe(l.second), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }

            charToWrite?.let { target ->
                BleWriteDialog(target, maxPayload = mtu - 3, onDismiss = { charToWrite = null }) { bytes, writeType ->
                    scope.launch {
                        val r = ops.write(target.charObj, bytes, writeType)
                        val mode = if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) "WnR" else "W"
                        log("[WRITE/$mode] ${target.uuid.take(8)} ${bytes.size}B -> ${r.statusName}", if (r.ok) "39FF14" else "FF2D55")
                    }
                }
            }

            if (showAuditOptions) {
                AuditOptionsDialog(onDismiss = { showAuditOptions = false }) { opts ->
                    showAuditOptions = false
                    runAudit(opts)
                }
            }
        }
    }

    @Composable
    fun SmallAction(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
        Button(
            enabled = enabled,
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(28.dp)
        ) { Text(label, fontSize = 9.sp) }
    }

    @Composable
    fun AuditOptionsDialog(onDismiss: () -> Unit, onStart: (AuditOptions) -> Unit) {
        var testRead by remember { mutableStateOf(true) }
        var testSub by remember { mutableStateOf(true) }
        var echoWrite by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.surface,
            title = { Text("GATT Access Audit", color = Pal.c.text) },
            text = {
                Column {
                    Text(
                        "Tests what each characteristic allows WITHOUT pairing. Run it on an unbonded device; " +
                        "if Android shows a pairing prompt, decline it to keep results unauthenticated.",
                        color = Pal.c.textSecondary, fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = testRead, onCheckedChange = { testRead = it })
                        Text("Read readable characteristics", color = Pal.c.text, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = testSub, onCheckedChange = { testSub = it })
                        Text("Subscribe / unsubscribe (CCCD)", color = Pal.c.text, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = echoWrite, onCheckedChange = { echoWrite = it })
                        Text("Echo-write (writes back the value just read)", color = Pal.c.text, fontSize = 12.sp)
                    }
                    if (echoWrite) {
                        Text(
                            "Echo-write sends real writes. It only rewrites a value read moments earlier, and never touches " +
                            "write-only characteristics, but some devices act on any write (e.g. trigger commands). Use only on hardware you own.",
                            color = Pal.c.amber, fontSize = 10.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = testRead || testSub || echoWrite,
                    onClick = { onStart(AuditOptions(testRead, testSub, echoWrite)) }
                ) { Text("START") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
        )
    }

    @Composable
    fun BleWriteDialog(char: GattCharUI, maxPayload: Int, onDismiss: () -> Unit, onSend: (ByteArray, Int) -> Unit) {
        var textVal by remember { mutableStateOf("") }
        var isHex by remember { mutableStateOf(false) }
        // Default to write-with-response when available: it returns an ATT status.
        var noResponse by remember { mutableStateOf(!char.canWrite && char.canWriteNoResp) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Pal.c.surface,
            title = { Text("GATT Payload", color = Pal.c.text) },
            text = {
                Column {
                    TextField(
                        value = textVal,
                        onValueChange = { textVal = it; errorMsg = null },
                        placeholder = { Text("Data payload...") }
                    )
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = Pal.c.danger, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isHex, onCheckedChange = { isHex = it; errorMsg = null })
                        Text("Hex Mode", color = Pal.c.text)
                    }
                    if (char.canWrite && char.canWriteNoResp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = noResponse, onCheckedChange = { noResponse = it })
                            Text("Write without response", color = Pal.c.text)
                        }
                    } else {
                        Text(if (char.canWrite) "Mode: write with response" else "Mode: write without response", color = Pal.c.textSecondary, fontSize = 10.sp)
                    }
                    Text("Single-write limit at current MTU: $maxPayload bytes", color = Pal.c.textSecondary, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val clean = textVal.replace(" ", "")
                        if (isHex && clean.length % 2 != 0) {
                            errorMsg = "ERR: Hex needs an even number of digits"
                            return@Button
                        }
                        val b = if(isHex) clean.chunked(2).map{it.toInt(16).toByte()}.toByteArray() else textVal.toByteArray()
                        if (b.isEmpty()) {
                            errorMsg = "ERR: Payload is empty"
                            return@Button
                        }
                        val type = if (noResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        onSend(b, type)
                        onDismiss()
                    } catch(e: Exception) {
                        errorMsg = "ERR: Invalid Hex Format"
                    }
                }) { Text("SEND") }
            }
        )
    }
}
