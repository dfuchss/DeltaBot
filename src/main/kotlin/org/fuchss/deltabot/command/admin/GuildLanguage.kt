package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
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

/**
 * A [BotCommand] that changes the [Language] of a [Guild].
 */
class GuildLanguage : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        val command = CommandData("guild-language", "set the bot language of your guild")
        command.addOptions(
            OptionData(OptionType.STRING, "lang", "your language").setRequired(true).addChoices(
                Language.values().map { l -> Command.Choice(l.toString(), l.locale) }.withFirst(Command.Choice("None", "None"))
            )
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val guild = event.guild
        if (guild == null) {
            event.reply("You have to execute the command in a guild".translate(event)).setEphemeral(true).queue()
            return
        }

        val locale = event.getOption("lang")?.asString ?: ""
        val language = Language.values().find { l -> l.locale == locale }
        guild.setLanguage(language)
        event.reply("Your new guild language is #".translate(guild.internalLanguage(), language ?: "--")).setEphemeral(true).queue()
    }
}


