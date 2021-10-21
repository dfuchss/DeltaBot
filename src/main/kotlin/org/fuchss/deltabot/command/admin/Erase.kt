package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.fetchHistory
import org.fuchss.deltabot.utils.logger

/**
 * A [BotCommand] that removes every message from a channel.
 */
class Erase : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        return CommandData("erase", "erase all content of a channel")
    }

    override fun handle(event: SlashCommandEvent) {
        event.reply("Starting the deletion of all messages ... if not all messages will be deleted restart your client or ask the bot owner").setEphemeral(true).queue()

        val deletion = Thread { deleteMessages(event.channel) }
        deletion.isDaemon = true
        deletion.start()
    }

    private fun deleteMessages(channel: MessageChannel) {
        val retrieveMax = 20
        try {
            var history = channel.history.fetchHistory(retrieveMax)
            while (history.isNotEmpty()) {
                for (m in history) {
                    m.delete().queue()
                }
                history = channel.history.fetchHistory(retrieveMax)
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
}