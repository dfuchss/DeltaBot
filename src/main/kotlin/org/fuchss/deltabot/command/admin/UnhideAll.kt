package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.extensions.unhideAll

class UnhideAll : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ADMIN
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("unhide-all", "unhide all hidden messages")
    }

    override fun handle(event: SlashCommandEvent) {
        event.reply("Unhiding all messages ..").setEphemeral(true).complete()
        val thread = Thread { unhideAll(event.jda) }
        thread.isDaemon = true
        thread.start()

    }
}