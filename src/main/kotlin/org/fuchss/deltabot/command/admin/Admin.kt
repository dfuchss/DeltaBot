package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.command.ICommandRegistry
import org.fuchss.deltabot.command.fixCommandPermissions

/**
 * A [BotCommand] that toggles the admin state for a user.
 */
class Admin(private val configuration: BotConfiguration, private val commandRegistry: ICommandRegistry) : GuildCommand {

    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN

    override fun createCommand(guild: Guild): CommandData {
        val command = CommandData("admin", "Op or de-op an user")
        command.addOptions(OptionData(OptionType.USER, "user", "the user that shall be changed").setRequired(true))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val user = event.getOption("user")?.asUser

        if (user == null || user.isBot) {
            event.reply("No valid user was mentioned").setEphemeral(true).queue()
            return
        }

        val nowAdmin = configuration.toggleAdmin(user)
        event.reply("User ${user.asMention} is now ${if (nowAdmin) "an" else "no"} admin").queue()

        // Fix Guild Commands ..
        fixCommandPermissions(event.jda, configuration, commandRegistry::permissions, user)
    }
}
