package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.ICommandRegistry
import org.fuchss.deltabot.command.user.Help
import org.fuchss.deltabot.utils.extensions.fetchCommands
import org.fuchss.objectcasket.port.Session

/**
 * A [BotCommand] that prints a persistent help message.
 */
class PersistentHelp(configuration: BotConfiguration, session: Session, registry: ICommandRegistry) : Help(configuration, session, registry) {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(): SlashCommandData {
        return Commands.slash("help-persist", "Prints a help message that will be persisted")
    }

    override fun handle(event: SlashCommandInteraction) {
        var commands = if (event.isFromGuild) event.guild!!.fetchCommands() + event.jda.fetchCommands() else event.jda.fetchCommands()
        commands = commands.filter { c -> registry.permissions(c) == CommandPermissions.ALL }
        val botName = event.guild?.selfMember?.effectiveName ?: event.jda.selfUser.name
        event.replyEmbeds(generateText(botName, commands)).queue()
    }
}
