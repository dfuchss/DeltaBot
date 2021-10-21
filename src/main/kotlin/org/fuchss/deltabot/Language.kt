package org.fuchss.deltabot

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.fuchss.deltabot.db.load
import org.fuchss.deltabot.db.settings.LanguageSettings
import org.fuchss.deltabot.utils.*
import org.fuchss.objectcasket.port.Session

/**
 * Definition of all supported languages with their locales.
 */
enum class Language(val locale: String) {
    ENGLISH("en_GB"), DEUTSCH("de_DE");

    override fun toString(): String {
        val name = super.toString()
        return name[0] + name.lowercase().substring(1)
    }
}

/**
 * Calculate the [Language] based on the current [Guild] and current [User].
 */
fun language(guild: Guild?, user: User?): Language {
    if (guild != null) {
        val usersGuildLanguage = user?.internalLanguage(guild)
        if (usersGuildLanguage != null)
            return usersGuildLanguage

        val guildLanguage = guild.internalLanguage()
        if (guildLanguage != null)
            return guildLanguage
    }

    val userLanguage = user?.internalLanguage()
    if (userLanguage != null)
        return userLanguage

    return languageSettings.defaultLanguage()
}

/**
 * Read the global [Language] of a [User].
 */
fun User.internalLanguage() = languageSettings.userToLanguage(this.id)

/**
 * Read the guild override of a [Language] of a [User].
 */
fun User.internalLanguage(guild: Guild) = languageSettings.userAndGuildToLanguage(this.id, guild.id)

/**
 * Set the global [Language] for a [User].
 */
fun User.setLanguage(language: Language?) {
    if (language == null)
        languageSettings.removeUserToLanguage(this.id)
    else
        languageSettings.setUserToLanguage(this, language)
}

/**
 * Set the [Language] in a specific [Guild] for a [User].
 */
fun User.setLanguage(language: Language?, guild: Guild) {
    if (language == null)
        languageSettings.removeUserAndGuildToLanguage(this.id, guild.id)
    else
        languageSettings.setUserAndGuildToLanguage(this, guild, language)
}

/**
 * Get the global [Guild] language iff set.
 */
fun Guild.internalLanguage() = languageSettings.guildToLanguage(this.id)

/**
 * Set the global [Guild] language.
 */
fun Guild.setLanguage(language: Language?) {
    if (language == null)
        languageSettings.removeGuildToLanguage(this.id)
    else
        languageSettings.setGuildToLanguage(this, language)
}


private var languageSettings: LanguageSettings = LanguageSettings()

fun initLanguage(session: Session) {
    languageSettings = session.load(LanguageSettings::class.java, initializer = { l, s -> l.setSession(s) }, defaultValue = { LanguageSettings() })
}


private val translations = mutableMapOf<Language, MutableMap<String, String>>()

/**
 * Calculate the language based on a [GenericInteractionCreateEvent].
 */
fun GenericInteractionCreateEvent.language(): Language = language(guild, user)

/**
 * Calculate the language based on a [MessageReceivedEvent].
 */
fun MessageReceivedEvent.language(): Language = language(message.optionalGuild(), message.author)

/**
 * Calculate the language based on a [Message] of a [User].
 */
fun Message.language(): Language = language(optionalGuild(), author)

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
    val lang = language ?: languageSettings.defaultLanguage()
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