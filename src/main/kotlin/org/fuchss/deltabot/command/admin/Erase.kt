package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.fetchHistory
import org.fuchss.deltabot.utils.extensions.logger

/**
 * A [BotCommand] that removes every message from a channel.
 */
class Erase : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("erase", "erase all content of a channel")
        command.addOptions(OptionData(OptionType.USER, "user", "an optional user to select certain messages").setRequired(false))
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
        event.reply("Starting the deletion of all messages ... if not all messages will be deleted restart your client or ask the bot owner").setEphemeral(true).queue()
        val userToDelete = event.getOption("user")?.asUser

        val deletion = Thread { deleteMessages(event.channel, userToDelete) }
        deletion.isDaemon = true
        deletion.start()
    }

    private fun deleteMessages(channel: MessageChannel, user: User?) {
        val retrieveMax = 20
        try {
            val history = channel.history
            var messages = history.fetchHistory(retrieveMax)
            while (messages.isNotEmpty()) {
                for (m in messages) {
                    if (user == null || m.author.id == user.id)
                        m.delete().queue()
                }
                messages = history.fetchHistory(retrieveMax)
            }
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
}
