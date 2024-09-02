package org.fuchss.deltabot.command

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * The command handler of the bot.
 * @param[configuration] the configuration of the bot
 * @param[session] the session (DB) of the bot
 * @param[commandRegistry] the registry of all commands
 */
class CommandHandler(
    private val configuration: BotConfiguration,
    private val session: Session,
    private val commandRegistry: ICommandRegistry
) : EventListener {
    private var nameToCommand: Map<String, BotCommand> = commandRegistry.nameToCommand()

    init {
        commandRegistry.registerUpdateHook { nameToCommand = commandRegistry.nameToCommand() }
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is SlashCommandInteraction) {
            return
        }
        handleSlashCommand(event)
    }

    private fun handleSlashCommand(event: SlashCommandInteraction) {
        logger.debug(event.toString())
        val command = nameToCommand[event.name] ?: UnknownCommand()

        if (command.permissions == CommandPermissions.ADMIN && !configuration.isAdmin(event.user)) {
            event.reply("You are not an admin!").setEphemeral(true).queue()
            return
        }

        if (event.guild != null && command.permissions == CommandPermissions.GUILD_ADMIN && !isGuildAdmin(event, configuration, session)) {
            event.reply("You are not an admin!").setEphemeral(true).queue()
            return
        }

        command.handle(event)
    }

    private class UnknownCommand : GlobalCommand {
        override val permissions: CommandPermissions get() = CommandPermissions.ALL

        override fun createCommand(): SlashCommandData = error("Command shall only be used internally")

        override fun handle(event: SlashCommandInteraction) {
            event.reply("Unknown command .. please contact the admin of the bot!").setEphemeral(true).complete()
        }
    }
}
