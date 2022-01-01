package org.fuchss.deltabot.cognitive

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.readKtValue

/**
 * The implementation of an interface to a RASA Multi NLU service at a certain [endpoint url][BotConfiguration.nluUrl].
 */
class RasaService(configuration: BotConfiguration) {

    private val endpoint = configuration.nluUrl
    private var version: String = ""

    /**
     * Recognize [Intents][IntentResult] and [Entities][EntityResult] from a text.
     * @param[content] the input text
     * @param[lang] the language key to use
     * @return the intents and entities
     */
    fun recognize(content: String, lang: String): Pair<List<IntentResult>, List<EntityResult>> {
        val empty = emptyList<IntentResult>() to emptyList<EntityResult>()

        if (version.isEmpty()) {
            this.init()
            if (version.isEmpty())
                return empty
        }

        val cleanContent = content.replace(Regex("[^a-zA-Z0-9ÄÖÜäöüß -]"), "")

        if (cleanContent.isBlank())
            return empty

        return try {
            val payload = "{ \"locale\": \"$lang\", \"text\": \"$cleanContent\" }"
            val dataString = post("$endpoint/nlu/", "application/json", payload)

            val data: RecognitionResult = createObjectMapper().readKtValue(dataString.toByteArray(), RecognitionResult())
            data.entities.forEach { e -> e.message = data.message }
            data.intents to data.entities
        } catch (e: Exception) {
            logger.error(e.message)
            empty
        }
    }

    private fun init() {
        try {
            val version = get("$endpoint/nlu/", 200)
            val status = "Hello from MultiNLU "
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