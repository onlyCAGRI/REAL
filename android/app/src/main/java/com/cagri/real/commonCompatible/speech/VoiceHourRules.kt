package com.cagri.real.speech

enum class TimeOfDay { Morning, Noon, Evening, Night }

fun interpretHour(hour: Int, timeOfDay: TimeOfDay? = null): Int {
    if (timeOfDay != null) {
        return when (timeOfDay) {
            TimeOfDay.Morning -> if (hour == 12) 0 else hour
            TimeOfDay.Noon -> if (hour in 1..6) hour + 12 else hour
            TimeOfDay.Evening -> if (hour in 1..11) hour + 12 else hour
            TimeOfDay.Night -> when {
                hour in 8..11 -> hour + 12
                hour == 12 -> 0
                else -> hour
            }
        }
    }
    return when (hour) {
        in 1..7 -> hour + 12
        in 8..11 -> hour
        12 -> 12
        else -> hour
    }
}

