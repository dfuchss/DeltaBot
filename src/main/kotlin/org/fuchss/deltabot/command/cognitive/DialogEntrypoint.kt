package org.fuchss.deltabot.command.cognitive

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.utils.logger

class DialogEntrypoint(private val configuration: Configuration) : BotCommand {
    override val isAdminCommand: Boolean get() = false
    override val isGlobal: Boolean get() = true

    private val rasa: RasaService = RasaService(configuration)

    override fun createCommand(): CommandData {
        val command = CommandData("say", "say something to the bot")
        command.addOptions(
            OptionData(OptionType.STRING, "text", "the text you want to say").setRequired(true)
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        if (configuration.disableNlu) {
            event.reply("NLU is currently disabled").setEphemeral(true).complete()
            return
        }

        val msg = event.getOption("text")?.asString
        if (msg == null) {
            event.reply("I need text for my interpretations ..").setEphemeral(true).complete()
            return
        }

        val response = rasa.recognize(msg)
        logger.debug("RASA Response: $response")

        // TODO Handle it ..
        event.reply("The dialog system is currently work in progress .. please ask my admin for further information").setEphemeral(true).complete()
    }
}