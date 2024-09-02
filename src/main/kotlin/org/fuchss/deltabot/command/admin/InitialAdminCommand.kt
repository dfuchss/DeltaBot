package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import java.util.function.BiConsumer

/**
 * A [BotCommand] that performs the task of creation of an initial admin user.
 */
class InitialAdminCommand(
    private val configuration: BotConfiguration,
    private val adminAddedCallback: BiConsumer<JDA, User>
) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    override fun createCommand(): SlashCommandData = Commands.slash("initial-admin", "create an initial admin user")

    override fun handle(event: SlashCommandInteraction) {
        if (configuration.hasAdmins()) {
            event.reply("This shall not happen .. an admin already exist ..").setEphemeral(true).queue()
            return
        }

        configuration.toggleAdmin(event.user)
        event.reply("I've added you as admin").setEphemeral(true).queue()
        adminAddedCallback.accept(event.jda, event.user)
    }
}
