package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.utils.extensions.translate

class Emojify : GlobalCommand {
    companion object {
        private val ValidSpecialChars = listOf(' ', '!', '?')
    }

    override val permissions: CommandPermissions get() = CommandPermissions.ALL
 
    override fun createCommand(): CommandData {
        val command = CommandData("emojify", "\'emojify' a text")
        command.addOptions(OptionData(OptionType.STRING, "text", "the text you want to \'emojify\'").setRequired(true))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val rawText = (event.getOption("text")?.asString ?: "").lowercase()
        val text = rawText.mapNotNull { c -> c.takeIf { (it in 'a'..'z') || (it in '0'..'9') || it in ValidSpecialChars } }.joinToString("")

        if (text.isBlank()) {
            event.reply("You should provide at least one valid letter [A..Za..z0..9] :)".translate(event)).queue()
            return
        }
        val reply = event.deferReply().complete()
        val emojis = text.map { l -> emojify(l) }.joinToString("")
        reply.editOriginal("> $emojis").queue()
    }

    private fun emojify(letter: Char): String = when (letter) {
        ' ' -> " "
        '?' -> ":question:"
        '!' -> ":exclamation:"
        '0' -> ":zero:"
        '1' -> ":one:"
        '2' -> ":two:"
        '3' -> ":three:"
        '4' -> ":four:"
        '5' -> ":five:"
        '6' -> ":six:"
        '7' -> ":seven:"
        '8' -> ":eight:"
        '9' -> ":nine:"
        else -> ":regional_indicator_${letter.lowercase()}:"
    }
}