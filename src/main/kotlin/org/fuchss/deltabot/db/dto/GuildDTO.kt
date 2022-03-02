package org.fuchss.deltabot.db.dto

import net.dv8tion.jda.api.entities.Guild
import org.fuchss.objectcasket.port.Session
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "Guild")
class GuildDTO {

    companion object {
        fun findDBGuild(session: Session?, guild: Guild): GuildDTO? {
            val dbGuild = findDBGuild(session, guild.id) ?: return null
            dbGuild.update(session, guild)
            return dbGuild
        }

        fun findDBGuild(session: Session?, guildId: String): GuildDTO? {
            if (session == null)
                return null
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

    private fun update(session: Session?, guild: Guild) {
        readableName = guild.name
        session?.persist(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GuildDTO

        return discordId == other.discordId
    }

    override fun hashCode(): Int = discordId.hashCode()
}
