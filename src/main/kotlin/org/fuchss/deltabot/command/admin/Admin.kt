package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.fixCommandPermissions
import org.fuchss.deltabot.translate

class Admin(private val configuration: Configuration, private val commands: List<BotCommand>) : BotCommand {

    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        val command = CommandData("admin", "Op or de-op an user")
        command.addOptions(OptionData(OptionType.USER, "user", "the user that shall be changed").setRequired(true))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        if (!configuration.isAdmin(event.user)) {
            event.reply("You are not a global admin!".translate(event)).setEphemeral(true).complete()
            return
        }


        val user = event.getOption("user")?.asUser

        if (user == null || user.isBot) {
            event.reply("No valid user was mentioned").setEphemeral(true).complete()
            return
        }

        val nowAdmin = configuration.toggleAdmin(user)
        event.reply("User ${user.asMention} is now ${if (nowAdmin) "an" else "no"} admin").complete()

        // Fix Guild Commands ..
        fixCommandPermissions(event.jda, configuration, commands, user)
    }
}