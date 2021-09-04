package org.fuchss.deltabot

import net.dv8tion.jda.api.entities.Guild
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

fun User.language() = languageSettings.userToLanguage[this.id]

fun User.setLanguage(language: Language?) {
    if (language == null)
        languageSettings.userToLanguage.remove(this.id)
    else
        languageSettings.userToLanguage[this.id] = language

    languageSettings.store()
}

fun Guild.language() = languageSettings.guildToLanguage[this.id]

fun Guild.setLanguage(language: Language?) {
    if (language == null)
        languageSettings.guildToLanguage.remove(this.id)
    else
        languageSettings.guildToLanguage[this.id] = language

    languageSettings.store()
}


private val languageSettings = LanguageSettings().load("./states/languages.json")

private data class LanguageSettings(
    var guildToLanguage: MutableMap<String, Language> = mutableMapOf(),
    var userToLanguage: MutableMap<String, Language> = mutableMapOf(),
    var defaultLanguage: Language = Language.ENGLISH
) : Storable()

private val translations = mutableMapOf<Language, MutableMap<String, String>>()

fun GenericInteractionCreateEvent.language(): Language = language(guild, user)

fun language(guild: Guild?, user: User?): Language {
    val guildLanguage = guild?.language()
    if (guildLanguage != null)
        return guildLanguage

    val userLanguage = user?.language()
    if (userLanguage != null)
        return userLanguage

    return languageSettings.defaultLanguage
}

fun String.translate(event: GenericInteractionCreateEvent, vararg attributes: Any) = this.translate(event.language(), *attributes)

fun String.translate(language: Language?, vararg attributes: Any): String {
    val lang = language ?: languageSettings.defaultLanguage
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