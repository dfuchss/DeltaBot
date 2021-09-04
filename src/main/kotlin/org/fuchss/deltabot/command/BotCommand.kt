package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData

interface BotCommand : Comparable<BotCommand> {
    val isAdminCommand: Boolean
    val isGlobal: Boolean
    val name: String get() = createCommand().name
    val description: String get() = createCommand().description
    val subcommands: List<SubcommandData> get() = createCommand().subcommands

    override fun compareTo(other: BotCommand): Int {
        return this.name.lowercase().compareTo(other.name.lowercase())
    }

    fun createCommand(): CommandData
    fun handle(event: SlashCommandEvent)

    fun registerJDA(jda: JDA) {
        // NOP
    }
}