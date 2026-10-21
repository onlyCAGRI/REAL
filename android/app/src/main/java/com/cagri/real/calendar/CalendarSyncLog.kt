package com.cagri.real.calendar

import android.util.Log

/** Teşhis günlüğü: `adb logcat -s RealCalSync`. Eşitleme gerçekten döngüye giriyorsa satırlar saniyede bir tekrar eder. */
internal fun calendarSyncLog(message: String) {
    Log.d("RealCalSync", message)
}
