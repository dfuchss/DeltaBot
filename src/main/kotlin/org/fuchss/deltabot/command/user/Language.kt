package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.internalLanguage
import org.fuchss.deltabot.setLanguage
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.withFirst

class Language : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        val languages = Language.values().map { l -> Command.Choice(l.toString(), l.locale) }.withFirst(Command.Choice("None", "None"))
        val command = CommandData("language", "set your bot language")
        command.addOptions(
            OptionData(OptionType.STRING, "guild-language", "your language at this guild").addChoices(languages),
            OptionData(OptionType.STRING, "lang", "your language").addChoices(languages)
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val ownLocale = event.getOption("lang")?.asString ?: ""
        val guildLocale = event.getOption("guild-lang")?.asString ?: ""
        val ownLanguage = Language.values().find { l -> l.locale == ownLocale }
        val guildLanguage = Language.values().find { l -> l.locale == guildLocale }

        var response = ""

        if (ownLocale.isNotBlank()) {
            event.user.setLanguage(ownLanguage)
            response = "Your new language is #".translate(event.user.internalLanguage(), ownLanguage ?: "--") + "\n"
        }

        if (guildLocale.isNotBlank()) {
            if (event.guild == null) {
                response += "You can't set your guild language in a private channel".translate(event.user.internalLanguage())
            } else {
                event.user.setLanguage(guildLanguage, event.guild!!)
                response = "Your new guild language is #".translate(event.user.internalLanguage(event.guild!!), ownLanguage ?: "--")
            }


        }

        event.reply(response.trim()).setEphemeral(true).queue()
    }
}