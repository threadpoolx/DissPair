package org.disspair.disspair.gap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/** What kind of Bluetooth LE address a device uses, and what that means for tracking. */
enum class AddressKind(val label: String, val trackable: Boolean, val explain: String) {
    PUBLIC("Public", true, "Fixed, manufacturer-assigned address. Never changes, so the device can be tracked over time."),
    RANDOM_STATIC("Random static", true, "Random but fixed until power cycle or reset. Trackable for as long as it stays the same."),
    RPA("Resolvable private (RPA)", false, "Rotates periodically; only bonded peers can resolve it. This is the privacy-preserving option."),
    NRPA("Non-resolvable private", false, "Random and rotating, not resolvable by anyone. Typical for non-connectable beacons."),
    UNKNOWN("Unknown", false, "Address type not reported by this Android version."),
}

data class AddressInfo(val kind: AddressKind, val certain: Boolean, val note: String?)

/** Immutable view of everything observed for one advertiser. */
data class AdvSnapshot(
    val mac: String,
    val name: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val packets: Int,
    val rssi: List<Int>,
    val intervalMs: Double?,
    val raw: ByteArray?,
    val payloadVariants: Int,
    val isLegacy: Boolean?,
    val connectable: Boolean?,
    val primaryPhy: Int?,
    val secondaryPhy: Int?,
    val sid: Int?,
    val txPower: Int?,
    val periodicIntervalMs: Double?,
    val address: AddressInfo,
) {
    val seenForMs get() = lastSeenMs - firstSeenMs
}

/**
 * Accumulates advertisements for one device. Mutated from the scan callback thread,
 * read by the UI via [snapshot]; all access is synchronised.
 */
class AdvRecord(val mac: String) {
    private val maxHistory = 240
    private var name: String? = null
    private var firstSeen = 0L
    private var lastSeen = 0L
    private var packets = 0
    private val rssi = ArrayDeque<Int>()
    private val tsNanos = ArrayDeque<Long>()
    private var raw: ByteArray? = null
    private val payloadHashes = HashSet<Int>()
    private var isLegacy: Boolean? = null
    private var connectable: Boolean? = null
    private var primaryPhy: Int? = null
    private var secondaryPhy: Int? = null
    private var sid: Int? = null
    private var txPower: Int? = null
    private var periodicInterval: Int? = null
    private var addressType: Int? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun update(r: ScanResult) {
        val now = System.currentTimeMillis()
        if (firstSeen == 0L) firstSeen = now
        lastSeen = now
        packets++
        r.device.name?.let { name = it }
        push(rssi, r.rssi)
        push(tsNanos, r.timestampNanos)
        r.scanRecord?.bytes?.let { b ->
            raw = b
            if (payloadHashes.size < 1000) payloadHashes += AdvAnalysis.significantHash(b)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isLegacy = r.isLegacy
            connectable = r.isConnectable
            primaryPhy = r.primaryPhy
            secondaryPhy = r.secondaryPhy
            sid = r.advertisingSid
            txPower = r.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }
            periodicInterval = r.periodicAdvertisingInterval.takeIf { it != 0 }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            addressType = r.device.addressType
        }
    }

    private fun <T> push(q: ArrayDeque<T>, v: T) {
        q.addLast(v)
        if (q.size > maxHistory) q.removeFirst()
    }

    @Synchronized
    fun snapshot(): AdvSnapshot = AdvSnapshot(
        mac = mac,
        name = name,
        firstSeenMs = firstSeen,
        lastSeenMs = lastSeen,
        packets = packets,
        rssi = rssi.toList(),
        intervalMs = AdvAnalysis.estimateIntervalMs(tsNanos.toList()),
        raw = raw,
        payloadVariants = payloadHashes.size,
        isLegacy = isLegacy,
        connectable = connectable,
        primaryPhy = primaryPhy,
        secondaryPhy = secondaryPhy,
        sid = sid,
        txPower = txPower,
        periodicIntervalMs = periodicInterval?.let { it * 1.25 },
        address = AdvAnalysis.classifyAddress(mac, addressType),
    )
}

/** Thread-safe store of advertisers seen during scanning. */
class AdvStore {
    private val map = ConcurrentHashMap<String, AdvRecord>()
    private val maxDevices = 500

    fun update(r: ScanResult) {
        val mac = r.device.address
        val rec = map[mac] ?: run {
            if (map.size >= maxDevices) return
            map.getOrPut(mac) { AdvRecord(mac) }
        }
        rec.update(r)
    }

    fun snapshot(mac: String): AdvSnapshot? = map[mac]?.snapshot()
    fun clear() = map.clear()
}

/** Pure analysis helpers (no Android dependencies, unit-testable). */
object AdvAnalysis {

    /** Android's BluetoothDevice.ADDRESS_TYPE_* values (API 34+). */
    const val ADDR_PUBLIC = 0
    const val ADDR_RANDOM = 1

    fun classifyAddress(mac: String, addressType: Int?): AddressInfo {
        val top = mac.substring(0, 2).toIntOrNull(16)?.shr(6) ?: -1
        fun randomKind() = when (top) {
            0b11 -> AddressKind.RANDOM_STATIC
            0b01 -> AddressKind.RPA
            0b00 -> AddressKind.NRPA
            else -> null // 0b10 is reserved for random addresses
        }
        return when (addressType) {
            ADDR_PUBLIC -> AddressInfo(AddressKind.PUBLIC, true, null)
            ADDR_RANDOM -> randomKind()?.let { AddressInfo(it, true, null) }
                ?: AddressInfo(AddressKind.UNKNOWN, true, "Random address with reserved top bits 0b10: spec violation")
            else -> {
                // Before Android 14 the public/random bit isn't exposed. The top two bits only
                // identify the sub-type IF the address is random, so this is a best guess.
                val guess = randomKind()
                AddressInfo(guess ?: AddressKind.UNKNOWN, false,
                    "Android < 14 doesn't report public vs random. Top bits suggest ${guess?.label ?: "a public address"}.")
            }
        }
    }

    /**
     * Estimates the advertising interval from receive timestamps.
     * Phones miss packets (radio shared with Wi-Fi, scan windows), which only makes gaps
     * LONGER, so the lower quartile of gaps is a better estimate than the mean.
     * Returns null until enough samples exist.
     */
    fun estimateIntervalMs(tsNanos: List<Long>): Double? {
        if (tsNanos.size < 6) return null
        val gaps = tsNanos.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.filter { it > 5.0 }.sorted()
        if (gaps.size < 5) return null
        return gaps[gaps.size / 4]
    }

    /**
     * Hash of the significant part of the payload (stops at the zero-length terminator),
     * so trailing padding differences don't count as a new payload variant.
     */
    fun significantHash(b: ByteArray): Int {
        var i = 0
        while (i < b.size) {
            val len = b[i].toInt() and 0xFF
            if (len == 0 || i + 1 + len > b.size) break
            i += 1 + len
        }
        return b.copyOfRange(0, i).contentHashCode()
    }

    fun phyName(p: Int?): String = when (p) {
        null -> "?"
        1 -> "LE 1M"; 2 -> "LE 2M"; 3 -> "LE Coded"
        0 -> "none"
        else -> "PHY $p"
    }

    /** Device-level findings that come from observation over time rather than one packet. */
    fun observationInsights(s: AdvSnapshot): List<Insight> {
        val out = mutableListOf<Insight>()
        val a = s.address
        if (a.kind.trackable) {
            out += Insight(if (a.certain) Severity.WARN else Severity.NOTE,
                "Trackable address (${a.kind.label})" + if (a.certain) "" else " - unconfirmed",
                a.kind.explain)
        }
        if (a.kind == AddressKind.RPA && s.seenForMs > 20 * 60_000) {
            out += Insight(Severity.WARN, "Private address not rotating",
                "This resolvable private address has been seen for ${s.seenForMs / 60_000} min. Most stacks rotate every ~15 min; a stale RPA is effectively static.")
        }
        if (s.payloadVariants > 1) {
            out += Insight(Severity.INFO, "Payload changes over time",
                "${s.payloadVariants} different advertisement payloads observed. Content reflects live device state; compare variants to learn what changes.")
        }
        s.intervalMs?.let {
            if (it < 50) out += Insight(Severity.INFO, "Fast advertiser (~%.0f ms)".format(it),
                "Short intervals are typical when a device is in pairing or setup mode.")
        }
        if (s.connectable == true && s.name == null) {
            out += Insight(Severity.INFO, "Connectable but unnamed",
                "Accepts connections without advertising a name; check its GATT database with ANALYSE.")
        }
        return out
    }

    /** Plain-text report for COPY / SHARE. */
    fun report(s: AdvSnapshot, parsed: AdParseResult): String = buildString {
        appendLine("DissPair Advertisement Report")
        appendLine("Device  : ${s.name ?: "Unknown"} (${s.mac})")
        appendLine("Address : ${s.address.kind.label}${if (s.address.certain) "" else " (unconfirmed)"}")
        s.address.note?.let { appendLine("          $it") }
        appendLine("Observed: ${s.packets} packets over ${s.seenForMs / 1000}s")
        appendLine("Interval: ${s.intervalMs?.let { "~%.0f ms".format(it) } ?: "not enough samples"}")
        if (s.rssi.isNotEmpty()) appendLine("RSSI    : now ${s.rssi.last()} dBm, min ${s.rssi.min()}, max ${s.rssi.max()}, avg ${s.rssi.average().toInt()}")
        appendLine("PDU     : ${when (s.isLegacy) { true -> "legacy"; false -> "extended"; null -> "?" }}, " +
                "${when (s.connectable) { true -> "connectable"; false -> "non-connectable"; null -> "?" }}, " +
                "PHY ${phyName(s.primaryPhy)}/${phyName(s.secondaryPhy)}" + (s.txPower?.let { ", TX $it dBm" } ?: ""))
        appendLine("Variants: ${s.payloadVariants} distinct payloads")
        appendLine("Raw     : ${s.raw?.hex() ?: "-"}")
        val ins = observationInsights(s) + parsed.insights
        if (ins.isNotEmpty()) {
            appendLine(); appendLine("Findings:")
            ins.forEach { appendLine("  [${it.severity}] ${it.title}: ${it.detail}") }
        }
        appendLine(); appendLine("AD structures:")
        parsed.structures.forEach { st ->
            appendLine("  0x%02X %s (%d bytes)".format(st.type, st.typeName, st.data.size))
            st.decoded.forEach { appendLine("      $it") }
        }
        parsed.malformedTail?.let { appendLine("  MALFORMED tail: ${it.hex()}") }
    }
}
