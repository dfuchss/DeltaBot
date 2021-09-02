package org.fuchss.deltabot.cognitive

import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.logger
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class DucklingService(private val endpoint: String) {

    fun interpretTime(text: String): List<LocalDateTime> {
        val om = createObjectMapper()
        val tz = ZoneId.systemDefault().id
        val locale = "en_GB"
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
            val rawResponse = post(postData)
            foundTimes = om.readValue(rawResponse, foundTimes.javaClass)

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            val time = foundTimes.map { t -> LocalDateTime.parse(t.value.value, df) }
            logger.debug("Found times: $time")
            time
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun post(postData: ByteArray): String {
        val url = URL("$endpoint/parse")
        val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
        urlConnection.requestMethod = "POST";
        urlConnection.useCaches = false
        urlConnection.doOutput = true

        urlConnection.setRequestProperty("content-type", "application/x-www-form-urlencoded")
        urlConnection.setRequestProperty("charset", "utf-8");
        urlConnection.setRequestProperty("Content-Length", postData.size.toString())

        DataOutputStream(urlConnection.outputStream).use { wr -> wr.write(postData) }

        val inputStream = urlConnection.inputStream
        val buffer = ByteArray(4096)
        val sb = StringBuilder()
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0)
                break

            if (read < buffer.size) {
                val s = String(buffer.copyOfRange(0, read), Charset.forName("utf-8"))
                sb.append(s)
            } else {
                val s = String(buffer, Charset.forName("utf-8"))
                sb.append(s)
            }
        }
        inputStream.close()
        return sb.toString()
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
        // E.g. 2021-09-01T10:00:00.000+02:00
        var value: String
    )

}