package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.user.Help

class PersistentHelp(configuration: Configuration, commands: List<BotCommand>) : Help(configuration, commands) {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(): CommandData {
        return CommandData("help-persist", "Prints a help message that will be persisted")
    }

    override fun handle(event: SlashCommandEvent) {
        val commands = commands.filter { c -> c.permissions == CommandPermissions.ALL }.sorted()
        event.replyEmbeds(generateText(event.jda, commands)).queue()
    }
}