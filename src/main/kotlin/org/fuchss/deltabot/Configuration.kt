package org.fuchss.deltabot

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.utils.Storable

/**
 * The configuration of the bot.
 */
data class Configuration(
    var nluUrl: String = "http://localhost:5005",
    var docklingUrl: String = "http://localhost:8000",
    var nluThreshold: Double = 0.7,
    private var admins: MutableList<String> = ArrayList(),
    var debug: Boolean = false,
    var disableNlu: Boolean = false
) : Storable() {

    fun isAdmin(user: User): Boolean {
        return admins.isEmpty() || admins.contains(user.id)
    }

    fun toggleAdmin(user: User): Boolean {
        if (admins.contains(user.id))
            admins.remove(user.id)
        else
            admins.add(user.id)

        this.store()
        return admins.contains(user.id)
    }

    fun getAdmins(jda: JDA): List<String> {
        return admins.mapNotNull { u -> jda.retrieveUserById(u).complete()?.asMention }
    }

    fun toggleDebug(): Boolean {
        debug = !debug
        this.store()
        return debug
    }

}