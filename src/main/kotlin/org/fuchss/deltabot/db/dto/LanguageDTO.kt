package org.fuchss.deltabot.db.dto

import org.fuchss.deltabot.Language
import javax.persistence.*

@Entity
@Table(name = "Language")
class LanguageDTO {

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

    @Id
    @GeneratedValue
    var id: Int? = null

    @ManyToOne
    var userDTO: UserDTO?

    @ManyToOne
    var guildDTO: GuildDTO?

    var languageName: String?

    fun language() = Language.valueOf(languageName!!)
}
