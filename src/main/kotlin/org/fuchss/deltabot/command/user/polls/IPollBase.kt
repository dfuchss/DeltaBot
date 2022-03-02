package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent

interface IPollBase {
    fun terminate(jda: JDA, user: User, mid: String)
    fun removePoll(jda: JDA, user: User, mid: String)
    fun refreshPoll(jda: JDA, user: User, mid: String)
    fun isOwner(event: ButtonClickEvent, mid: String): Boolean
    fun postpone(jda: JDA, user: User, mid: String, minutes: Int)
}
