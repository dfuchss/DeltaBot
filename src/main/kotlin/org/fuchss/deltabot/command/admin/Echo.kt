package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand

/**
 * A [BotCommand] that simply echos sent messages.
 */
class Echo : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN
   
    override fun createCommand(guild: Guild): CommandData {
        val cmd = CommandData("echo", "Simply echo the text you are writing now ..")
        val od: OptionData = OptionData(OptionType.STRING, "text", "the text you want to echo").setRequired(true)
        cmd.addOptions(od)
        return cmd
    }


    override fun handle(event: SlashCommandEvent) {
        val text = event.getOption("text")?.asString ?: ""
        event.reply(text.replace("<", "").replace(">", "")).queue()
    }
}