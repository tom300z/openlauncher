package com.openlauncher.app.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

data class DailyVoltageStats(val epochDay: Long, val minimum: Float, val maximum: Float)

private const val DAY_MS = 86_400_000L

fun currentLocalEpochDay(now: Long = System.currentTimeMillis()): Long =
    Math.floorDiv(now + TimeZone.getDefault().getOffset(now), DAY_MS)

data class BatteryMonitorSnapshot(
    val voltage: Float? = null,
    val connected: Boolean = false,
    val history: List<DailyVoltageStats> = emptyList(),
    val lastChargingAtMillis: Long = 0L
)

/**
 * Reads the custom F0/43 MCU battery endpoint through the stock Jancar CarService.
 * One request is made per second. Only the highest response in each completed
 * six-second window is published and recorded, suppressing starter/load dips.
 */
class BatteryVoltageMonitor(private val context: Context) {
    companion object {
        private const val TAG = "BatteryVoltage"
        private const val CAR_DESCRIPTOR = "com.jancar.services.car.ICar"
        private const val CALLBACK_DESCRIPTOR = "com.jancar.services.car.IPassthroughDataCallback"
        private const val CAR_SERVICE_PACKAGE = "com.jancar.services"
        private const val CAR_SERVICE_CLASS = "com.jancar.services.car.CarService"
        private const val REGISTER_CALLBACK = 7
        private const val UNREGISTER_CALLBACK = 8
        private const val SEND_PASSTHROUGH = 20
        private const val INTERFACE_TRANSACTION = 1598968902
        private const val WINDOW_MS = 6_000L
        private const val SAMPLE_MS = 1_000L
        const val CHARGING_THRESHOLD_VOLTS = 13.0f
        const val SETTLING_DURATION_MS = 15 * 60 * 1_000L

        // Derived from the MCU's existing voltage thresholds. Calibrate this
        // constant against a simultaneous multimeter reading for best accuracy.
        const val ADC_VOLTS_PER_COUNT = 0.145f
    }

    private val preferences = context.getSharedPreferences("battery_voltage_history", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _snapshot = MutableStateFlow(
        BatteryMonitorSnapshot(
            history = loadHistory(),
            lastChargingAtMillis = preferences.getLong("last_charging_at", 0L).takeIf { it > 0L }
                ?: preferences.getLong("settling_until", 0L)
                    .takeIf { it > 0L }?.minus(SETTLING_DURATION_MS) ?: 0L
        )
    )
    val snapshot: StateFlow<BatteryMonitorSnapshot> = _snapshot

    private var service: IBinder? = null
    private var callbackRegistered = false
    private var binding = false
    private var bound = false
    private var samplingJob: Job? = null
    private var windowStartedAt = 0L
    private var windowPeak: Float? = null
    private var windowHistoryEligible = true
    private var lastPersistedChargingAt = _snapshot.value.lastChargingAtMillis

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            if (code == 1) {
                data.enforceInterface(CALLBACK_DESCRIPTOR)
                val payload = data.createByteArray()
                val length = data.readInt()
                reply?.writeNoException()
                if (payload != null && length > 0 && payload.isNotEmpty()) {
                    acceptSample((payload[0].toInt() and 0xff) * ADC_VOLTS_PER_COUNT)
                }
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = binder
            binding = false
            bound = true
            callbackRegistered = registerCallback(binder, REGISTER_CALLBACK, callback)
            _snapshot.value = _snapshot.value.copy(connected = callbackRegistered)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            callbackRegistered = false
            binding = false
            bound = false
            _snapshot.value = _snapshot.value.copy(connected = false)
        }
    }

    fun start(scope: CoroutineScope) {
        if (samplingJob != null) return
        samplingJob = scope.launch {
            while (isActive) {
                ensureBound()
                val binder = service
                if (callbackRegistered && binder != null) {
                    withContext(Dispatchers.IO) { sendQuery(binder) }
                }
                delay(SAMPLE_MS)
            }
        }
    }

    fun stop() {
        samplingJob?.cancel()
        samplingJob = null
        persistChargingTimestamp(_snapshot.value.lastChargingAtMillis, force = true)
        service?.let { binder ->
            if (callbackRegistered) registerCallback(binder, UNREGISTER_CALLBACK, callback)
        }
        callbackRegistered = false
        service = null
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        binding = false
    }

    fun clearHistory() {
        synchronized(lock) {
            preferences.edit().remove("daily").apply()
            _snapshot.value = _snapshot.value.copy(history = emptyList())
        }
    }

    private fun ensureBound() {
        if (bound || binding) return
        binding = true
        val intent = Intent().setClassName(CAR_SERVICE_PACKAGE, CAR_SERVICE_CLASS)
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .getOrDefault(false)
        if (!bound) binding = false
    }

    private fun registerCallback(binder: IBinder, transaction: Int, callback: IBinder): Boolean {
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        return try {
            request.writeInterfaceToken(CAR_DESCRIPTOR)
            request.writeStrongBinder(callback)
            binder.transact(transaction, request, response, 0)
            response.readException()
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Callback transaction $transaction failed", error)
            false
        } finally {
            response.recycle()
            request.recycle()
        }
    }

    private fun sendQuery(binder: IBinder) {
        val request = Parcel.obtain()
        val response = Parcel.obtain()
        try {
            request.writeInterfaceToken(CAR_DESCRIPTOR)
            request.writeByte(0xf0.toByte())
            request.writeByteArray(byteArrayOf(0x43, 0x00))
            binder.transact(SEND_PASSTHROUGH, request, response, 0)
            response.readException()
        } catch (error: RemoteException) {
            Log.w(TAG, "Battery query failed", error)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Battery query rejected", error)
        } finally {
            response.recycle()
            request.recycle()
        }
    }

    private fun acceptSample(voltage: Float) {
        synchronized(lock) {
            val wallNow = System.currentTimeMillis()
            val isCharging = voltage > CHARGING_THRESHOLD_VOLTS
            val wasCharging = _snapshot.value.voltage?.let { it > CHARGING_THRESHOLD_VOLTS } == true
            val lastChargingAt = if (isCharging) wallNow else _snapshot.value.lastChargingAtMillis
            if (isCharging) persistChargingTimestamp(lastChargingAt)
            if (wasCharging && !isCharging) persistChargingTimestamp(lastChargingAt, force = true)

            // Live UI state follows every MCU response (normally once per
            // second). History remains isolated behind the six-second peak
            // accumulator below.
            _snapshot.value = _snapshot.value.copy(
                voltage = voltage,
                connected = true,
                lastChargingAtMillis = lastChargingAt
            )
            val sampleHistoryEligible = !isCharging &&
                (lastChargingAt == 0L || wallNow >= lastChargingAt + SETTLING_DURATION_MS)
            val now = SystemClock.elapsedRealtime()
            if (windowStartedAt == 0L) {
                windowStartedAt = now
                windowPeak = voltage
                windowHistoryEligible = sampleHistoryEligible
                return
            }
            if (now - windowStartedAt >= WINDOW_MS) {
                windowPeak?.let { recordWindowPeak(it, windowHistoryEligible) }
                windowStartedAt = now
                windowPeak = voltage
                windowHistoryEligible = sampleHistoryEligible
            } else {
                windowPeak = windowPeak?.let { max(it, voltage) } ?: voltage
                windowHistoryEligible = windowHistoryEligible && sampleHistoryEligible
            }
        }
    }

    private fun recordWindowPeak(voltage: Float, historyEligible: Boolean) {
        // The peak represents every sample in this six-second window. If it is
        // above 13 V, the alternator/charger was active at least once, so expose
        // it as the live value but keep the whole window out of parked-battery
        // high/low history.
        if (!historyEligible || voltage > CHARGING_THRESHOLD_VOLTS) {
            return
        }
        val today = currentLocalEpochDay()
        val retained = _snapshot.value.history.filter { it.epochDay >= today - 369 }.toMutableList()
        val index = retained.indexOfFirst { it.epochDay == today }
        if (index >= 0) {
            val old = retained[index]
            retained[index] = old.copy(
                minimum = min(old.minimum, voltage),
                maximum = max(old.maximum, voltage)
            )
        } else {
            retained += DailyVoltageStats(today, voltage, voltage)
        }
        val sorted = retained.sortedBy { it.epochDay }
        saveHistory(sorted)
        _snapshot.value = _snapshot.value.copy(connected = true, history = sorted)
    }

    private fun loadHistory(): List<DailyVoltageStats> =
        preferences.getString("daily", "").orEmpty().split(';').mapNotNull { row ->
            val values = row.split(',')
            if (values.size != 3) null else runCatching {
                DailyVoltageStats(values[0].toLong(), values[1].toFloat(), values[2].toFloat())
            }.getOrNull()
        }.sortedBy { it.epochDay }

    private fun saveHistory(history: List<DailyVoltageStats>) {
        val encoded = history.joinToString(";") { "${it.epochDay},${it.minimum},${it.maximum}" }
        preferences.edit().putString("daily", encoded).apply()
    }

    private fun persistChargingTimestamp(timestamp: Long, force: Boolean = false) {
        // Avoid writing shared preferences every second throughout a long drive.
        // One-minute checkpoints plus stop() retain the absolute timestamp accurately
        // enough across an unexpected process restart without excessive writes.
        if (timestamp <= 0L) return
        if (!force && timestamp - lastPersistedChargingAt < 60_000L) return
        preferences.edit().putLong("last_charging_at", timestamp).apply()
        lastPersistedChargingAt = timestamp
    }
}
