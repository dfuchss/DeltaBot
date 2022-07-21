package org.fuchss.deltabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.setLogLevel
import org.fuchss.objectcasket.port.Session
import org.slf4j.spi.LocationAwareLogger
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.Table
import javax.persistence.Transient

@Entity
@Table(name = "Configuration")
class BotConfiguration {
    companion object {
        fun loadConfig(session: Session): BotConfiguration {
            val configs = session.getAllObjects(BotConfiguration::class.java)

            val config = if (configs.isEmpty()) {
                val newConfig = BotConfiguration()
                session.persist(newConfig)
                newConfig
            } else {
                configs.first()
            }
            config.session = session

            logger.info("Loaded Config: ${config.nluUrl}, ${config.nluThreshold}, ${config.ducklingUrl}, Admins: '${config.admins}', Debug: ${config.debug}")

            return config
        }
    }

    @Transient
    private lateinit var session: Session

    @Id
    @GeneratedValue
    var id: Int? = null

    /**
     * The path to the multi nlu interpreter.
     */
    var nluUrl: String = "http://localhost:5005"

    /**
     * The path to ducking service.
     */
    var ducklingUrl: String = "http://localhost:8000"

    /**
     * The threshold for filtering nlu intents.
     */
    var nluThreshold: Double = 0.7

    /**
     * A list of all global admins identified by [User.getId]
     */
    @OneToMany
    private var admins: MutableSet<UserDTO> = mutableSetOf()

    /**
     * Indicator whether debug is enabled.
     */
    var debug: Boolean = false

    /**
     * Indicator whether the NLU Unit is disabled.
     */
    var disableNlu: Boolean = false

    init {
        if (runInDocker()) {
            nluUrl = "http://deltabot-nlu:5005"
            ducklingUrl = "http://deltabot-duckling:8000"
        }
    }

    fun runInDocker() = System.getenv("RUN_IN_DOCKER") == "true"

    fun isAdmin(user: User): Boolean {
        return admins.any { u -> u.discordId == user.id }
    }

    fun toggleAdmin(user: User): Boolean {
        if (admins.any { u -> u.discordId == user.id }) {
            admins.removeIf { u -> u.discordId == user.id }
        } else {
            admins.add(UserDTO(user))
        }

        session.persist(this)
        return admins.any { u -> u.discordId == user.id }
    }

    fun getAdmins(jda: JDA): List<User> {
        return admins.mapNotNull { u -> jda.fetchUser(u.discordId) }
    }

    fun toggleDebug(): Boolean {
        debug = !debug

        if (debug) {
            logger.setLogLevel(LocationAwareLogger.DEBUG_INT)
        } else {
            logger.setLogLevel(LocationAwareLogger.INFO_INT)
        }

        session.persist(this)
        return debug
    }

    fun hasAdmins() = admins.isNotEmpty()
}
