package org.fuchss.deltabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.utils.*
import org.slf4j.spi.LocationAwareLogger

/**
 * The configuration of the bot.
 */
data class Configuration(
    /**
     * The path to the multi nlu interpreter.
     */
    var nluUrl: String = "http://localhost:5005",
    /**
     * The path to ducking service.
     */
    var ducklingUrl: String = "http://localhost:8000",
    /**
     * The threshold for filtering nlu intents.
     */
    var nluThreshold: Double = 0.7,
    /**
     * A list of all global admins identified by [User.getId]
     */
    private var admins: MutableList<String> = ArrayList(),
    /**
     * Indicator whether debug is enabled.
     */
    var debug: Boolean = false,
    /**
     * Indicator whether the NLU Unit is disabled.
     */
    var disableNlu: Boolean = false
) : Storable() {

    fun isAdmin(user: User): Boolean {
        return admins.contains(user.id)
    }

    fun toggleAdmin(user: User): Boolean {
        if (admins.contains(user.id))
            admins.remove(user.id)
        else
            admins.add(user.id)

        this.store()
        return admins.contains(user.id)
    }

    fun getAdmins(jda: JDA): List<User> {
        return admins.mapNotNull { u -> jda.fetchUser(u) }
    }

    fun toggleDebug(): Boolean {
        debug = !debug

        if (debug) {
            logger.setLogLevel(LocationAwareLogger.DEBUG_INT)
        } else {
            logger.setLogLevel(LocationAwareLogger.INFO_INT)
        }

        this.store()
        return debug
    }

    fun getAdminsMembersOfGuild(guild: Guild?): List<User> {
        if (guild == null)
            return emptyList()
        val adminsOfGuild = mutableListOf<User>()
        adminsOfGuild.add(guild.fetchMember(guild.ownerId)!!.user)

        for (admin in admins) {
            val member = guild.fetchMember(admin)
            if (member != null && member.user !in adminsOfGuild) {
                adminsOfGuild.add(member.user)
            }
        }

        return adminsOfGuild
    }

    fun hasAdmins() = admins.isNotEmpty()

}


