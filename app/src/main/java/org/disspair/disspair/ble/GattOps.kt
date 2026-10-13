package org.disspair.disspair.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class GattResult(val status: Int, val value: ByteArray? = null, val mtu: Int = 0) {
    val ok get() = status == 0
    val statusName get() = GattStatus.name(status)
}

/**
 * Serialises GATT operations.
 *
 * Android allows only one outstanding GATT operation per connection; issuing a second
 * one before the first callback arrives makes the API return false (or BUSY) and the
 * operation is silently dropped. Every operation here takes a mutex, starts the request,
 * then suspends until the matching callback calls [complete] or the timeout fires.
 *
 * The BluetoothGattCallback must forward results via [complete] using the same key
 * scheme as the request methods (see [key]).
 */
@SuppressLint("MissingPermission")
class GattOps(private val timeoutMs: Long = 6000) {
    @Volatile var gatt: BluetoothGatt? = null

    private val mutex = Mutex()
    @Volatile private var pendingKey: String? = null
    @Volatile private var pending: CompletableDeferred<GattResult>? = null

    companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        fun key(kind: String, uuid: UUID?) = "$kind:${uuid ?: ""}"
        const val READ = "R"
        const val WRITE = "W"
        const val DESC_WRITE = "DW"
        const val DESC_READ = "DR"
        const val MTU = "MTU"
    }

    /** Called from BluetoothGattCallback. Results for a key nobody is waiting on are dropped. */
    fun complete(key: String, result: GattResult) {
        if (key == pendingKey) pending?.complete(result)
    }

    /** Abort whatever is in flight (used on disconnect / reset). */
    fun cancelPending() {
        pending?.complete(GattResult(GattStatus.LOCAL_REJECTED))
    }

    private suspend fun run(key: String, start: (BluetoothGatt) -> Boolean): GattResult = mutex.withLock {
        val g = gatt ?: return@withLock GattResult(GattStatus.LOCAL_REJECTED)
        val d = CompletableDeferred<GattResult>()
        pendingKey = key
        pending = d
        val started = try { start(g) } catch (e: SecurityException) { false } catch (e: Exception) { false }
        val result = if (!started) {
            GattResult(GattStatus.LOCAL_REJECTED)
        } else {
            withTimeoutOrNull(timeoutMs) { d.await() } ?: GattResult(GattStatus.LOCAL_TIMEOUT)
        }
        pendingKey = null
        pending = null
        result
    }

    suspend fun read(c: BluetoothGattCharacteristic): GattResult =
        run(key(READ, c.uuid)) { it.readCharacteristic(c) }

    /**
     * @param writeType BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT (with response)
     *                  or WRITE_TYPE_NO_RESPONSE.
     */
    @Suppress("DEPRECATION")
    suspend fun write(c: BluetoothGattCharacteristic, bytes: ByteArray, writeType: Int): GattResult =
        run(key(WRITE, c.uuid)) { g ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, bytes, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                c.writeType = writeType
                c.value = bytes
                g.writeCharacteristic(c)
            }
        }

    @Suppress("DEPRECATION")
    suspend fun writeDescriptor(d: BluetoothGattDescriptor, bytes: ByteArray): GattResult =
        run(key(DESC_WRITE, d.uuid)) { g ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(d, bytes) == BluetoothStatusCodes.SUCCESS
            } else {
                d.value = bytes
                g.writeDescriptor(d)
            }
        }

    suspend fun readDescriptor(d: BluetoothGattDescriptor): GattResult =
        run(key(DESC_READ, d.uuid)) { it.readDescriptor(d) }

    suspend fun requestMtu(mtu: Int): GattResult = run(key(MTU, null)) { it.requestMtu(mtu) }

    /**
     * Enable / disable notifications or indications.
     * Prefers notifications when both are supported. Returns the CCCD write result,
     * which is where a peripheral enforces security on subscriptions.
     */
    suspend fun setSubscription(c: BluetoothGattCharacteristic, enable: Boolean): GattResult {
        val g = gatt ?: return GattResult(GattStatus.LOCAL_REJECTED)
        val cccd = c.getDescriptor(CCCD) ?: return GattResult(GattStatus.LOCAL_REJECTED)
        val local = try { g.setCharacteristicNotification(c, enable) } catch (e: SecurityException) { false }
        if (!local) return GattResult(GattStatus.LOCAL_REJECTED)
        val value = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            c.properties and GattProps.NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        return writeDescriptor(cccd, value)
    }
}
