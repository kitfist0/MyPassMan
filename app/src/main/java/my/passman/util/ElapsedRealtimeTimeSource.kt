package my.passman.util

import android.os.SystemClock
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

/**
 * A [kotlin.time.TimeSource] backed by [SystemClock.elapsedRealtime]: monotonic, so
 * changing the wall clock cannot skew it, and unlike [System.nanoTime] (which
 * `TimeSource.Monotonic` uses) it keeps counting while the device is in deep sleep.
 */
object ElapsedRealtimeTimeSource : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
    override fun read(): Long = SystemClock.elapsedRealtime()
}
