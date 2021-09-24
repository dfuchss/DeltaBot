package org.fuchss.deltabot.cognitive

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.logger

class RasaService(configuration: Configuration) {

    private val endpoint = configuration.nluUrl
    private var version: String = ""

    fun recognize(content: String): Pair<List<IntentResult>, List<EntityResult>> {
        val empty = emptyList<IntentResult>() to emptyList<EntityResult>()

        if (version.isEmpty()) {
            this.init()
            if (version.isEmpty())
                return empty
        }

        val cleanContent = content.replace(Regex("[^a-zA-Z0-9ÄÖÜäöüß -]"), "")

        if (cleanContent.isBlank())
            return empty

        val orm = createObjectMapper()

        try {
            val payload = "{ \"text\": \"$cleanContent\" }"
            val dataString = post("$endpoint/model/parse", "application/json", payload)

            val data: RecognitionResult = orm.readValue(dataString.toByteArray(), RecognitionResult().javaClass)
            data.entities.forEach { e -> e.message = data.message }
            return data.intents to data.entities
        } catch (e: Exception) {
            logger.error(e.message)
            return empty
        }
    }

    private fun init() {
        try {
            val version = get(endpoint)
            val status = "Hello from Rasa: "
            if (version.startsWith(status)) {
                this.version = version.substring(status.length)
            }

        } catch (e: Exception) {
            logger.error(e.message)
        }
    }


    private data class RecognitionResult(
        @JsonProperty("intent_ranking")
        var intents: MutableList<IntentResult> = mutableListOf(),
        @JsonProperty("entities")
        var entities: MutableList<EntityResult> = mutableListOf(),
        @JsonProperty("text")
        var message: String? = null
    )

    data class IntentResult(
        @JsonProperty("name")
        var name: String,
        @JsonProperty("confidence")
        var score: Double
    )

    data class EntityResult(
        @JsonProperty("value")
        var name: String,
        @JsonProperty("entity")
        var group: String,
        @JsonProperty("start")
        var start: Int = 0,
        @JsonProperty("end")
        var end: Int = 0,
        @JsonIgnore
        var message: String? = null
    ) {
        val value: String? get() = message?.substring(start, end)
    }
}