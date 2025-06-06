package org.fuchss.deltabot.utils.extensions

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageHistory
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Component
import net.dv8tion.jda.api.interactions.components.ItemComponent
import kotlin.math.max
import kotlin.math.min

fun MessageHistory.fetchHistory(amount: Int): List<Message> =
    try {
        this.retrievePast(amount).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        listOf()
    }

fun Guild.fetchCommands(): List<Command> {
    return try {
        this.retrieveCommands().complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return listOf()
    }
}

fun Guild.fetchOwner(): User = this.fetchMember(this.ownerId)!!.user

fun JDA.fetchCommands(): List<Command> {
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
        if (cached != null) {
            return cached
        }

        return this.retrieveMemberById(uid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun Guild.fetchMessage(
    cid: String,
    mid: String
): Message? {
    try {
        val channel = this.jda.fetchChannel(this.id, cid) ?: return null
        return channel.retrieveMessageById(mid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun JDA.fetchChannel(
    gid: String,
    cid: String
): GuildMessageChannel? {
    try {
        val cached = this.getGuildChannelById(cid)
        if (cached != null) {
            return cached as? GuildMessageChannel
        }

        val guild = this.getGuildById(gid)!!
        return guild.getGuildChannelById(gid) as? GuildMessageChannel
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun JDA.fetchUser(uid: String): User? {
    try {
        val cached = this.getUserById(uid)
        if (cached != null) {
            return cached
        }

        return this.retrieveUserById(uid).complete()
    } catch (e: Exception) {
        logger.error(e.message)
        return null
    }
}

fun Message.refresh(): Message = this.channel.retrieveMessageById(this.id).complete()

fun Message.optionalGuild(): Guild? = if (isFromGuild) guild else null

/**
 * Pin a [Message] and delete the response in Discord.
 */
fun Message.pinAndDelete() {
    try {
        pin().complete()
        val history = channel.history.retrievePast(1).complete()
        if (history.isEmpty()) {
            return
        }

        val pinned = history[0] ?: return
        if (!pinned.author.isBot) {
            return
        }
        if (pinned.id == this.id) {
            return
        }
        if (pinned.messageReference?.messageId != this.id) {
            return
        }

        pinned.delete().complete()
    } catch (e: Exception) {
        logger.error(e.message)
    }
}

/**
 * Create [ActionRows][ActionRow] out of a list of [Components][Component].
 * @param[maxInRow] the maximum number of elements in a row
 * @param[tryModZero] indicator whether the system shall try to create rows with equal amounts of elements
 */
fun <E : ItemComponent> List<E>.toActionRows(
    maxInRow: Int = 5,
    tryModZero: Boolean = true
): List<ActionRow> {
    var maxItems = max(min(maxInRow, 5), 1)
    if (tryModZero && maxItems > 3) {
        for (i in maxItems downTo 3) {
            if (this.size % i == 0) {
                maxItems = i
                break
            }
        }
    }

    val rows = mutableListOf<ActionRow>()
    var row = mutableListOf<E>()
    for (c in this) {
        if (row.size >= maxItems) {
            rows.add(ActionRow.of(row))
            row = mutableListOf()
        }
        row.add(c)
    }

    if (row.isNotEmpty()) {
        rows.add(ActionRow.of(row))
    }

    return rows
}
