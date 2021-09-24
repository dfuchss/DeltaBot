package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.fetchCommands
import org.fuchss.deltabot.utils.logger
import java.io.File

class ResetStateAndCommands(private val configuration: Configuration) : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("deinit", "main reset command. resets the states and deletes all registered commands")
    }

    override fun handle(event: SlashCommandEvent) {
        if (!configuration.isAdmin(event.user)) {
            event.reply("You have to be a global admin of the bot!").setEphemeral(true).queue()
            return
        }

        event.reply("Executing deinit & reset ..").setEphemeral(true).queue()

        // Remove state files
        try {
            val states = File("./states")
            states.listFiles()?.forEach { f -> f.exists() && f.isFile && f.delete() }
            logger.info("Remove state files done")
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