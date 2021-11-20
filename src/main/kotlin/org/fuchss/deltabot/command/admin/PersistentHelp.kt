package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.ICommandRegistry
import org.fuchss.deltabot.command.user.Help
import org.fuchss.deltabot.utils.extensions.fetchCommands

/**
 * A [BotCommand] that prints a persistent help message.
 */
class PersistentHelp(configuration: BotConfiguration, registry: ICommandRegistry) : Help(configuration, registry) {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(): CommandData {
        return CommandData("help-persist", "Prints a help message that will be persisted")
    }

    override fun handle(event: SlashCommandEvent) {
        var commands = if (event.isFromGuild) event.guild!!.fetchCommands() + event.jda.fetchCommands() else event.jda.fetchCommands()
        commands = commands.filter { c -> registry.permissions(c) == CommandPermissions.ALL }
        val botName = event.guild?.selfMember?.effectiveName ?: event.jda.selfUser.name
        event.replyEmbeds(generateText(botName, commands)).queue()
    }
}