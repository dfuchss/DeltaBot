package org.fuchss.deltabot.utils

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.interactions.commands.Command

fun MessageHistory.fetchHistory(amount: Int): List<Message> {
    return try {
        this.retrievePast(amount).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        listOf()
    }
}

fun Guild.fetchCommands(): List<Command> {
    return try {
        this.retrieveCommands().complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return listOf()
    }
}

fun Guild.fetchMember(uid: String): Member? {
    try {
        val cached = this.getMemberById(uid)
        if (cached != null)
            return cached

        return this.retrieveMemberById(uid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun Guild.fetchMessage(cid: String, mid: String): Message? {
    try {
        val channel = this.jda.fetchTextChannel(this.id, cid) ?: return null
        return channel.retrieveMessageById(mid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}


fun JDA.fetchCommands(): List<Command> {
    return try {
        this.retrieveCommands().complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return listOf()
    }
}

fun JDA.fetchTextChannel(gid: String, cid: String): TextChannel? {
    try {
        val cached = this.getTextChannelById(cid)
        if (cached != null)
            return cached

        val guild = this.getGuildById(gid)!!
        return guild.getTextChannelById(gid)!!
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun JDA.fetchUser(uid: String): User? {
    try {
        val cached = this.getUserById(uid)
        if (cached != null)
            return cached

        return this.retrieveUserById(uid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun Message.refresh(): Message = this.channel.retrieveMessageById(this.id).complete()

fun Message.optionalGuild(): Guild? = if (isFromGuild) guild else null