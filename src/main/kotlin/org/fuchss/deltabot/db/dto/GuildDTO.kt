package org.fuchss.deltabot.db.dto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.fuchss.objectcasket.objectpacker.port.Session

@Entity
@Table(name = "Guild")
class GuildDTO {
    companion object {
        fun findDBGuild(
            session: Session?,
            guild: Guild
        ): GuildDTO? {
            val dbGuild = findDBGuild(session, guild.id) ?: return null
            dbGuild.update(session, guild)
            return dbGuild
        }

        fun findDBGuild(
            session: Session?,
            guildId: String
        ): GuildDTO? {
            if (session == null) {
                return null
            }
            val guilds = session.getAllObjects(GuildDTO::class.java)
            return guilds.find { u -> u.discordId == guildId }
        }
    }

    constructor()
    constructor(guild: Guild) {
        discordId = guild.id
        update(null, guild)
    }

    @Id
    @GeneratedValue
    var id: Int? = null

    @Column(name = "discord_id")
    var discordId: String = ""

    @Column(name = "readable_name")
    var readableName: String = ""

    @Column(name = "admins")
    @ManyToMany
    var admins: MutableSet<UserDTO> = mutableSetOf()

    private fun update(
        session: Session?,
        guild: Guild
    ) {
        readableName = guild.name
        session?.persist(this)
    }

    fun toggleGuildAdmin(
        session: Session,
        user: User
    ): Boolean {
        val userDTO = UserDTO.findDBUser(session, user) ?: UserDTO(user)
        val nowAdmin =
            if (userDTO in admins) {
                admins.remove(userDTO)
                false
            } else {
                admins.add(userDTO)
                true
            }
        session.persist(this)
        return nowAdmin
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildDTO

        return discordId == other.discordId
    }

    override fun hashCode(): Int = discordId.hashCode()
}
