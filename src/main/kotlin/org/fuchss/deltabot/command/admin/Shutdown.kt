package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions

class Shutdown : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("shutdown", "Shutdown/Restart the bot")
    }

    override fun handle(event: SlashCommandEvent) {
        event.reply("Shutting down").setEphemeral(true).complete()
        event.jda.shutdown()
    }
}