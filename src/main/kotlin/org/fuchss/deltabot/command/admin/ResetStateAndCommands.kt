package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.utils.extensions.fetchCommands
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.objectpacker.PackerPort
import org.fuchss.objectcasket.objectpacker.port.Session
import java.io.File

/**
 * A [BotCommand] that resets the command states of all bot commands in discord.
 */
class ResetStateAndCommands(
    private val configuration: BotConfiguration,
    private val dbLocation: String,
    private val session: Session
) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(): SlashCommandData = Commands.slash("deinit", "main reset command. resets the states and deletes all registered commands")

    override fun handle(event: SlashCommandInteraction) {
        if (!configuration.isAdmin(event.user)) {
            event.reply("You have to be a global admin of the bot!").setEphemeral(true).queue()
            return
        }

        event.reply("Executing deinit & reset ..").setEphemeral(true).complete()

        // Remove database files
        try {
            PackerPort.PORT.sessionManager().terminate(session)
            File(dbLocation).delete()
            logger.info("Remove database files done")
        } catch (e: Exception) {
            logger.error(e.message)
        }

        // Remove global commands
        try {
            event.jda.fetchCommands().forEach { c -> c.delete().complete() }
            logger.info("Remove global commands done")
        } catch (e: Exception) {
            logger.error(e.message)
        }

        // Remove guild commands
        try {
            event.jda.guilds.forEach { g -> g.fetchCommands().forEach { c -> c.delete().complete() } }
            logger.info("Remove guild commands done")
        } catch (e: Exception) {
            logger.error(e.message)
        }

        event.jda.shutdownNow()

        logger.info("Factory reset done")
    }
}
