package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand

class Debug(private val configuration: Configuration) : BotCommand {
    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("debug", "toggle the debug flag")
    }

    override fun handle(event: SlashCommandEvent) {
        event.reply("Debug is not ${configuration.toggleDebug()}")
    }
}