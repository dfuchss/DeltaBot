package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.fuchss.deltabot.utils.extensions.*

class PollAdmin : EventListener, IPollAdmin {
    companion object {
        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()
        private val refresh = ":cyclone:".toEmoji()

        private const val pollTypeLine = "PollType: "
        private const val pollIdLine = "PollId: "
    }

    private val pollXmanager: MutableMap<String, IPollBase> = mutableMapOf()

    private fun handleAction(event: ButtonClickEvent) {
        if (event.message.author != event.jda.selfUser) {
            return
        }

        val buttonId = event.button?.id ?: ""
        if (buttonId !in listOf(finish, delete, refresh).map { e -> e.name })
            return

        val rawMessage = event.message.contentRaw.lines()
        if (rawMessage.size < 2)
            return

        val type = if (rawMessage[0].startsWith(pollTypeLine)) rawMessage[0].substring(pollTypeLine.length).trim() else ""
        val mid = if (rawMessage[1].startsWith(pollIdLine)) rawMessage[1].substring(pollIdLine.length).trim() else ""

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
            else -> logger.error("ButtonId was $buttonId, but no method is registered!")
        }
    }

    override fun createAdminArea(reply: InteractionHook, data: Poll) {
        var message = ""
        message += "$pollTypeLine${data.pollType}\n"
        message += "$pollIdLine${data.mid}\n\n"
        message += "This is the Admin Area of the Poll. Feel free to do what you want :)".translate(reply.interaction.user.internalLanguage())

        val globalActions = listOf( //
            Button.of(ButtonStyle.SECONDARY, finish.name + "", "Finish", finish),  //
            Button.of(ButtonStyle.SECONDARY, delete.name, "Delete", delete), //
            Button.of(ButtonStyle.SECONDARY, refresh.name, "Refresh", refresh) //
        )
        reply.editOriginal(message).setActionRows(globalActions.toActionRows()).queue()
    }

    override fun register(pollType: String, manager: IPollBase) {
        pollXmanager[pollType] = manager
    }
 
    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonClickEvent)
            return
        handleAction(event)
    }
}
