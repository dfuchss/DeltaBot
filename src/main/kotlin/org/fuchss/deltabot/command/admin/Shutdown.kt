package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand

/**
 * A [BotCommand] that shutdown the bot.
 */
class Shutdown : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(): SlashCommandData {
        return Commands.slash("shutdown", "Shutdown/Restart the bot")
    }

    override fun handle(event: SlashCommandInteraction) {
        event.reply("Shutting down").setEphemeral(true).complete()
        event.jda.shutdown()
    }
}
