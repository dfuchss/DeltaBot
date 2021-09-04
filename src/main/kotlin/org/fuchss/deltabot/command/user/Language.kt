package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.setLanguage
import org.fuchss.deltabot.translate

class Language : BotCommand {
    override val isAdminCommand: Boolean get() = false
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        val command = CommandData("language", "set your bot language")
        command.addOptions(OptionData(OptionType.STRING, "lang", "your language").setRequired(true).addChoices(
            Language.values().map { l -> Command.Choice(l.toString(), l.locale) }
        ))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val locale = event.getOption("lang")?.asString ?: ""
        val language = Language.values().find { l -> l.locale == locale }
        if (language == null) {
            event.reply("I can't find your new locale. Please ask the admin".translate(event.user)).setEphemeral(true).complete()
            return
        }
        event.user.setLanguage(language)
        event.reply("Your new language is #".translate(event.user, language)).setEphemeral(true).complete()
    }
}