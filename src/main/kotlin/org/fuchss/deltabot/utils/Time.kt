package org.fuchss.deltabot.utils

import org.fuchss.deltabot.cognitive.DucklingService
import java.time.*
import java.util.*

// 'Regex' X 'Value in String' X 'Time Units'
private val genericTimeSpans = listOf(
    ("in \\d+ minute(s)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofMinutes(1)),
    ("in (one|a) minute" to { _: String -> 1 } to Duration.ofMinutes(1)),

    ("in \\d+ hour(s)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofHours(1)),
    ("in (one|a) hour" to { _: String -> 1 } to Duration.ofHours(1)),

    ("in \\d+ day(s)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofDays(1)),
    ("in (one|a) day" to { _: String -> 1 } to Duration.ofDays(1)),

    ("in \\d+ week(s)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofDays(7)),
    ("in (one|a) week" to { _: String -> 1 } to Duration.ofDays(7)),

    ("next weekend" to { _: String -> nextWeekend() } to Duration.ofDays(1)),
    ("today" to { _: String -> 0 } to Duration.ofDays(1)),
    ("tomorrow" to { _: String -> 1 } to Duration.ofDays(1))
)

private fun regex(data: Pair<Pair<String, (String) -> Int>, Duration>): String = data.first.first
private fun extractor(data: Pair<Pair<String, (String) -> Int>, Duration>): (String) -> Int = data.first.second
private fun unit(data: Pair<Pair<String, (String) -> Int>, Duration>): Duration = data.second

private fun nextWeekend(): Int {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val diffToSaturday = Calendar.SATURDAY - dayOfWeek
    return if (diffToSaturday == 0) 7 else diffToSaturday
}

fun findGenericDayTimespan(message: String, ducklingService: DucklingService? = null): Pair<Duration, Pair<IntRange, String>>? {
    for (timespan in genericTimeSpans) {
        // Only consider timespans >= 1 day
        if (unit(timespan) < Duration.ofDays(1)) {
            continue
        }
        val rgx = Regex(regex(timespan))
        val match = rgx.findAll(message).toList()

        if (match.size != 1)
            continue

        val multiply = extractor(timespan)(match[0].value)
        val days = unit(timespan).multipliedBy(multiply.toLong())
        return days to (match[0].range to daysText(days))
    }

    if (ducklingService != null) {
        val times = ducklingService.interpretTime(message)
        if (times.size != 1)
            return null

        val (time, range) = times[0]
        val days = Duration.between(LocalDateTime.now(), time).abs()
        return days to (range to daysText(days))
    }

    return null
}

fun daysText(days: Duration): String {
    return when (days.toDays()) {
        1L -> "tomorrow"
        0L -> "today"
        else -> "in ${days.toDays()} days"
    }
}

fun nextDayTS(days: Long = 1): Long {
    val tomorrow = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT).plusDays(days)
    // For Debugging ..
    // val tomorrow = LocalDateTime.of(LocalDate.now(), LocalTime.now()).plusSeconds(5)
    return tomorrow.timestamp()
}

fun LocalDateTime.timestamp(): Long = this.atZone(ZoneId.systemDefault()).toEpochSecond()