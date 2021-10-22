package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions

/**
 * A [BotCommand] that prints the current state of the bot to discord.
 */
class State(private val config: BotConfiguration) : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("state", "print the state of the bot")
    }

    override fun handle(event: SlashCommandEvent) {
        var msg = "Current State:\n"
        msg += "NLU: ${config.nluUrl}, Threshold: ${config.nluThreshold}, State: ${if (config.disableNlu) "disabled" else "enabled"}\n"
        msg += "Admins: ${config.getAdmins(event.jda).joinToString { u -> u.asMention }}\n"
        msg += "Debug: ${config.debug}"
        event.reply(msg).setEphemeral(true).queue()
    }
}