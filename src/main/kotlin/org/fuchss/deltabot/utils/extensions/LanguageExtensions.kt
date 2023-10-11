package org.fuchss.deltabot.utils.extensions

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.CommandInteraction
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.LanguageSettings
import org.fuchss.objectcasket.objectpacker.port.Session

private var internalLanguageSettings: LanguageSettings? = null

fun languageSettings(): LanguageSettings = internalLanguageSettings ?: error("Language is not initialized")

fun initLanguage(session: Session) {
    internalLanguageSettings = LanguageSettings(session)
}

/**
 * Calculate the [Language] based on the current [Guild] and current [User].
 */
fun language(
    guild: Guild?,
    user: User?
): Language {
    if (guild != null) {
        val usersGuildLanguage = user?.internalLanguage(guild)
        if (usersGuildLanguage != null) {
            return usersGuildLanguage
        }

        val guildLanguage = guild.internalLanguage()
        if (guildLanguage != null) {
            return guildLanguage
        }
    }

    val userLanguage = user?.internalLanguage()
    if (userLanguage != null) {
        return userLanguage
    }

    return languageSettings().defaultLanguage()
}

/**
 * Read the global [Language] of a [User].
 */
fun User.internalLanguage() = languageSettings().userToLanguage(this.id)

/**
 * Read the guild override of a [Language] of a [User].
 */
fun User.internalLanguage(guild: Guild) = languageSettings().userAndGuildToLanguage(this.id, guild.id)

/**
 * Set the global [Language] for a [User].
 */
fun User.setLanguage(language: Language?) {
    if (language == null) {
        languageSettings().removeUserToLanguage(this.id)
    } else {
        languageSettings().setUserToLanguage(this, language)
    }
}

/**
 * Set the [Language] in a specific [Guild] for a [User].
 */
fun User.setLanguage(
    language: Language?,
    guild: Guild
) {
    if (language == null) {
        languageSettings().removeUserAndGuildToLanguage(this.id, guild.id)
    } else {
        languageSettings().setUserAndGuildToLanguage(this, guild, language)
    }
}

/**
 * Get the global [Guild] language iff set.
 */
fun Guild.internalLanguage() = languageSettings().guildToLanguage(this.id)

/**
 * Set the global [Guild] language.
 */
fun Guild.setLanguage(language: Language?) {
    if (language == null) {
        languageSettings().removeGuildToLanguage(this.id)
    } else {
        languageSettings().setGuildToLanguage(this, language)
    }
}

/**
 * Calculate the language based on a [CommandInteraction].
 */
fun CommandInteraction.language(): Language = language(guild, user)

/**
 * Calculate the language based on a [ButtonInteractionEvent].
 */
fun ButtonInteractionEvent.language(): Language = language(guild, user)

/**
 * Calculate the language based on a [MessageReceivedEvent].
 */
fun MessageReceivedEvent.language(): Language = language(message.optionalGuild(), message.author)

/**
 * Calculate the language based on a [Message] of a [User].
 */
fun Message.language(): Language = language(optionalGuild(), author)
