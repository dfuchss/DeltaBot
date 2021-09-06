package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import java.util.function.BiConsumer

class InitialAdminCommand(private val configuration: Configuration, private val adminAddedCallback: BiConsumer<JDA, User>) : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData = CommandData("initial-admin", "create an initial admin user")

    override fun handle(event: SlashCommandEvent) {
        if (configuration.getAdmins(event.jda).isNotEmpty()) {
            event.reply("This shall not happen .. an admin already exist ..").setEphemeral(true).queue()
            return
        }

        configuration.toggleAdmin(event.user)
        event.reply("I've added you as admin").setEphemeral(true).queue()
        adminAddedCallback.accept(event.jda, event.user)
    }
}