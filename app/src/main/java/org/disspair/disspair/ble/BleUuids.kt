package org.disspair.disspair.ble

import java.util.Locale
import java.util.UUID

/**
 * Human-readable names for Bluetooth SIG-assigned and well-known vendor UUIDs.
 * Used by the GATT screen and the advertisement decoder.
 */
object BleUuids {
    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Returns the 16-bit short form ("180f") if the UUID is on the Bluetooth Base UUID, else null. */
    fun short16(uuid: String): String? {
        val u = uuid.lowercase(Locale.US)
        return if (u.length == 36 && u.startsWith("0000") && u.endsWith(BASE_SUFFIX)) u.substring(4, 8) else null
    }

    fun short16(uuid: UUID) = short16(uuid.toString())

    /** Builds a full UUID string from a 16-bit value. */
    fun from16(v: Int): String = "0000%04x$BASE_SUFFIX".format(v and 0xFFFF)

    fun name(uuid: String): String? {
        val s = short16(uuid)
        return if (s != null) sig16[s] else vendor128[uuid.lowercase(Locale.US)]
    }

    fun name(uuid: UUID) = name(uuid.toString())

    /** Short display label: name if known, otherwise "0x180F" / the full 128-bit UUID. */
    fun label(uuid: String): String {
        name(uuid)?.let { return it }
        return short16(uuid)?.let { "0x" + it.uppercase(Locale.US) } ?: uuid
    }

    /** UUIDs whose presence is worth flagging during an assessment. */
    fun riskNote(uuid: String): String? = risky[short16(uuid) ?: uuid.lowercase(Locale.US)]

    // --- Bluetooth SIG 16-bit UUIDs: services, characteristics, descriptors, member services ---
    private val sig16 = mapOf(
        // Services
        "1800" to "Generic Access", "1801" to "Generic Attribute", "1802" to "Immediate Alert",
        "1803" to "Link Loss", "1804" to "Tx Power", "1805" to "Current Time",
        "1806" to "Reference Time Update", "1807" to "Next DST Change", "1808" to "Glucose",
        "1809" to "Health Thermometer", "180a" to "Device Information", "180d" to "Heart Rate",
        "180e" to "Phone Alert Status", "180f" to "Battery", "1810" to "Blood Pressure",
        "1811" to "Alert Notification", "1812" to "Human Interface Device", "1813" to "Scan Parameters",
        "1814" to "Running Speed and Cadence", "1816" to "Cycling Speed and Cadence",
        "1818" to "Cycling Power", "1819" to "Location and Navigation", "181a" to "Environmental Sensing",
        "181c" to "User Data", "181d" to "Weight Scale", "181e" to "Bond Management",
        "1822" to "Pulse Oximeter", "1826" to "Fitness Machine",
        "1827" to "Mesh Provisioning", "1828" to "Mesh Proxy",
        // Characteristics
        "2a00" to "Device Name", "2a01" to "Appearance", "2a02" to "Peripheral Privacy Flag",
        "2a03" to "Reconnection Address", "2a04" to "Peripheral Preferred Connection Parameters",
        "2a05" to "Service Changed", "2a06" to "Alert Level", "2a07" to "Tx Power Level",
        "2a08" to "Date Time", "2a0f" to "Local Time Information", "2a19" to "Battery Level",
        "2a23" to "System ID", "2a24" to "Model Number String", "2a25" to "Serial Number String",
        "2a26" to "Firmware Revision String", "2a27" to "Hardware Revision String",
        "2a28" to "Software Revision String", "2a29" to "Manufacturer Name String",
        "2a2a" to "IEEE Regulatory Certification", "2a2b" to "Current Time",
        "2a37" to "Heart Rate Measurement", "2a38" to "Body Sensor Location",
        "2a39" to "Heart Rate Control Point", "2a4a" to "HID Information", "2a4b" to "Report Map",
        "2a4c" to "HID Control Point", "2a4d" to "Report", "2a4e" to "Protocol Mode",
        "2a50" to "PnP ID", "2aa6" to "Central Address Resolution",
        "2ac9" to "Resolvable Private Address Only", "2b29" to "Client Supported Features",
        "2b2a" to "Database Hash", "2b3a" to "Server Supported Features",
        // Descriptors
        "2900" to "Characteristic Extended Properties", "2901" to "Characteristic User Description",
        "2902" to "Client Characteristic Configuration", "2903" to "Server Characteristic Configuration",
        "2904" to "Characteristic Presentation Format",
        // Member / vendor 16-bit service UUIDs
        "fe2c" to "Google Fast Pair", "feaa" to "Eddystone (Google)",
        "fd6f" to "Exposure Notification", "fe59" to "Nordic Secure DFU",
        "feed" to "Tile", "feec" to "Tile", "fd5a" to "Samsung SmartTag",
    )

    // --- Well-known vendor 128-bit UUIDs ---
    private val vendor128 = mapOf(
        // Apple Notification Center Service
        "7905f431-b5ce-4e99-a40f-4b1e122d00d0" to "Apple Notification Center Service (ANCS)",
        "9fbf120d-6301-42d9-8c58-25e699a21dbd" to "ANCS Notification Source",
        "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9" to "ANCS Control Point",
        "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb" to "ANCS Data Source",
        // Apple Media Service
        "89d3502b-0f36-433a-8ef4-c502ad55f8dc" to "Apple Media Service (AMS)",
        "9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2" to "AMS Remote Command",
        "2f7cabce-808d-411f-9a0c-bb92ba96c102" to "AMS Entity Update",
        "c6b2f38c-23ab-46d8-a6ab-a3a870bbd5d7" to "AMS Entity Attribute",
        // Apple Continuity
        "d0611e78-bbb4-4591-a5f8-487910ae4366" to "Apple Continuity",
        "8667556c-9a37-4c91-84ed-54ee27d90049" to "Apple Continuity",
        "9fa480e0-4967-4542-9390-d343dc5d04ae" to "Apple (proprietary)",
        // Nordic
        "6e400001-b5a3-f393-e0a9-e50e24dcca9e" to "Nordic UART Service",
        "6e400002-b5a3-f393-e0a9-e50e24dcca9e" to "Nordic UART RX (write)",
        "6e400003-b5a3-f393-e0a9-e50e24dcca9e" to "Nordic UART TX (notify)",
        "00001530-1212-efde-1523-785feabcd123" to "Nordic Legacy DFU",
        "8ec90001-f315-4f60-9fb8-838830daea50" to "Nordic Secure DFU Control Point",
        "8ec90002-f315-4f60-9fb8-838830daea50" to "Nordic Secure DFU Packet",
        "8ec90003-f315-4f60-9fb8-838830daea50" to "Nordic Buttonless DFU",
    )

    private val risky = mapOf(
        "fe59" to "Firmware update (DFU) service exposed: check that updates are signed and require pairing",
        "00001530-1212-efde-1523-785feabcd123" to "Legacy Nordic DFU: historically accepts unsigned firmware",
        "8ec90003-f315-4f60-9fb8-838830daea50" to "Buttonless DFU: one write can reboot the device into bootloader",
        "6e400001-b5a3-f393-e0a9-e50e24dcca9e" to "Nordic UART: raw command channel, test for unauthenticated commands",
    )
}

/** Renders bytes as text when they are mostly printable ASCII, else null. */
fun ByteArray?.printableOrNull(): String? {
    if (this == null || isEmpty()) return null
    val printable = count { it in 0x20..0x7E || it == 0x0A.toByte() || it == 0x0D.toByte() }
    if (printable < size * 0.85) return null
    return String(this, Charsets.US_ASCII).trimEnd('\u0000')
}
