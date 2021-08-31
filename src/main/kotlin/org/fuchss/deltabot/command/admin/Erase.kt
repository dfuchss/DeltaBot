package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.command.BotCommand

class Erase : BotCommand {
    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        return CommandData("erase", "erase all content of a channel")
    }

    override fun handle(event: SlashCommandEvent) {
        val reply = event.reply("Starting the deletion of all messages ...").setEphemeral(true).complete()

        val retrieveMax = 10

        try {
            var history = event.channel.history.retrievePast(retrieveMax).complete()
            while (history.isNotEmpty()) {
                for (m in history) {
                    m.delete().complete()
                }
                history = event.channel.history.retrievePast(retrieveMax).complete()
            }
        } catch (e: Exception) {
            reply.editOriginal("An error occured while deleting messages: ${e.message}").complete()
        }
    }
}