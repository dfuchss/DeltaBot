package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.command.BotCommand

open class Help(private val configuration: Configuration, protected val commands: List<BotCommand>) : BotCommand {

    override val isAdminCommand: Boolean get() = false
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        return CommandData("help", "Prints a help message")
    }

    override fun handle(event: SlashCommandEvent) {
        val admin = configuration.isAdmin(event.user)
        val commands = commands.sorted().filter { c -> admin || !c.isAdminCommand }
        event.replyEmbeds(generateText(event, commands)).setEphemeral(admin).complete()
    }

    protected fun generateText(event: SlashCommandEvent, commands: List<BotCommand>): MessageEmbed {
        var message = ""
        for (cmd in commands) {
            message += "**/${cmd.name}**: ${cmd.description}\n"
        }
        return EmbedBuilder().setTitle(event.jda.selfUser.name + " Help").setDescription(message.trim()).setColor(Constants.BLUE).build()
    }
}