package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.user.Help

/**
 * A [BotCommand] that prints a persistent help message.
 */
class PersistentHelp(configuration: BotConfiguration, commands: List<BotCommand>) : Help(configuration, commands) {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(): CommandData {
        return CommandData("help-persist", "Prints a help message that will be persisted")
    }

    override fun handle(event: SlashCommandEvent) {
        val commands = commands.filter { c -> c.permissions == CommandPermissions.ALL }.sorted()
        val botName = event.guild?.selfMember?.effectiveName ?: event.jda.selfUser.name
        event.replyEmbeds(generateText(botName, commands)).queue()
    }
}