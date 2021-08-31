package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.command.BotCommand

class Echo : BotCommand {
    override fun createCommand(): CommandData {
        val cmd = CommandData("echo", "Simply Echo the text you are writing now ..")
        val od: OptionData = OptionData(OptionType.STRING, "text", "the text you want to echo").setRequired(true)
        cmd.addOptions(od)
        return cmd
    }

    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = false

    override fun handle(event: SlashCommandEvent) {
        val text = event.getOption("text")?.asString ?: ""
        event.reply(text.replace("<", "").replace(">", "")).complete()
    }
}