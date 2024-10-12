package org.fuchss.deltabot.utils

import java.time.LocalDateTime
import java.time.ZoneId

enum class Weekday {
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday
}

fun LocalDateTime.timestamp(): Long = this.atZone(ZoneId.systemDefault()).toEpochSecond()
