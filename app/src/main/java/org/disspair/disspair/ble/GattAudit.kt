package org.disspair.disspair.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unauthenticated GATT access audit.
 *
 * For every characteristic it records what an attacker who has NOT paired can do:
 *  - READ:      read the value (non-destructive)
 *  - SUBSCRIBE: enable notifications/indications via the CCCD, then disable again
 *  - WRITE:     only when [AuditOptions.echoWrite] is on, and only for characteristics
 *               that were successfully read first: the value just read is written back
 *               unchanged. Write-only characteristics are never written, because there is
 *               no known-safe value and an arbitrary write can reconfigure a device.
 *
 * Results are only meaningful as "unauthenticated" findings if the device was not bonded
 * when the audit ran ([AuditReport.bondedAtStart]). Android may start pairing on its own
 * when a peripheral answers INSUFFICIENT_AUTHENTICATION; [AuditReport.bondChangedDuringAudit]
 * flags that so later results can be read with care.
 */
data class AuditOptions(
    val testRead: Boolean = true,
    val testSubscribe: Boolean = true,
    val echoWrite: Boolean = false,
)

data class CharAudit(
    val serviceUuid: String,
    val charUuid: String,
    val name: String,
    val properties: Int,
    val read: GattStatus.Access = GattStatus.Access.NOT_TESTED,
    val readStatus: Int? = null,
    val readValue: ByteArray? = null,
    val subscribe: GattStatus.Access = GattStatus.Access.NOT_TESTED,
    val subscribeStatus: Int? = null,
    val write: GattStatus.Access = GattStatus.Access.NOT_TESTED,
    val writeStatus: Int? = null,
    val note: String = "",
) {
    /** Anything usable without pairing. */
    val exposed: Boolean
        get() = read == GattStatus.Access.OPEN ||
                subscribe == GattStatus.Access.OPEN ||
                write == GattStatus.Access.OPEN

    fun shortLine(): String =
        "R:${read.tag} S:${subscribe.tag} W:${write.tag}" + if (note.isNotEmpty()) " ($note)" else ""
}

data class AuditReport(
    val deviceName: String,
    val deviceMac: String,
    val startedAt: Long,
    val finishedAt: Long,
    val bondedAtStart: Boolean,
    val bondChangedDuringAudit: Boolean,
    val mtu: Int,
    val options: AuditOptions,
    val results: List<CharAudit>,
) {
    val exposedCount get() = results.count { it.exposed }

    fun toText(): String = buildString {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        appendLine("DissPair GATT Access Audit")
        appendLine("Device : $deviceName ($deviceMac)")
        appendLine("Time   : ${fmt.format(Date(startedAt))} (${(finishedAt - startedAt) / 1000.0}s)")
        appendLine("Bonded : ${if (bondedAtStart) "YES - results do NOT reflect unauthenticated access" else "NO (unauthenticated)"}")
        if (bondChangedDuringAudit) appendLine("WARNING: bond state changed during audit (stack-initiated pairing). Re-run unbonded to confirm.")
        appendLine("MTU    : $mtu")
        appendLine("Tests  : read=${options.testRead} subscribe=${options.testSubscribe} echoWrite=${options.echoWrite}")
        appendLine("Summary: $exposedCount/${results.size} characteristics usable without pairing")
        appendLine()
        appendLine("Legend: OPEN=allowed unpaired, AUTH/ENC/AUTHZ/KEY=security required, DENY=not permitted, ERR=other error, --=not tested")
        results.groupBy { it.serviceUuid }.forEach { (svc, chars) ->
            appendLine()
            appendLine("Service $svc")
            chars.forEach { c ->
                appendLine("  ${if (c.exposed) "[!]" else "[ ]"} ${c.charUuid}  ${c.name}")
                appendLine("      props=${GattProps.describe(c.properties)}  ${c.shortLine()}")
                c.readStatus?.let { if (it != 0) appendLine("      read  -> ${GattStatus.name(it)}") }
                if (c.read == GattStatus.Access.OPEN) appendLine("      value -> ${c.readValue.toHex()}" + (c.readValue.printableOrNull()?.let { "  \"$it\"" } ?: ""))
                c.subscribeStatus?.let { if (it != 0) appendLine("      sub   -> ${GattStatus.name(it)}") }
                c.writeStatus?.let { if (it != 0) appendLine("      write -> ${GattStatus.name(it)}") }
            }
        }
    }
}

@SuppressLint("MissingPermission")
class GattAudit(
    private val ops: GattOps,
    private val device: BluetoothDevice,
    private val nameFor: (String) -> String,
    private val log: (String, String) -> Unit,
) {
    suspend fun run(
        services: List<BluetoothGattService>,
        options: AuditOptions,
        currentMtu: Int,
        onCharDone: (CharAudit) -> Unit,
    ): AuditReport {
        val started = System.currentTimeMillis()
        val bondedAtStart = device.bondState == BluetoothDevice.BOND_BONDED
        if (bondedAtStart) log("[!] Device is BONDED - audit shows paired access, not unauthenticated", "FFB000")
        val results = mutableListOf<CharAudit>()

        for (svc in services) {
            for (c in svc.characteristics) {
                val a = auditChar(svc, c, options)
                results += a
                onCharDone(a)
                log(
                    "[AUDIT] ${c.uuid.toString().take(8)} ${a.shortLine()}",
                    if (a.exposed) "FF2D55" else "39FF14"
                )
            }
        }

        val bondChanged = (device.bondState == BluetoothDevice.BOND_BONDED) != bondedAtStart
        return AuditReport(
            deviceName = device.name ?: "Unknown",
            deviceMac = device.address,
            startedAt = started,
            finishedAt = System.currentTimeMillis(),
            bondedAtStart = bondedAtStart,
            bondChangedDuringAudit = bondChanged,
            mtu = currentMtu,
            options = options,
            results = results,
        )
    }

    private suspend fun auditChar(
        svc: BluetoothGattService,
        c: BluetoothGattCharacteristic,
        o: AuditOptions,
    ): CharAudit {
        val p = c.properties
        var a = CharAudit(svc.uuid.toString(), c.uuid.toString(), nameFor(c.uuid.toString()), p)

        if (o.testRead && p and GattProps.READ != 0) {
            val r = ops.read(c)
            a = a.copy(read = GattStatus.classify(r.status), readStatus = r.status, readValue = r.value)
        }

        if (o.testSubscribe && p and (GattProps.NOTIFY or GattProps.INDICATE) != 0) {
            if (c.getDescriptor(GattOps.CCCD) == null) {
                a = a.copy(note = joinNote(a.note, "no CCCD"))
            } else {
                val r = ops.setSubscription(c, true)
                a = a.copy(subscribe = GattStatus.classify(r.status), subscribeStatus = r.status)
                if (r.ok) ops.setSubscription(c, false)
            }
        }

        val writable = p and (GattProps.WRITE or GattProps.WRITE_NO_RESPONSE) != 0
        if (o.echoWrite && writable) {
            val v = a.readValue
            if (a.read == GattStatus.Access.OPEN && v != null) {
                // Write-without-response gives no ATT response, so it can't reveal security
                // requirements; prefer write-with-response when the characteristic supports it.
                val type = if (p and GattProps.WRITE != 0) WRITE_TYPE_DEFAULT else WRITE_TYPE_NO_RESPONSE
                val r = ops.write(c, v, type)
                a = a.copy(write = GattStatus.classify(r.status), writeStatus = r.status)
                if (type == WRITE_TYPE_NO_RESPONSE) a = a.copy(note = joinNote(a.note, "WnR: accepted locally, unconfirmed"))
            } else {
                a = a.copy(note = joinNote(a.note, "write skipped: no safe value"))
            }
        }
        return a
    }

    private fun joinNote(a: String, b: String) = if (a.isEmpty()) b else "$a; $b"
}
