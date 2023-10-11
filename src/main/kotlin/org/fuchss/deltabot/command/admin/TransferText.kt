package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion
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
import org.fuchss.deltabot.utils.extensions.translate
import java.time.format.DateTimeFormatter

/**
 * A [BotCommand] that copies a text from one channel to another.
 */
class TransferText : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("transfer", "copy text from a channel to another")
        command.addOptions(
            OptionData(
                OptionType.CHANNEL,
                "source",
                "the source channel"
            ).setRequired(true),
            OptionData(OptionType.CHANNEL, "target", "the target channel").setRequired(true)
        )
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
        val from = event.getOption("source")?.asChannel!!
        val to = event.getOption("target")?.asChannel!!

        val hook = event.deferReply(true).complete()

        val messages = mutableListOf<Message>()
        historyOf(messages, from)

        if (messages.isEmpty()) {
            hook.editOriginal("No messages found in #".translate(event, from.asMention)).queue()
            return
        }

        sendMessage(to, "Transfer from # to # ...".translate(event, from.asMention, to.asMention))
        messages.map { toResponse(it) }.all { sendMessage(to, it) }
    }

    private fun toResponse(message: Message): String {
        val author = message.author.name
        val date = message.timeCreated.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM.YYYY hh:mm"))
        val content = message.contentDisplay
        return "$author->[$date]: ```\n${content.lines().joinToString("\n") { it }}\n```"
    }

    private fun sendMessage(
        channel: GuildChannelUnion,
        content: String
    ): Boolean {
        if (channel is MessageChannel) {
            channel.sendMessage(content).queue()
            return true
        }
        return false
    }

    private fun historyOf(
        result: MutableList<Message>,
        channel: GuildChannelUnion
    ) {
        require(result.isEmpty())

        if (channel is MessageChannel) {
            val retrieveMax = 20
            try {
                val history = channel.history
                var messages = history.fetchHistory(retrieveMax)
                while (messages.isNotEmpty()) {
                    result += messages
                    messages = history.fetchHistory(retrieveMax)
                }
            } catch (e: Exception) {
                logger.error(e.message, e)
            }
        }

        result.reverse()
    }
}
