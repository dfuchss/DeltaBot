package org.fuchss.deltabot

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import org.fuchss.deltabot.utils.Storable
import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.load
import org.fuchss.deltabot.utils.logger


enum class Language(val locale: String) {
    ENGLISH("en_GB"), DEUTSCH("de_DE");

    override fun toString(): String {
        val name = super.toString()
        return name[0] + name.lowercase().substring(1)
    }
}

fun User.language() = languageSettings.userToLanguage[this.id] ?: languageSettings.defaultLanguage

fun User.setLanguage(language: Language?) {
    if (language == null)
        languageSettings.userToLanguage.remove(this.id)
    else
        languageSettings.userToLanguage[this.id] = language

    languageSettings.store()
}

private val languageSettings = LanguageSettings().load("./states/languages.json")

private data class LanguageSettings(
    var userToLanguage: MutableMap<String, Language> = mutableMapOf(),
    var defaultLanguage: Language = Language.ENGLISH
) : Storable()

private val translations = mutableMapOf<Language, MutableMap<String, String>>()

fun String.translate(event: GenericInteractionCreateEvent, vararg attributes: Any): String = this.translate(event.user, *attributes)
fun String.translate(user: User, vararg attributes: Any): String = this.translate(user.language(), *attributes)

fun String.translate(language: Language, vararg attributes: Any): String {
    var text = this
    if (language != Language.ENGLISH) {
        if (translations[language] == null) {
            var data: MutableMap<String, String> = mutableMapOf()
            val file = "/translations/${language.locale}.json"
            val loader = object : Any() {}
            val rawData = loader.javaClass.getResourceAsStream(file)
            data = if (rawData != null) createObjectMapper().readValue(rawData, data.javaClass) else mutableMapOf()
            translations[language] = data
        }
        text = translations[language]?.get(this) ?: ""
        if (text.isBlank()) {
            text = "[Translate to $language]: $this"
            logger.error("Missing Translation: $text")
        }
    }

    for (attr in attributes) {
        text = text.replaceFirst("#", attr.toString())
    }

    return text
}