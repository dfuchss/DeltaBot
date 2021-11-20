package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.translate
import kotlin.random.Random

/**
 * A [BotCommand] that distributes members of a [VoiceChannel] into teams.
 */
class Teams : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    override fun createCommand(guild: Guild): CommandData {
        val command = CommandData("teams", "create a team based on the people in your voice channel")
        command.addOptions(OptionData(OptionType.INTEGER, "amount", "the amount of teams (default: 2)").setRequired(false))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val voiceChannel = event.member?.voiceState?.channel
        if (voiceChannel == null) {
            event.reply("You are not in a voice channel".translate(event)).setEphemeral(true).queue()
            return
        }

        val members: MutableList<User> = voiceChannel.members.map { m -> m.user }.sortedBy { u -> u.name.lowercase() }.toMutableList()
        if (members.size < 2) {
            event.reply("There are not enough members in the voice channel".translate(event)).setEphemeral(true).queue()
            return
        }

        val amount = event.getOption("amount")?.asLong?.toInt() ?: 2
        if (amount < 2) {
            event.reply("Not enough teams".translate(event)).setEphemeral(true).queue()
            return
        }

        val teams = Array(amount) { ArrayList<User>() }
        var idx = 0
        while (members.isNotEmpty()) {
            val u = members.removeAt(Random.nextInt(members.size))
            teams[idx].add(u)
            idx %= teams.size
        }

        var msg = "Teams:\n\n"
        for (t in teams.indices) {
            msg += "Team ${t + 1}: ${teams[t].joinToString { m -> m.asMention }}"
        }

        event.reply(msg).queue()
    }
}