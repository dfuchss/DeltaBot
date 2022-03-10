package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.selections.SelectOption
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.Weekday
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.objectcasket.port.Session

/**
 * A [Poll][PollBase] that provides polls for weekdays.
 */
class WeekdayPoll(pollAdmin: IPollAdmin, scheduler: Scheduler, session: Session) : PollBase(pollAdmin, "weekday", scheduler, session) {

    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("poll-weekday", "create a poll that has the weekdays as options").addOptions()
        command.addOptions(
            OptionData(OptionType.STRING, "question", "your question for the poll").setRequired(true)
        )
        return command
    }

    override fun onEvent(event: GenericEvent) {
        if (event is SelectMenuInteractionEvent && event.selectMenu.id == "weekday") {
            handleCreationOfSummon(event)
            return
        }
        super.onEvent(event)
    }

    private fun handleCreationOfSummon(event: SelectMenuInteractionEvent) {
        val hook = event.deferEdit().complete()
        val question = event.message.contentRaw.split("\n")[0]
        val weekdays = event.selectedOptions.map { o -> o.value }
        val options = getOptions(weekdays)
        createPoll(hook, null, event.user, question, options, false)
    }

    override fun handle(event: SlashCommandInteraction) {
        val question = event.getOption("question")?.asString ?: ""

        if (question.isBlank()) {
            event.reply("You have to provide a question!".translate(event)).setEphemeral(true).queue()
            return
        }

        val message = "**$question**\n\n" + "*Please choose the Weekdays you want to use*".translate(event)

        val weekdays = SelectMenu.create("weekday").addOptions(
            Weekday.values().map { v -> SelectOption.of(v.name.translate(event), v.name.translate(event)) }
        ).setMinValues(1).setMaxValues(Weekday.values().size).build()

        val msg = MessageBuilder().setContent(message).setActionRows(ActionRow.of(weekdays)).build()
        event.reply(msg).queue()
    }
}
