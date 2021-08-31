package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand

class State(private val config: Configuration) : BotCommand {
    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("state", "print the state of the bot")
    }

    override fun handle(event: SlashCommandEvent) {
        var msg = "Current State:\n"
        msg += "NLU: ${config.nluUrl}, Threshold: ${config.nluThreshold}, State: ${if (config.disableNlu) "disabled" else "enabled"}\n"
        msg += "Admins: ${config.getAdmins(event.jda).joinToString()}\n"
        msg += "Debug: ${config.debug}"
        event.reply(msg).setEphemeral(true).complete()
    }
}