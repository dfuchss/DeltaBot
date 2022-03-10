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

/**
 * A [BotCommand] that simply echos sent messages.
 */
class Echo : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val cmd = Commands.slash("echo", "Simply echo the text you are writing now ..")
        val od: OptionData = OptionData(OptionType.STRING, "text", "the text you want to echo").setRequired(true)
        cmd.addOptions(od)
        return cmd
    }

    override fun handle(event: SlashCommandInteraction) {
        val text = event.getOption("text")?.asString ?: ""
        event.reply(text.replace("<", "").replace(">", "")).queue()
    }
}
