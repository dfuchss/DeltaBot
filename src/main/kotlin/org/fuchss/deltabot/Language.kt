package org.fuchss.deltabot

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.deltabot.db.dto.LanguageDTO
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * Definition of all supported languages with their locales.
 */
enum class Language(
    val locale: String
) {
    ENGLISH("en_GB"),
    DEUTSCH("de_DE");

    override fun toString(): String {
        val name = super.toString()
        return name[0] + name.lowercase().substring(1)
    }
}

class LanguageSettings(
    private val session: Session
) {
    private var languages: MutableSet<LanguageDTO> = mutableSetOf()

    init {
        val dbLanguages = session.getAllObjects(LanguageDTO::class.java)
        languages.addAll(dbLanguages)
    }

    fun defaultLanguage() = Language.ENGLISH

    fun userToLanguage(userId: String): Language? {
        val user = UserDTO.findDBUser(session, userId) ?: return null
        return userDTOToLanguage()[user]?.language()
    }

    fun userAndGuildToLanguage(
        userId: String,
        guildId: String
    ): Language? {
        val userDTO = UserDTO.findDBUser(session, userId) ?: return null
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return null
        return userAndGuildDTOToLanguage()[userDTO to guildDTO]?.language()
    }

    fun setUserToLanguage(
        user: User,
        language: Language
    ) {
        removeUserToLanguage(user.id)

        val userDTO = UserDTO.findDBUser(session, user) ?: UserDTO(user)
        val lang = LanguageDTO(userDTO, language)
        session.persist(lang)
        languages += lang
    }

    fun removeUserToLanguage(userId: String) {
        val userDTO = UserDTO.findDBUser(session, userId) ?: return
        val lang = languages.find { l -> l.userDTO == userDTO && l.guildDTO == null } ?: return
        deleteLanguage(lang)
    }

    fun removeUserAndGuildToLanguage(
        userId: String,
        guildId: String
    ) {
        val userDTO = UserDTO.findDBUser(session, userId) ?: return
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return
        val lang = languages.find { l -> l.userDTO == userDTO && l.guildDTO == guildDTO } ?: return
        deleteLanguage(lang)
    }

    fun setUserAndGuildToLanguage(
        user: User,
        guild: Guild,
        language: Language
    ) {
        removeUserAndGuildToLanguage(user.id, guild.id)

        val userDTO = UserDTO.findDBUser(session, user) ?: UserDTO(user)
        val guildDTO = GuildDTO.findDBGuild(session, guild) ?: GuildDTO(guild)
        val lang = LanguageDTO(userDTO, guildDTO, language)
        session.persist(lang)
        languages += lang
    }

    fun guildToLanguage(guildId: String): Language? {
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return null
        return guildDTOToLanguage()[guildDTO]?.language()
    }

    fun removeGuildToLanguage(guildId: String) {
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return
        val lang = languages.find { l -> l.userDTO == null && l.guildDTO == guildDTO } ?: return
        deleteLanguage(lang)
    }

    fun setGuildToLanguage(
        guild: Guild,
        language: Language
    ) {
        removeGuildToLanguage(guild.id)

        val guildDTO = GuildDTO.findDBGuild(session, guild) ?: GuildDTO(guild)
        val lang = LanguageDTO(guildDTO, language)
        session.persist(lang)
        languages += lang
    }

    private fun deleteLanguage(lang: LanguageDTO) {
        lang.delete(session)
        languages.remove(lang)
    }

    private fun userDTOToLanguage() = languages.filter { l -> l.userDTO != null && l.guildDTO == null }.associateBy { l -> l.userDTO!! }

    private fun guildDTOToLanguage() = languages.filter { l -> l.userDTO == null && l.guildDTO != null }.associateBy { l -> l.guildDTO!! }

    private fun userAndGuildDTOToLanguage() = languages.filter { l -> l.userDTO != null && l.guildDTO != null }.associateBy { l -> l.userDTO!! to l.guildDTO!! }
}
