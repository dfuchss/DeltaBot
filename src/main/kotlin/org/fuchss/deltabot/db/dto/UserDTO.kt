package org.fuchss.deltabot.db.dto

import net.dv8tion.jda.api.entities.User
import org.fuchss.objectcasket.port.Session
import javax.persistence.*

@Entity
@Table(name = "User")
class UserDTO {

    companion object {
        fun findDBUser(session: Session?, user: User): UserDTO? {
            val dbUser = findDBUser(session, user.id) ?: return null
            dbUser.update(session, user)
            return dbUser
        }

        fun findDBUser(session: Session?, userId: String): UserDTO? {
            if (session == null)
                return null

            val users = session.getAllObjects(UserDTO::class.java)
            return users.find { u -> u.discordId == userId }
        }
    }


    constructor()
    constructor(user: User) {
        discordId = user.id
        update(null, user)
    }

    @Id
    @GeneratedValue
    var id: Int? = null

    @Column(name = "discord_id")
    var discordId: String = ""

    @Column(name = "readable_name")
    var readableName: String = ""

    private fun update(session: Session?, user: User) {
        readableName = "${user.name}#${user.discriminator}"
        session?.persist(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserDTO
        return discordId == other.discordId
    }

    override fun hashCode(): Int = discordId.hashCode()
}