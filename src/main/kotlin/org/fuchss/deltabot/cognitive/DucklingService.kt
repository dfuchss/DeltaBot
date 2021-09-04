package org.fuchss.deltabot.cognitive

import org.fuchss.deltabot.Language
import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class DucklingService(private val endpoint: String) {

    fun interpretTime(text: String, language: Language): List<Pair<LocalDateTime, IntRange>> {
        val om = createObjectMapper()
        val tz = ZoneId.systemDefault().id
        val locale = language.locale
        val dims = "[\"time\"]"

        val values = mapOf(
            "tz" to tz,
            "locale" to locale,
            "text" to text,
            "dims" to dims
        )

        var foundTimes: Array<DucklingResponseData> = emptyArray()
        return try {
            val postData = getDataString(values).toByteArray(StandardCharsets.UTF_8)
            val rawResponse = post("$endpoint/parse", "application/x-www-form-urlencoded", postData)
            foundTimes = om.readValue(rawResponse, foundTimes.javaClass)

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            val times = foundTimes.map { t -> extractTime(t.value) to IntRange(t.start, t.end - 1) }.map { (t, r) -> LocalDateTime.parse(t, df) to r }
            logger.debug("Found times: $times")
            times
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractTime(t: DucklingResponseValue): String {
        return if (t.value == null) {
            // From & To are set ..
            t.from!!.value!!
        } else
            t.value!!
    }

    private fun getDataString(params: Map<String, Any>): String {
        val result = StringBuilder()
        var first = true
        for ((key, value) in params) {
            if (first) first = false else result.append("&")
            result.append(URLEncoder.encode(key, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode("$value", "UTF-8"))
        }
        return result.toString()
    }

    private data class DucklingResponseData(
        var body: String,
        var start: Int,
        var end: Int,
        var dim: String,
        var value: DucklingResponseValue
    )

    private data class DucklingResponseValue(
        var grain: String? = null,
        // E.g. 2021-09-01T10:00:00.000+02:00
        var value: String? = null,
        var from: DucklingResponseValue? = null,
        var to: DucklingResponseValue? = null
    )

}