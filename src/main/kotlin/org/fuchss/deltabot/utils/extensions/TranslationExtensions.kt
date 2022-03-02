package org.fuchss.deltabot.utils.extensions

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.fuchss.deltabot.Language

private val translations = mutableMapOf<Language, MutableMap<String, String>>()

/**
 * Translate a string based the language retrieved for the [GenericInteractionCreateEvent].
 */
fun String.translate(event: GenericInteractionCreateEvent, vararg attributes: Any) = this.translate(event.language(), *attributes)

/**
 * Translate a string based the language retrieved for the [MessageReceivedEvent].
 */
fun String.translate(event: MessageReceivedEvent, vararg attributes: Any) = this.translate(event.language(), *attributes)

/**
 * Translate a string based on a [Language] and replace the "#" with the [attributes].
 */
fun String.translate(language: Language?, vararg attributes: Any): String {
    val lang = language ?: languageSettings().defaultLanguage()
    var text = this
    if (lang != Language.ENGLISH) {
        if (translations[lang] == null) {
            var data: MutableMap<String, String> = mutableMapOf()
            val file = "/translations/${lang.locale}.json"
            val loader = object : Any() {}
            val rawData = loader.javaClass.getResourceAsStream(file)
            data = if (rawData != null) createObjectMapper().readValue(rawData, data.javaClass) else mutableMapOf()
            translations[lang] = data
        }
        text = translations[lang]?.get(this) ?: ""
        if (text.isBlank()) {
            text = "[Translate to $lang]: $this"
            logger.error("Missing Translation: $text")
        }
    }

    for (attr in attributes) {
        text = text.replaceFirst("#", attr.toString())
    }

    return text
}
