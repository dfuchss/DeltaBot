package org.fuchss.deltabot.cognitive

import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


fun get(url: String): String {
    val endpoint = URL(url)
    val urlConnection: HttpURLConnection = endpoint.openConnection() as HttpURLConnection
    urlConnection.requestMethod = "GET";
    urlConnection.useCaches = false

    urlConnection.setRequestProperty("charset", "utf-8");

    return readInputStream(urlConnection.inputStream)
}

fun post(url: String, contentType: String, postData: String) = post(url, contentType, postData.toByteArray(StandardCharsets.UTF_8))

fun post(url: String, contentType: String, postData: ByteArray): String {
    val endpoint = URL(url)
    val urlConnection: HttpURLConnection = endpoint.openConnection() as HttpURLConnection
    urlConnection.requestMethod = "POST";
    urlConnection.useCaches = false
    urlConnection.doOutput = true

    urlConnection.setRequestProperty("content-type", contentType)
    urlConnection.setRequestProperty("charset", "utf-8");
    urlConnection.setRequestProperty("Content-Length", postData.size.toString())

    DataOutputStream(urlConnection.outputStream).use { wr -> wr.write(postData) }

    return readInputStream(urlConnection.inputStream)
}

private fun readInputStream(inputStream: InputStream): String {
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