package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.fuchss.deltabot.command.BotCommand

class Roles : BotCommand {
    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        val command = CommandData("roles", "manage the role changer message of this guild")
        command.addSubcommands(
            SubcommandData("init", "creates the role changer message in this channel"),
            SubcommandData("add", "adds an emoji for a specific role").addOptions(
                OptionData(OptionType.STRING, "emoji", "the emoji for the role").setRequired(true),
                OptionData(OptionType.ROLE, "role", "the role that shall be added to the message").setRequired(true)
            ),
            SubcommandData("del", "remove an emoji from the role changer message").addOptions(
                OptionData(OptionType.STRING, "emoji", "the emoji to delete").setRequired(true)
            ),
            SubcommandData("purge", "remove the whole message from the guild")
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        println(event)
    }
}