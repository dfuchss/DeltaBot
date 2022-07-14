package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.News as NewsDialog

class News : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override fun createCommand(): SlashCommandData {
        val command = Commands.slash("news", "get current news from rss feeds")
        command.addOptions(OptionData(OptionType.STRING, "topic", "the topics you want news for").setRequired(false).addChoices(NewsDialog.providers.keys.map { Command.Choice(it, it) }))
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
        val reply = event.deferReply().complete()

        val topic = event.getOption("topic")?.asString
        val topics = NewsDialog.providers.keys.toMutableList()
        if (topic != null) topics.removeIf { it != topic }

        val messages = NewsDialog.createNewsMessage(topics)
        messages.forEach { reply.sendMessage(it).queue() }
    }
}
