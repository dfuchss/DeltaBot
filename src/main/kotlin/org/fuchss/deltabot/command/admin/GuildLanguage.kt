package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.internalLanguage
import org.fuchss.deltabot.utils.extensions.setLanguage
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.deltabot.utils.extensions.withFirst

/**
 * A [BotCommand] that changes the [Language] of a [Guild].
 */
class GuildLanguage : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("guild-language", "set the bot language of your guild")
        command.addOptions(
            OptionData(OptionType.STRING, "lang", "your language").setRequired(true).addChoices(
                Language.values().map { l -> Command.Choice(l.toString(), l.locale) }.withFirst(Command.Choice("None", "None"))
            )
        )
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
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
