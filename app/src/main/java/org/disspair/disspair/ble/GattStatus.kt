package org.disspair.disspair.ble

/**
 * ATT / Android GATT status decoding and access classification.
 *
 * ATT error codes are from Bluetooth Core Spec Vol 3, Part F, 3.4.1.1.
 * Values >= 0x80 are Android stack-specific (not sent over the air).
 */
object GattStatus {
    /** Local: the Android API refused to start the operation (busy, not connected, missing permission). */
    const val LOCAL_REJECTED = -1
    /** Local: no callback arrived within the timeout. */
    const val LOCAL_TIMEOUT = -2

    fun name(status: Int): String = when (status) {
        0x00 -> "SUCCESS"
        0x01 -> "INVALID_HANDLE"
        0x02 -> "READ_NOT_PERMITTED"
        0x03 -> "WRITE_NOT_PERMITTED"
        0x04 -> "INVALID_PDU"
        0x05 -> "INSUFFICIENT_AUTHENTICATION"
        0x06 -> "REQUEST_NOT_SUPPORTED"
        0x07 -> "INVALID_OFFSET"
        0x08 -> "INSUFFICIENT_AUTHORIZATION"
        0x09 -> "PREPARE_QUEUE_FULL"
        0x0A -> "ATTRIBUTE_NOT_FOUND"
        0x0B -> "ATTRIBUTE_NOT_LONG"
        0x0C -> "INSUFFICIENT_KEY_SIZE"
        0x0D -> "INVALID_ATTRIBUTE_VALUE_LENGTH"
        0x0E -> "UNLIKELY_ERROR"
        0x0F -> "INSUFFICIENT_ENCRYPTION"
        0x10 -> "UNSUPPORTED_GROUP_TYPE"
        0x11 -> "INSUFFICIENT_RESOURCES"
        in 0x80..0x9F -> androidName(status)
        in 0xE0..0xFF -> "COMMON_PROFILE_ERROR(0x%02X)".format(status)
        LOCAL_REJECTED -> "LOCAL_REJECTED"
        LOCAL_TIMEOUT -> "LOCAL_TIMEOUT"
        else -> "APP_ERROR(0x%02X)".format(status)
    }

    private fun androidName(status: Int): String = when (status) {
        0x80 -> "NO_RESOURCES"
        0x81 -> "INTERNAL_ERROR"
        0x82 -> "WRONG_STATE"
        0x83 -> "DB_FULL"
        0x84 -> "BUSY"
        0x85 -> "GATT_ERROR"
        0x87 -> "ILLEGAL_PARAMETER"
        0x89 -> "AUTH_FAIL"
        0x8A -> "MORE"
        0x8D -> "ENCRYPTED_NO_MITM"
        0x8F -> "CONNECTION_CONGESTED"
        else -> "ANDROID_STACK(0x%02X)".format(status)
    }

    /** What a single operation's status tells us about the attribute's security requirement. */
    enum class Access(val label: String, val tag: String) {
        OPEN("Open without pairing", "OPEN"),
        AUTHENTICATION("Requires authentication", "AUTH"),
        ENCRYPTION("Requires encryption", "ENC"),
        AUTHORIZATION("Requires authorization", "AUTHZ"),
        KEY_SIZE("Requires stronger key", "KEY"),
        NOT_PERMITTED("Not permitted", "DENY"),
        ERROR("Error", "ERR"),
        NOT_TESTED("Not tested", "--");
    }

    fun classify(status: Int): Access = when (status) {
        0x00 -> Access.OPEN
        0x05, 0x89 -> Access.AUTHENTICATION
        0x0F, 0x8D -> Access.ENCRYPTION
        0x08 -> Access.AUTHORIZATION
        0x0C -> Access.KEY_SIZE
        0x02, 0x03, 0x06 -> Access.NOT_PERMITTED
        else -> Access.ERROR
    }
}

/** GATT characteristic property bits (Core Spec Vol 3, Part G, 3.3.1.1). */
object GattProps {
    const val BROADCAST = 0x01
    const val READ = 0x02
    const val WRITE_NO_RESPONSE = 0x04
    const val WRITE = 0x08
    const val NOTIFY = 0x10
    const val INDICATE = 0x20
    const val SIGNED_WRITE = 0x40
    const val EXTENDED = 0x80

    fun describe(p: Int): String = buildList {
        if (p and READ != 0) add("R")
        if (p and WRITE != 0) add("W")
        if (p and WRITE_NO_RESPONSE != 0) add("WnR")
        if (p and NOTIFY != 0) add("N")
        if (p and INDICATE != 0) add("I")
        if (p and SIGNED_WRITE != 0) add("SW")
        if (p and BROADCAST != 0) add("B")
        if (p and EXTENDED != 0) add("EXT")
    }.joinToString(" ").ifEmpty { "none" }
}

fun ByteArray?.toHex(): String = this?.joinToString("") { "%02X".format(it) }?.ifEmpty { "Empty" } ?: "Empty"
