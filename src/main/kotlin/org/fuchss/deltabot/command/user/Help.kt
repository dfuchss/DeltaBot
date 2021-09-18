package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions

open class Help(private val configuration: Configuration, protected val commands: List<BotCommand>) : BotCommand {

    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("help", "Prints a help message")
    }

    override fun handle(event: SlashCommandEvent) {
        val visibilities = mutableListOf(CommandPermissions.ALL)
        if (configuration.isAdmin(event.user)) {
            visibilities.add(CommandPermissions.ALL)
            visibilities.add(CommandPermissions.GUILD_ADMIN)
        } else if (event.user in configuration.getAdminsMembersOfGuild(event.guild)) {
            visibilities.add(CommandPermissions.GUILD_ADMIN)
        }

        val commands = commands.sorted().filter { c -> c.permissions in visibilities }
        event.replyEmbeds(generateText(event.jda, commands)).setEphemeral(true).queue()
    }

    companion object {
        fun generateText(jda: JDA, commands: List<BotCommand>): MessageEmbed {
            var message = ""
            for (cmd in commands) {
                message += "**/${cmd.name}**: ${cmd.description}\n"
                val subcommands = cmd.subcommands
                if (subcommands.isNotEmpty()) {
                    for (subcommand in subcommands)
                        message += "→ **${subcommand.name}**: ${subcommand.description}\n"
                }
            }
            return EmbedBuilder().setTitle(jda.selfUser.name + " Help").setDescription(message.trim()).setColor(Constants.BLUE).build()
        }
    }


}