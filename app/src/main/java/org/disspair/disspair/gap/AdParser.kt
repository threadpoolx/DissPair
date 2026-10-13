package org.disspair.disspair.gap

import org.disspair.disspair.ble.BleUuids
import java.util.Locale

/**
 * Advertising / scan-response data decoder (Core Spec Vol 3, Part C, 11 and the
 * Supplement to the Core Specification). Pure Kotlin: no Android dependencies.
 *
 * Note: on Android, ScanRecord.getBytes() concatenates the advertising data and the
 * scan response for legacy advertising, so the two cannot be told apart.
 */
data class AdStructure(
    val type: Int,
    val typeName: String,
    val data: ByteArray,
    /** Decoded, human-readable lines (may be empty if nothing to decode). */
    val decoded: List<String>,
)

enum class Severity { INFO, NOTE, WARN }

/** A finding worth surfacing to the tester. */
data class Insight(val severity: Severity, val title: String, val detail: String)

data class AdParseResult(
    val structures: List<AdStructure>,
    val insights: List<Insight>,
    /** Trailing bytes that did not form a valid AD structure (malformed advertisement). */
    val malformedTail: ByteArray?,
)

object AdParser {

    fun parse(raw: ByteArray?): AdParseResult {
        if (raw == null) return AdParseResult(emptyList(), emptyList(), null)
        val out = mutableListOf<AdStructure>()
        val insights = mutableListOf<Insight>()
        var i = 0
        var malformed: ByteArray? = null
        while (i < raw.size) {
            val len = raw[i].u()
            if (len == 0) break // zero padding terminates the significant part
            if (i + 1 + len > raw.size) {
                malformed = raw.copyOfRange(i, raw.size)
                insights += Insight(
                    Severity.WARN, "Malformed advertisement",
                    "AD length byte 0x%02X at offset %d runs past the end of the payload. Scanners that trust AD lengths can over-read here.".format(len, i)
                )
                break
            }
            val type = raw[i + 1].u()
            val data = raw.copyOfRange(i + 2, i + 1 + len)
            val decoded = mutableListOf<String>()
            decode(type, data, decoded, insights)
            out += AdStructure(type, typeName(type), data, decoded)
            i += 1 + len
        }
        return AdParseResult(out, insights, malformed)
    }

    // ---------------------------------------------------------------------------------

    private fun decode(type: Int, d: ByteArray, out: MutableList<String>, ins: MutableList<Insight>) {
        when (type) {
            0x01 -> if (d.isNotEmpty()) decodeFlags(d[0].u(), out)
            0x02, 0x03 -> uuidList(d, 2, out, ins)
            0x04, 0x05 -> uuidList(d, 4, out, ins)
            0x06, 0x07 -> uuidList(d, 16, out, ins)
            0x14 -> uuidList(d, 2, out, ins, solicited = true)
            0x15 -> uuidList(d, 16, out, ins, solicited = true)
            0x1F -> uuidList(d, 4, out, ins, solicited = true)
            0x08, 0x09 -> {
                val name = String(d, Charsets.UTF_8)
                out += "\"$name\""
                if (Regex("[0-9A-Fa-f]{6,}|[-_ ][0-9A-Fa-f]{4}\\b|\\bS/?N\\b|[0-9]{8,}").containsMatchIn(name)) {
                    ins += Insight(Severity.NOTE, "Name may contain an identifier",
                        "Advertised name \"$name\" looks like it embeds a serial or MAC fragment. That makes the device trackable even if its address rotates.")
                }
            }
            0x0A -> if (d.isNotEmpty()) out += "${d[0].toInt()} dBm (TX power at 0 m reference)"
            0x0D -> if (d.size >= 3) out += "Class of Device 0x%06X".format(le(d, 0, 3))
            0x10 -> if (d.size >= 8) out += "Device ID: source=%d vendor=0x%04X product=0x%04X version=0x%04X"
                .format(le(d, 0, 2), le(d, 2, 2), le(d, 4, 2), le(d, 6, 2))
            0x12 -> if (d.size >= 4) {
                val min = le(d, 0, 2); val max = le(d, 2, 2)
                out += "Preferred connection interval %.2f - %.2f ms".format(min * 1.25, max * 1.25)
            }
            0x16 -> serviceData(d, 2, out, ins)
            0x20 -> serviceData(d, 4, out, ins)
            0x21 -> serviceData(d, 16, out, ins)
            0x19 -> if (d.size >= 2) out += appearance(le(d, 0, 2))
            0x1A -> if (d.size >= 2) out += "Advertising interval %.2f ms".format(le(d, 0, 2) * 0.625)
            0x1B -> if (d.size >= 7) out += "LE device address ${mac(d.copyOfRange(0, 6))} (${if (d[6].u() and 1 == 1) "random" else "public"})"
            0x1C -> if (d.isNotEmpty()) out += when (d[0].u()) {
                0 -> "Peripheral only"; 1 -> "Central only"
                2 -> "Peripheral and Central (Peripheral preferred)"; 3 -> "Peripheral and Central (Central preferred)"
                else -> "Reserved role 0x%02X".format(d[0].u())
            }
            0x24 -> out += "URI: " + String(d, Charsets.UTF_8)
            0x2A, 0x2B, 0x29 -> {
                out += "Bluetooth Mesh traffic"
                ins += Insight(Severity.INFO, "Bluetooth Mesh node", "Device participates in a Mesh network (provisioning, beacons or messages).")
            }
            0x30 -> out += "Broadcast name: \"" + String(d, Charsets.UTF_8) + "\""
            0xFF -> manufacturer(d, out, ins)
        }
    }

    private fun decodeFlags(f: Int, out: MutableList<String>) {
        val bits = listOf(
            0x01 to "LE Limited Discoverable", 0x02 to "LE General Discoverable",
            0x04 to "BR/EDR Not Supported", 0x08 to "LE + BR/EDR Controller", 0x10 to "LE + BR/EDR Host",
        )
        out += bits.filter { f and it.first != 0 }.joinToString(", ") { it.second }.ifEmpty { "No flags set (non-discoverable)" }
        if (f and 0x04 == 0) out += "Dual-mode: also reachable over Classic BR/EDR"
    }

    private fun uuidList(d: ByteArray, w: Int, out: MutableList<String>, ins: MutableList<Insight>, solicited: Boolean = false) {
        var i = 0
        while (i + w <= d.size) {
            val u = uuid(d, i, w)
            out += (if (solicited) "solicits " else "") + BleUuids.label(u)
            BleUuids.riskNote(u)?.let { ins += Insight(Severity.WARN, "${BleUuids.label(u)} advertised", it) }
            i += w
        }
    }

    private fun serviceData(d: ByteArray, w: Int, out: MutableList<String>, ins: MutableList<Insight>) {
        if (d.size < w) return
        val u = uuid(d, 0, w)
        val payload = d.copyOfRange(w, d.size)
        out += "${BleUuids.label(u)}: ${payload.hex()}"
        BleUuids.riskNote(u)?.let { ins += Insight(Severity.WARN, "${BleUuids.label(u)} advertised", it) }
        when (BleUuids.short16(u)) {
            "feaa" -> eddystone(payload, out)
            "fe2c" -> {
                if (payload.size == 3) {
                    out += "Fast Pair model ID 0x%06X (pairing mode)".format(be(payload, 0, 3))
                    ins += Insight(Severity.INFO, "Google Fast Pair in pairing mode",
                        "Broadcasting its model ID, so any phone nearby can start pairing.")
                } else out += "Fast Pair account-key filter (not in pairing mode)"
            }
            "fd6f" -> out += "Exposure Notification rolling identifier"
            "fd5a" -> ins += Insight(Severity.NOTE, "Possible tracker: Samsung SmartTag", "SmartTag service data present.")
            "feed", "feec" -> ins += Insight(Severity.NOTE, "Possible tracker: Tile", "Tile service data present.")
        }
    }

    private fun manufacturer(d: ByteArray, out: MutableList<String>, ins: MutableList<Insight>) {
        if (d.size < 2) { out += "Too short (${d.size} bytes)"; return }
        val cid = le(d, 0, 2)
        val payload = d.copyOfRange(2, d.size)
        out += "${Companies.name(cid)} (0x%04X): ${payload.hex()}".format(cid)
        when (cid) {
            0x004C -> AppleContinuity.decode(payload, out, ins)
            0x0006 -> out += "Microsoft beacon (CDP / Swift Pair family)"
        }
    }

    private fun eddystone(p: ByteArray, out: MutableList<String>) {
        if (p.isEmpty()) return
        when (p[0].u()) {
            0x00 -> if (p.size >= 18) out += "Eddystone-UID namespace=${p.copyOfRange(2, 12).hex()} instance=${p.copyOfRange(12, 18).hex()} (tx ${p[1].toInt()} dBm)"
            0x10 -> if (p.size >= 3) out += "Eddystone-URL ${eddystoneUrl(p.copyOfRange(2, p.size))} (tx ${p[1].toInt()} dBm)"
            0x20 -> if (p.size >= 14) out += "Eddystone-TLM battery %d mV, temp %.2f °C, adv count %d, uptime %.1f s".format(
                be(p, 2, 2), (p[4].toInt() + p[5].u() / 256.0), be(p, 6, 4), be(p, 10, 4) / 10.0)
            0x30 -> out += "Eddystone-EID (rotating ephemeral ID)"
            else -> out += "Eddystone frame 0x%02X".format(p[0].u())
        }
    }

    private fun eddystoneUrl(b: ByteArray): String {
        if (b.isEmpty()) return ""
        val schemes = listOf("http://www.", "https://www.", "http://", "https://")
        val exp = listOf(".com/", ".org/", ".edu/", ".net/", ".info/", ".biz/", ".gov/",
            ".com", ".org", ".edu", ".net", ".info", ".biz", ".gov")
        val sb = StringBuilder(schemes.getOrElse(b[0].u()) { "?" })
        for (k in 1 until b.size) {
            val c = b[k].u()
            sb.append(if (c < exp.size) exp[c] else c.toChar())
        }
        return sb.toString()
    }

    private fun appearance(v: Int): String {
        val cats = listOf("Unknown", "Phone", "Computer", "Watch", "Clock", "Display", "Remote Control",
            "Eye-glasses", "Tag", "Keyring", "Media Player", "Barcode Scanner", "Thermometer",
            "Heart Rate Sensor", "Blood Pressure", "Human Interface Device", "Glucose Meter",
            "Running/Walking Sensor", "Cycling")
        val cat = v shr 6
        return "Appearance 0x%04X: %s (sub-category %d)".format(v, cats.getOrElse(cat) { "category $cat" }, v and 0x3F)
    }

    fun typeName(t: Int): String = when (t) {
        0x01 -> "Flags"
        0x02 -> "Incomplete 16-bit UUIDs"; 0x03 -> "Complete 16-bit UUIDs"
        0x04 -> "Incomplete 32-bit UUIDs"; 0x05 -> "Complete 32-bit UUIDs"
        0x06 -> "Incomplete 128-bit UUIDs"; 0x07 -> "Complete 128-bit UUIDs"
        0x08 -> "Shortened Local Name"; 0x09 -> "Complete Local Name"
        0x0A -> "TX Power Level"; 0x0D -> "Class of Device"
        0x0E -> "Simple Pairing Hash C"; 0x0F -> "Simple Pairing Randomizer R"
        0x10 -> "Device ID"; 0x11 -> "Security Manager OOB Flags"
        0x12 -> "Peripheral Connection Interval Range"
        0x14 -> "16-bit Service Solicitation UUIDs"; 0x15 -> "128-bit Service Solicitation UUIDs"
        0x16 -> "Service Data (16-bit UUID)"; 0x17 -> "Public Target Address"
        0x18 -> "Random Target Address"; 0x19 -> "Appearance"; 0x1A -> "Advertising Interval"
        0x1B -> "LE Bluetooth Device Address"; 0x1C -> "LE Role"
        0x1F -> "32-bit Service Solicitation UUIDs"; 0x20 -> "Service Data (32-bit UUID)"
        0x21 -> "Service Data (128-bit UUID)"; 0x24 -> "URI"; 0x27 -> "LE Supported Features"
        0x29 -> "PB-ADV (Mesh)"; 0x2A -> "Mesh Message"; 0x2B -> "Mesh Beacon"
        0x2C -> "BIGInfo"; 0x2D -> "Broadcast Code"; 0x30 -> "Broadcast Name"
        0x31 -> "Encrypted Advertising Data"; 0xFF -> "Manufacturer Specific Data"
        else -> "Unknown type"
    }

    // --- helpers -------------------------------------------------------------------

    /** Little-endian UUID of width 2/4/16 bytes, as a full 128-bit string. */
    fun uuid(d: ByteArray, off: Int, w: Int): String = when (w) {
        2 -> BleUuids.from16(le(d, off, 2))
        4 -> "%08x-0000-1000-8000-00805f9b34fb".format(le(d, off, 4).toLong() and 0xFFFFFFFFL)
        else -> {
            val b = d.copyOfRange(off, off + 16).reversedArray().hex().lowercase(Locale.US)
            "${b.substring(0, 8)}-${b.substring(8, 12)}-${b.substring(12, 16)}-${b.substring(16, 20)}-${b.substring(20)}"
        }
    }
}

// --- byte helpers shared by the gap package ----------------------------------------
internal fun Byte.u(): Int = toInt() and 0xFF
internal fun ByteArray.hex(): String = joinToString("") { "%02X".format(it) }
internal fun le(d: ByteArray, off: Int, n: Int): Int { var v = 0; for (k in n - 1 downTo 0) v = (v shl 8) or d[off + k].u(); return v }
internal fun be(d: ByteArray, off: Int, n: Int): Int { var v = 0; for (k in 0 until n) v = (v shl 8) or d[off + k].u(); return v }
internal fun mac(b: ByteArray): String = b.reversedArray().joinToString(":") { "%02X".format(it) }

/** Bluetooth SIG company identifiers for common vendors. */
object Companies {
    private val ids = mapOf(
        0x0002 to "Intel", 0x0006 to "Microsoft", 0x000D to "Texas Instruments", 0x000F to "Broadcom",
        0x001D to "Qualcomm", 0x004C to "Apple", 0x0059 to "Nordic Semiconductor", 0x0075 to "Samsung",
        0x0087 to "Garmin", 0x009E to "Bose", 0x00E0 to "Google", 0x012D to "Sony", 0x0171 to "Amazon",
        0x027D to "Huawei", 0x02E5 to "Espressif", 0x038F to "Xiaomi", 0x0499 to "Ruuvi Innovations",
    )
    fun name(id: Int): String = ids[id] ?: "Company"
}
