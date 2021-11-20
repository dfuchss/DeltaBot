package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.languageSettings
import org.fuchss.objectcasket.port.Session

/**
 * A [BotCommand] that prints the current state of the bot to discord.
 */
class State(private val config: BotConfiguration, private val scheduler: Scheduler, private val session: Session) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(): CommandData {
        return CommandData("state", "print the state of the bot")
    }

    override fun handle(event: SlashCommandEvent) {
        var msg = ""
        msg += "NLU: ${config.nluUrl}, Threshold: ${config.nluThreshold}, State: ${if (config.disableNlu) "disabled" else "enabled"}\n"
        msg += "Admins: ${config.getAdmins(event.jda).joinToString { u -> u.asMention }}\n"
        msg += "Debug: ${config.debug}\n"
        msg += "Docker: ${config.runInDocker()}\n"
        msg += "Scheduler Queue: ${scheduler.size()}\n"

        msg += "Language (Default): ${languageSettings().defaultLanguage()}\n"
        if (event.guild != null)
            msg += "Language (Guild): ${languageSettings().guildToLanguage(event.guild!!.id)}\n"

        msg += "Registered Users: ${findUsers(event.jda, session, event.guild)}"
        event.replyEmbeds(EmbedBuilder().setTitle("Current State").setDescription(msg).setColor(Constants.BLUE).build()).setEphemeral(true).queue()
    }

    private fun findUsers(jda: JDA, session: Session, guild: Guild?): List<String> {
        val users = session.getAllObjects(UserDTO::class.java)
        var discordUsers = users.mapNotNull { dto -> jda.fetchUser(dto.discordId) }

        if (guild != null) {
            discordUsers = discordUsers.filter { u -> guild.isMember(u) }
        }

        return discordUsers.map { u -> u.asMention }
    }
}