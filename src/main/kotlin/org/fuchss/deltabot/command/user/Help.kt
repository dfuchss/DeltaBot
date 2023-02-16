package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.command.ICommandRegistry
import org.fuchss.deltabot.command.isGuildAdmin
import org.fuchss.deltabot.utils.extensions.fetchCommands
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * A [BotCommand] that prints a temporary help message.
 */
open class Help(private val configuration: BotConfiguration, private val session: Session, protected val registry: ICommandRegistry) : GlobalCommand {

    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    override fun createCommand(): SlashCommandData {
        return Commands.slash("help", "Prints a help message")
    }

    override fun handle(event: SlashCommandInteraction) {
        val visibilities = mutableListOf(CommandPermissions.ALL)
        if (configuration.isAdmin(event.user)) {
            visibilities.add(CommandPermissions.ALL)
            visibilities.add(CommandPermissions.GUILD_ADMIN)
        } else if (isGuildAdmin(event, configuration, session)) {
            visibilities.add(CommandPermissions.GUILD_ADMIN)
        }

        var commands = if (event.isFromGuild) event.guild!!.fetchCommands() + event.jda.fetchCommands() else event.jda.fetchCommands()
        commands = commands.filter { c -> registry.permissions(c) in visibilities }

        val botName = event.guild?.selfMember?.effectiveName ?: event.jda.selfUser.name
        event.replyEmbeds(generateText(botName, commands)).setEphemeral(true).queue()
    }

    companion object {
        fun generateText(botName: String, rawCommands: List<Command>): MessageEmbed {
            val commands = rawCommands.sortedBy { c -> c.name.lowercase() }
            var message = ""
            for (cmd in commands) {
                message += "**/${cmd.name}**: ${cmd.description}\n"
                val subcommands = cmd.subcommands
                if (subcommands.isNotEmpty()) {
                    for (subcommand in subcommands)
                        message += "→ **${subcommand.name}**: ${subcommand.description}\n"
                }
            }

            return EmbedBuilder().setTitle("$botName Help").setDescription(message.trim()).setColor(Constants.BLUE).build()
        }
    }
}
