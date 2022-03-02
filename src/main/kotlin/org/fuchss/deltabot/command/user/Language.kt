package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.utils.extensions.internalLanguage
import org.fuchss.deltabot.utils.extensions.language
import org.fuchss.deltabot.utils.extensions.setLanguage
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.deltabot.utils.extensions.withFirst

/**
 * A [BotCommand] that sets the [Language] for certain users.
 */
class Language : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

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
        val guildLocale = event.getOption("guild-language")?.asString ?: ""
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

        if (response.isBlank())
            response = "You need to specify at least what language to you want to set".translate(event.language())

        event.reply(response.trim()).setEphemeral(true).queue()
    }
}
