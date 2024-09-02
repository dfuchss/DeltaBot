package org.fuchss.deltabot.cognitive

import org.fuchss.deltabot.Language
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.readKtValue
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.StringJoiner

/**
 * The implementation of an interface to a Duckling service at a certain [endpoint url][endpoint].
 */
class DucklingService(
    private val endpoint: String
) {
    fun interpretTime(
        text: String,
        language: Language
    ): List<LocalDateTime> {
        val tz = ZoneId.systemDefault().id
        val locale = language.locale
        val dims = "[\"time\"]"

        val payload =
            mapOf(
                "tz" to tz,
                "locale" to locale,
                "text" to text,
                "dims" to dims
            )

        val postData = getDataString(payload).toByteArray(StandardCharsets.UTF_8)

        return try {
            val rawResponse = post("$endpoint/parse", "application/x-www-form-urlencoded", postData)
            val foundTimes: Array<DucklingResponseData> = createObjectMapper().readKtValue(rawResponse, emptyArray())

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            val times = foundTimes.map { t -> extractTime(t.value) }.map { t -> LocalDateTime.parse(t, df) }
            logger.debug("Found times: $times")
            times
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractTime(t: DucklingResponseValue): String =
        if (t.value == null) {
            // From & To are set ..
            t.from!!.value!!
        } else {
            t.value!!
        }

    private fun getDataString(params: Map<String, Any>): String {
        val result = StringJoiner("&")
        for ((key, value) in params) {
            val urlKey = URLEncoder.encode(key, "UTF-8")
            val urlValue = URLEncoder.encode("$value", "UTF-8")
            result.add("$urlKey=$urlValue")
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
