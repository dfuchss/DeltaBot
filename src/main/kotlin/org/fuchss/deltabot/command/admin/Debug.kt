package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand

/**
 * A [BotCommand] that toggles the debug state.
 */
class Debug(private val configuration: BotConfiguration) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(): SlashCommandData {
        return Commands.slash("debug", "toggle the debug flag")
    }

    override fun handle(event: SlashCommandInteraction) {
        event.reply("Debug is now ${configuration.toggleDebug()}").setEphemeral(true).queue()
    }
}
