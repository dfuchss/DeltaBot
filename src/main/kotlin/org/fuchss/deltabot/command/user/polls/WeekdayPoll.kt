package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import net.dv8tion.jda.api.interactions.components.selections.SelectionMenu
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.*

class WeekdayPoll(scheduler: Scheduler) : PollBase("./states/weekday_poll.json", scheduler) {

    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        val command = CommandData("poll-weekday", "create a poll that has the weekdays as options").addOptions()
        command.addOptions(
            OptionData(OptionType.STRING, "question", "your question for the poll").setRequired(true)
        )
        return command
    }

    override fun onEvent(event: GenericEvent) {
        if (event is SelectionMenuEvent && event.selectionMenu?.id == "weekday") {
            handleCreationOfSummon(event)
            return
        }
        super.onEvent(event)
    }

    private fun handleCreationOfSummon(event: SelectionMenuEvent) {
        event.deferEdit().queue()
        val question = event.message.contentRaw.split("\n")[0]
        val weekdays = event.selectedOptions!!.map { o -> o.value }
        val options = getOptions(weekdays)
        createPoll(event.channel, null, event.user, question, options, false)
    }


    override fun handle(event: SlashCommandEvent) {
        val question = event.getOption("question")?.asString ?: ""

        if (question.isBlank()) {
            event.reply("You have to provide a question!".translate(event)).setEphemeral(true).queue()
            return
        }

        val message = "**$question**\n\n" + "*Please choose the Weekdays you want to use*".translate(event)

        val weekdays = SelectionMenu.create("weekday").addOptions(
            Weekday.values().map { v -> SelectOption.of(v.name.translate(event), v.name.translate(event)) }
        ).setMinValues(1).setMaxValues(Weekday.values().size).build()

        val msg = MessageBuilder().setContent(message).setActionRows(ActionRow.of(weekdays)).build()
        event.reply(msg).setEphemeral(true).queue()
    }

    override fun terminate(oldMessage: Message, uid: String) {
        val msg = oldMessage.refresh()
        val data = pollState.getPollData(msg.id)
        pollState.remove(data)
        if (data == null) {
            msg.editMessageComponents(listOf()).complete().hide(directHide = false)
            if (msg.isPinned)
                msg.unpin().complete()
            return
        }

        val user = oldMessage.jda.fetchUser(uid)
        val dayMapping = msg.buttons.associate { b -> b.id!! to b.label }
        val reactionsToUser: Map<String, List<String>> = data.react2User.mapKeys { (k, _) -> dayMapping[k]!! }

        var finalMsg = oldMessage.contentRaw.split("\n")[0] + "\n\n"
        for ((d, users) in reactionsToUser.entries) {
            finalMsg += "$d: ${if (users.isEmpty()) "--" else users.mapNotNull { u -> oldMessage.jda.fetchUser(u)?.asMention }.joinToString(" ")}\n"
        }
        finalMsg += "\n${pollFinished.translate(language(msg.guild, user))}"

        msg.editMessage(finalMsg).setActionRows(listOf()).complete().hide(directHide = false)
        if (msg.isPinned)
            msg.unpin().complete()
    }
}