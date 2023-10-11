package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchMember
import org.fuchss.deltabot.utils.extensions.fetchOwner
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.languageSettings
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * A [BotCommand] that prints the current state of the bot to discord.
 */
class State(private val config: BotConfiguration, private val scheduler: Scheduler, private val session: Session) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(): SlashCommandData {
        return Commands.slash("state", "print the state of the bot")
    }

    override fun handle(event: SlashCommandInteraction) {
        var msg = ""
        msg += "Admins: ${config.getAdmins(event.jda).joinToString { u -> u.asMention }}\n"
        if (event.isFromGuild) {
            val guild = GuildDTO.findDBGuild(session, event.guild!!)
            val admins = mutableListOf(event.guild!!.fetchOwner().asMention)
            if (guild != null) admins += guild.admins.mapNotNull { event.jda.fetchUser(it.discordId)?.asMention }
            msg += "Guild Admins: ${admins.joinToString()}\n"
        }
        msg += "Debug: ${config.debug}\n"
        msg += "Docker: ${config.runInDocker()}\n"
        msg += "Scheduler Queue: ${scheduler.size()}\n"

        msg += "Language (Default): ${languageSettings().defaultLanguage()}\n"
        if (event.guild != null) {
            msg += "Language (Guild): ${languageSettings().guildToLanguage(event.guild!!.id)}\n"
        }

        msg += "Registered Users: ${findUsers(event.jda, session, event.guild)}"
        event.replyEmbeds(EmbedBuilder().setTitle("Current State").setDescription(msg).setColor(Constants.BLUE).build()).setEphemeral(true).queue()
    }

    private fun findUsers(
        jda: JDA,
        session: Session,
        guild: Guild?
    ): List<String> {
        val users = session.getAllObjects(UserDTO::class.java)
        var discordUsers = users.mapNotNull { dto -> jda.fetchUser(dto.discordId) }

        if (guild != null) {
            discordUsers = discordUsers.mapNotNull { u -> guild.fetchMember(u.id)?.user }
        }

        return discordUsers.map { u -> u.asMention }
    }
}
