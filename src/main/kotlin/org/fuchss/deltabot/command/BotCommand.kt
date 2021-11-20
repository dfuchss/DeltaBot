package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData

/**
 * The interface for all commands of the bot.
 */
interface BotCommand {
    /**
     * The permissions of the command.
     */
    val permissions: CommandPermissions

    /**
     * Will be invoked upon [ReadyEvent] at the startup of the bot.
     */
    fun registerJDA(jda: JDA) {
        // NOP
    }

    /**
     * Handle the [SlashCommandEvent] for this command.
     */
    fun handle(event: SlashCommandEvent)
}

interface GuildCommand : BotCommand {
    /**
     * Create the command data for discord.
     */
    fun createCommand(guild: Guild): CommandData
}

interface GlobalCommand : BotCommand {
    /**
     * Create the command data for discord.
     */
    fun createCommand(): CommandData
}