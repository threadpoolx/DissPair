package org.disspair.disspair.gap

/**
 * Apple Continuity manufacturer data (company ID 0x004C).
 *
 * The payload is a sequence of [type][length][value] messages. Type names follow the
 * public reverse-engineering work on Continuity (furiousMAC, "Handoff All Your Privacy",
 * and the Continuity Wireshark dissector). Field-level meanings inside most messages are
 * undocumented, so values are shown raw except for formats that are well defined (iBeacon).
 */
object AppleContinuity {

    private data class Kind(val name: String, val privacy: String?)

    private val kinds = mapOf(
        0x02 to Kind("iBeacon", null),
        0x03 to Kind("AirPrint", null),
        0x05 to Kind("AirDrop", "AirDrop advertisements carry short hashes of the sender's contact identifiers (phone/email); published research shows these can be matched against known numbers."),
        0x06 to Kind("HomeKit", null),
        0x07 to Kind("Proximity Pairing (AirPods / Beats)", "Reveals the accessory model and battery/case state to anyone nearby."),
        0x08 to Kind("\"Hey Siri\"", null),
        0x09 to Kind("AirPlay Target", null),
        0x0A to Kind("AirPlay Source", null),
        0x0B to Kind("Magic Switch (Apple Watch)", null),
        0x0C to Kind("Handoff", "Changes as the user switches activity between Apple devices."),
        0x0D to Kind("Wi-Fi Settings / Tethering Target", null),
        0x0E to Kind("Instant Hotspot / Tethering Source", "Hotspot source advertisements can include battery and cellular signal state."),
        0x0F to Kind("Nearby Action", "Sent during actions such as Wi-Fi password sharing or setting up a new device."),
        0x10 to Kind("Nearby Info", "Encodes device activity state (e.g. screen on/off, in a call); changes in this message track what the user is doing."),
        0x12 to Kind("Find My (offline finding)", "Offline-finding beacon: an AirTag or a Find My device separated from its owner."),
    )

    fun decode(p: ByteArray, out: MutableList<String>, ins: MutableList<Insight>) {
        var i = 0
        var any = false
        while (i + 2 <= p.size) {
            val type = p[i].u()
            val len = p[i + 1].u()
            if (i + 2 + len > p.size) {
                out += "  Continuity 0x%02X: declared length %d exceeds payload (truncated)".format(type, len)
                break
            }
            val v = p.copyOfRange(i + 2, i + 2 + len)
            val kind = kinds[type]
            out += "  Continuity ${kind?.name ?: "type 0x%02X".format(type)}: ${v.hex().ifEmpty { "(empty)" }}"
            if (type == 0x02) iBeacon(v, out, ins)
            kind?.privacy?.let { ins += Insight(Severity.NOTE, "Apple ${kind.name}", it) }
            if (type == 0x12) ins += Insight(Severity.WARN, "Possible tracker: Find My device",
                "Offline-finding beacon seen. If this persists near you while its owner isn't, it may be a tracker.")
            any = true
            i += 2 + len
        }
        if (!any && p.isNotEmpty()) out += "  (not Continuity TLV format)"
    }

    private fun iBeacon(v: ByteArray, out: MutableList<String>, ins: MutableList<Insight>) {
        if (v.size < 21) return
        val u = v.copyOfRange(0, 16).hex().lowercase()
        val uuid = "${u.substring(0, 8)}-${u.substring(8, 12)}-${u.substring(12, 16)}-${u.substring(16, 20)}-${u.substring(20)}"
        out += "  iBeacon UUID=$uuid major=${be(v, 16, 2)} minor=${be(v, 18, 2)} measuredPower=${v[20].toInt()} dBm"
        ins += Insight(Severity.INFO, "iBeacon", "Static beacon identity: spoofable by anyone who copies UUID/major/minor.")
    }
}
