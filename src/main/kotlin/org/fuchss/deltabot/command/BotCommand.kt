package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData

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
     * Handle the [SlashCommandInteraction] for this command.
     */
    fun handle(event: SlashCommandInteraction)
}

interface GuildCommand : BotCommand {
    /**
     * Create the command data for discord.
     */
    fun createCommand(guild: Guild): SlashCommandData
}

interface GlobalCommand : BotCommand {
    /**
     * Create the command data for discord.
     */
    fun createCommand(): SlashCommandData
}
