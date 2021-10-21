package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

/**
 * The interface for all commands of the bot.
 */
interface BotCommand : Comparable<BotCommand> {
    /**
     * The permissions of the command.
     */
    val permissions: CommandPermissions

    /**
     * An indicator whether this is a global bot command (or a guild command).
     */
    val isGlobal: Boolean

    /**
     * The name of the command.
     */
    val name: String get() = createCommand().name

    /**
     * The description of the command.
     */
    val description: String get() = createCommand().description

    /**
     * All data for subcommands of the command.
     */
    val subcommands: List<SubcommandData> get() = createCommand().subcommands

    override fun compareTo(other: BotCommand): Int {
        return this.name.lowercase().compareTo(other.name.lowercase())
    }

    /**
     * Create the command data for discord.
     */
    fun createCommand(): CommandData

    /**
     * Handle the [SlashCommandEvent] for this command.
     */
    fun handle(event: SlashCommandEvent)

    /**
     * Will be invoked upon [ReadyEvent] at the startup of the bot.
     */
    fun registerJDA(jda: JDA) {
        // NOP
    }
}