package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.objectcasket.port.Session

/**
 * A [BotCommand] that toggles the admin state for a user in a guild.
 */
class GuildAdmin(private val session: Session) : GuildCommand {

    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("guild-admin", "Op or de-op an user")
        command.addOptions(OptionData(OptionType.USER, "user", "the user that shall be changed").setRequired(true))
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
        val user = event.getOption("user")?.asUser

        if (user == null || user.isBot || event.guild?.owner?.user == user) {
            event.reply("No valid user was mentioned").setEphemeral(true).queue()
            return
        }

        val guildDTO = GuildDTO.findDBGuild(session, event.guild!!) ?: GuildDTO(event.guild!!)
        val nowAdmin = guildDTO.toggleGuildAdmin(session, user)
        event.reply("User ${user.asMention} is now ${if (nowAdmin) "an" else "no"} guild admin").setEphemeral(true).queue()
    }
}
