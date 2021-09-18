package org.fuchss.deltabot.utils

import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.DucklingService
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

enum class Weekday {
    Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
}

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

fun findGenericTimespan(message: String, language: Language, ducklingService: DucklingService? = null): LocalDateTime? {
    if (message.isBlank())
        return null

    if (ducklingService != null) {
        val times = ducklingService.interpretTime(message, language)
        if (times.size == 1) {
            return times[0]
        }
    }

    for (timespan in genericTimeSpans[language] ?: listOf()) {
        val rgx = Regex(regex(timespan))
        val match = rgx.findAll(message).toList()

        if (match.size != 1)
            continue

        val multiply = extractor(timespan)(match[0].value)
        val time = LocalDateTime.now() + unit(timespan).multipliedBy(multiply.toLong())
        logger.debug("Found time $time")
        return time
    }

    return null
}

fun LocalDateTime.timestamp(): Long = this.atZone(ZoneId.systemDefault()).toEpochSecond()