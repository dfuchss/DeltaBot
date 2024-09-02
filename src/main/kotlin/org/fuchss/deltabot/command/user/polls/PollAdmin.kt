package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import org.fuchss.deltabot.utils.extensions.internalLanguage
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.toActionRows
import org.fuchss.deltabot.utils.extensions.toEmoji
import org.fuchss.deltabot.utils.extensions.translate

class PollAdmin :
    EventListener,
    IPollAdmin {
    companion object {
        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()
        private val refresh = ":cyclone:".toEmoji()

        private val postpones = listOf(":clock6" to -60, ":clock3:" to 15, ":clock12:" to 60)

        private const val POLL_TYPE_LINE = "PollType: "
        private const val POLL_ID_LINE = "PollId: "
    }

    private val pollXmanager: MutableMap<String, IPollBase> = mutableMapOf()

    private fun handleAction(event: ButtonInteractionEvent) {
        if (event.message.author != event.jda.selfUser) {
            return
        }

        val buttonId = event.button.id ?: ""
        if (buttonId !in (listOf(finish, delete, refresh).map { it.name } + postpones.map { it.first })) {
            return
        }

        val rawMessage = event.message.contentRaw.lines()
        if (rawMessage.size < 2) {
            return
        }

        val type = if (rawMessage[0].startsWith(POLL_TYPE_LINE)) rawMessage[0].substring(POLL_TYPE_LINE.length).trim() else ""
        val mid = if (rawMessage[1].startsWith(POLL_ID_LINE)) rawMessage[1].substring(POLL_ID_LINE.length).trim() else ""

        if (type.isEmpty() || mid.isEmpty() || !pollXmanager.containsKey(type)) {
            event.reply("Message or Type not found".translate(event)).setEphemeral(true).queue()
            return
        }

        val handler = pollXmanager[type]!!
        if (!handler.isOwner(event, mid)) {
            return
        }

        event.deferEdit().queue()
        when (buttonId) {
            finish.name -> handler.terminate(event.jda, event.user, mid)
            delete.name -> handler.removePoll(event.jda, event.user, mid)
            refresh.name -> handler.refreshPoll(event.jda, event.user, mid)
            in postpones.map { it.first } -> handler.postpone(event.jda, event.user, mid, postpones.toMap()[buttonId]!!)
            else -> logger.error("ButtonId was $buttonId, but no method is registered!")
        }
    }

    override fun createAdminArea(
        reply: InteractionHook,
        data: Poll
    ) {
        var message = ""
        message += "$POLL_TYPE_LINE${data.pollType}\n"
        message += "$POLL_ID_LINE${data.mid}\n\n"
        message += "This is the Admin Area of the Poll. Feel free to do what you want :)".translate(reply.interaction.user.internalLanguage())

        val globalActions =
            mutableListOf(
                Button.of(ButtonStyle.SECONDARY, finish.name + "", "Finish", finish),
                Button.of(ButtonStyle.SECONDARY, delete.name, "Delete", delete),
                Button.of(ButtonStyle.SECONDARY, refresh.name, "Refresh", refresh)
            )

        if (data.timestamp != null) {
            for ((buttonId, minutes) in postpones) {
                globalActions += Button.of(ButtonStyle.SECONDARY, buttonId, "${if (minutes >= 0) "+" else ""}$minutes min", buttonId.toEmoji())
            }
        }

        reply.editOriginal(message).setComponents(globalActions.toActionRows(3)).queue()
    }

    override fun register(
        pollType: String,
        manager: IPollBase
    ) {
        pollXmanager[pollType] = manager
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonInteractionEvent) {
            return
        }
        handleAction(event)
    }
}
