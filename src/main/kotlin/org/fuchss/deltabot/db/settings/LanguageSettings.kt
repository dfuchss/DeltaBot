package org.fuchss.deltabot.db.settings

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.objectcasket.port.Session
import javax.persistence.*

@Entity
class LanguageSettings {
    constructor()

    constructor(session: Session) {
        this.session = session
    }

    @Transient
    private var session: Session? = null

    fun setSession(session: Session) {
        this.session = session
    }

    @Id
    @GeneratedValue
    private var id: Int? = null

    private var defaultLanguageName: String = Language.ENGLISH.name

    @OneToMany
    private var languages: MutableSet<LanguageDTO> = mutableSetOf()

    fun defaultLanguage() = Language.valueOf(defaultLanguageName)

    fun userToLanguage(userId: String): Language? {
        val user = UserDTO.findDBUser(session, userId) ?: return null

        return userDTOToLanguage()[user]?.language()
    }


    fun userAndGuildToLanguage(userId: String, guildId: String): Language? {
        val userDTO = UserDTO.findDBUser(session!!, userId) ?: return null
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return null

        return userAndGuildDTOToLanguage()[userDTO to guildDTO]?.language()
    }


    fun setUserToLanguage(user: User, language: Language) {
        removeUserToLanguage(user.id)

        val userDTO = UserDTO.findDBUser(session!!, user) ?: UserDTO(user)
        languages += LanguageDTO(userDTO, language)
        store()
    }

    fun removeUserToLanguage(userId: String) {
        val userDTO = UserDTO.findDBUser(session, userId) ?: return
        languages.removeIf { l -> l.userDTO == userDTO && l.guildDTO == null }
        store()
    }

    fun removeUserAndGuildToLanguage(userId: String, guildId: String) {
        val userDTO = UserDTO.findDBUser(session!!, userId) ?: return
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return
        languages.removeIf { l -> l.userDTO == userDTO && l.guildDTO == guildDTO }
        store()
    }

    fun setUserAndGuildToLanguage(user: User, guild: Guild, language: Language) {
        removeUserAndGuildToLanguage(user.id, guild.id)

        val userDTO = UserDTO.findDBUser(session!!, user) ?: UserDTO(user)
        val guildDTO = GuildDTO.findDBGuild(session!!, guild) ?: GuildDTO(guild)
        languages += LanguageDTO(userDTO, guildDTO, language)
        store()
    }

    fun guildToLanguage(guildId: String): Language? {
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return null
        return guildDTOToLanguage()[guildDTO]?.language()
    }

    fun removeGuildToLanguage(guildId: String) {
        val guildDTO = GuildDTO.findDBGuild(session, guildId) ?: return
        languages.removeIf { l -> l.userDTO == null && l.guildDTO == guildDTO }
        store()
    }

    fun setGuildToLanguage(guild: Guild, language: Language) {
        removeGuildToLanguage(guild.id)

        val guildDTO = GuildDTO.findDBGuild(session, guild) ?: GuildDTO(guild)
        languages += LanguageDTO(guildDTO, language)
        store()
    }

    private fun userDTOToLanguage() = languages.filter { l -> l.userDTO != null && l.guildDTO == null }.associateBy { l -> l.userDTO!! }
    private fun guildDTOToLanguage() = languages.filter { l -> l.userDTO == null && l.guildDTO != null }.associateBy { l -> l.guildDTO!! }
    private fun userAndGuildDTOToLanguage() = languages.filter { l -> l.userDTO != null && l.guildDTO != null }.associateBy { l -> l.userDTO!! to l.guildDTO!! }

    private fun store() {
        this.session!!.persist(this)
        this.languages.mapNotNull { l -> l.userDTO }.forEach { u -> this.session!!.persist(u) }
        this.languages.mapNotNull { l -> l.guildDTO }.forEach { u -> this.session!!.persist(u) }
    }
}