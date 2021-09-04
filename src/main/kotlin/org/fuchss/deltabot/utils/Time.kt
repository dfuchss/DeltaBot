package org.fuchss.deltabot.utils

import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.translate
import java.time.*
import java.util.*

// 'Regex' X 'Value in String' X 'Time Units'
private val genericTimeSpansEN = listOf(
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

private val genericTimeSpansDE = listOf(
    ("in \\d+ minute(n)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofMinutes(1)),
    ("in einer minute" to { _: String -> 1 } to Duration.ofMinutes(1)),

    ("in \\d+ stunde(n)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofHours(1)),
    ("in einer stunde" to { _: String -> 1 } to Duration.ofHours(1)),

    ("in \\d+ tag(en)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofDays(1)),
    ("in einem tag" to { _: String -> 1 } to Duration.ofDays(1)),

    ("in \\d+ woche(n)?" to { s: String -> s.split(" ")[1].toInt() } to Duration.ofDays(7)),
    ("in einer woche" to { _: String -> 1 } to Duration.ofDays(7)),

    ("nächstes Wochende" to { _: String -> nextWeekend() } to Duration.ofDays(1)),
    ("heute" to { _: String -> 0 } to Duration.ofDays(1)),
    ("morgen" to { _: String -> 1 } to Duration.ofDays(1)),
    ("übermorgen" to { _: String -> 1 } to Duration.ofDays(2))
)

private val genericTimeSpans = mapOf(Language.ENGLISH to genericTimeSpansEN, Language.DEUTSCH to genericTimeSpansDE)

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

fun findGenericTimespan(message: String, language: Language, ducklingService: DucklingService? = null): Pair<LocalDateTime, IntRange>? {
    if (ducklingService != null) {
        val times = ducklingService.interpretTime(message, language)
        if (times.size != 1)
            return null

        val (time, range) = times[0]
        return time to range
    }

    for (timespan in genericTimeSpans[language] ?: listOf()) {
        // Only consider timespans >= 1 day
        val rgx = Regex(regex(timespan))
        val match = rgx.findAll(message).toList()

        if (match.size != 1)
            continue

        val multiply = extractor(timespan)(match[0].value)
        val time = LocalDateTime.now() + unit(timespan).multipliedBy(multiply.toLong())
        return time to match[0].range
    }
    
    return null
}

fun daysText(days: Duration, language: Language): String {
    return when (days.toDays()) {
        2L -> "in 3 days".translate(language)
        1L -> "tomorrow".translate(language)
        0L -> "today".translate(language)
        else -> "in # days".translate(language, days.toDays())
    }
}

fun nextDayTS(days: Long = 1): Long {
    val tomorrow = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT).plusDays(days)
    // For Debugging ..
    // val tomorrow = LocalDateTime.of(LocalDate.now(), LocalTime.now()).plusSeconds(5)
    return tomorrow.timestamp()
}

fun LocalDateTime.timestamp(): Long = this.atZone(ZoneId.systemDefault()).toEpochSecond()