package org.fuchss.deltabot.db.dto

import org.fuchss.deltabot.Language
import org.fuchss.objectcasket.objectpacker.port.Session
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "Language")
class LanguageDTO {
    @Id
    @GeneratedValue
    var id: Int? = null

    @ManyToOne
    var userDTO: UserDTO?

    @ManyToOne
    var guildDTO: GuildDTO?

    var languageName: String?

    constructor() {
        userDTO = null
        guildDTO = null
        languageName = null
    }

    constructor(userDTO: UserDTO, guildDTO: GuildDTO, language: Language) {
        this.userDTO = userDTO
        this.guildDTO = guildDTO
        this.languageName = language.name
    }

    constructor(userDTO: UserDTO, language: Language) {
        this.userDTO = userDTO
        this.guildDTO = null
        this.languageName = language.name
    }

    constructor(guildDTO: GuildDTO, language: Language) {
        this.userDTO = null
        this.guildDTO = guildDTO
        this.languageName = language.name
    }

    fun language() = Language.valueOf(languageName!!)
    fun delete(session: Session) {
        guildDTO = null
        userDTO = null
        session.persist(this)
        session.delete(this)
    }
}
