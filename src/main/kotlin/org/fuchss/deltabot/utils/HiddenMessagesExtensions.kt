package org.fuchss.deltabot.utils

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import java.time.Duration
import java.time.LocalDateTime


fun initHiddenMessages(jda: JDA, scheduler: Scheduler) {
    if (hiddenMessages != null)
        error("Hidden Messages already initialized")

    hiddenMessages = HiddenMessagesStore().load("./states/hiddenmsg.json").registerScheduler(scheduler)
    jda.addEventListener(hiddenMessages)
}


private var hiddenMessages: HiddenMessagesStore? = null
private const val hideId = "hide-message"

private class HiddenMessagesStore : Storable(), EventListener {

    var messages: MutableList<HiddenMessage> = mutableListOf()

    @Transient
    var scheduler: Scheduler? = null

    override fun onEvent(event: GenericEvent) {
        if (event is MessageDeleteEvent) {
            // Cleanup of hidden messages ..
            val hiddenMessage = findMessage(event) ?: return
            hiddenMessages!!.messages.remove(hiddenMessage)
            hiddenMessages!!.store()
            return
        }

        if (event !is ButtonClickEvent || event.button?.id != hideId)
            return

        val hiddenMessage = findMessage(event.message) ?: return
        handleHiddenMessageClick(event, hiddenMessage)
    }


    fun findMessage(messageDeleteEvent: MessageDeleteEvent): HiddenMessage? {
        val private = messageDeleteEvent.channelType == ChannelType.PRIVATE

        return if (private)
            messages.find { hm -> hm.isPrivateChannel && hm.mid == messageDeleteEvent.messageId && hm.uid == (messageDeleteEvent.channel as PrivateChannel).user.id }
        else
            messages.find { hm -> !hm.isPrivateChannel && hm.mid == messageDeleteEvent.messageId && hm.cid == messageDeleteEvent.channel.id && hm.gid == messageDeleteEvent.guild.id }
    }

    fun findMessage(message: Message?): HiddenMessage? {
        if (message == null)
            return null

        val private = message.channelType == ChannelType.PRIVATE

        return if (private)
            messages.find { hm -> hm.isPrivateChannel && hm.mid == message.id && hm.uid == (message.channel as PrivateChannel).user.id }
        else
            messages.find { hm -> !hm.isPrivateChannel && hm.mid == message.id && hm.cid == message.channel.id && hm.gid == message.guild.id }
    }

    private fun handleHiddenMessageClick(event: ButtonClickEvent, hiddenMessage: HiddenMessage) {
        event.deferEdit().complete()
        if (hiddenMessage.hidden)
            unhideMessage(scheduler, event.message!!, hiddenMessage)
        else
            hideMessage(event.message!!, hiddenMessage)
    }

    fun registerScheduler(scheduler: Scheduler): HiddenMessagesStore {
        this.scheduler = scheduler
        return this
    }
}

private const val maxContent = 24

fun Message.hide(directHide: Boolean = true): Message {
    val rawMessage = this.channel.retrieveMessageById(this.id).complete()
    if (rawMessage.buttons.isNotEmpty()) {
        error("There shall be no buttons!")
    }

    if (hiddenMessages == null)
        error("Hidden Messages are not initialized")

    val hiddenMessage = hiddenMessages!!.findMessage(rawMessage)
    if (hiddenMessage == null) {
        createHiddenMessage(rawMessage)
    }

    if (directHide) {
        return hideMessage(rawMessage, hiddenMessages!!.findMessage(rawMessage)!!)
    }

    return rawMessage
}


fun Message.unhide(): Message {
    if (hiddenMessages == null)
        error("Hidden Messages are not initialized")

    val hiddenMessage = hiddenMessages!!.findMessage(this) ?: return this
    unhideMessage(hiddenMessages!!.scheduler, this, hiddenMessage)
    hiddenMessages!!.messages.remove(hiddenMessage)
    hiddenMessages!!.store()
    return this
}

private fun createHiddenMessage(message: Message) {
    val hiddenMessage = if (message.channelType == ChannelType.PRIVATE) {
        HiddenMessage("", (message.channel as PrivateChannel).user.id, "", message.id, true, message.contentRaw, false)
    } else {
        HiddenMessage(message.guild.id, "", message.channel.id, message.id, false, message.contentRaw, false)
    }

    hiddenMessages!!.messages.add(hiddenMessage)
    hiddenMessages!!.store()

    val hideButton = Button.of(ButtonStyle.SECONDARY, hideId, "Details", ":arrow_down_small:".toEmoji())
    message.editMessageComponents(ActionRow.of(hideButton)).complete()
}

private fun hideMessage(message: Message, hiddenMessage: HiddenMessage): Message {
    if (hiddenMessage.hidden)
        return message

    val firstLine = message.contentDisplay.split("\n")[0]
    val newContent = (if (firstLine.length > maxContent) firstLine.substring(0, maxContent) else firstLine) + "..."
    val edited = message.editMessage(newContent).complete()
    hiddenMessage.hidden = true
    hiddenMessages!!.store()
    return edited
}


private fun unhideMessage(scheduler: Scheduler?, message: Message, hiddenMessage: HiddenMessage) {
    if (!hiddenMessage.hidden)
        return

    message.editMessage(hiddenMessage.content).complete()
    hiddenMessage.hidden = false
    hiddenMessages!!.store()
    scheduler?.queue({ hideMessage(message, hiddenMessage) }, (LocalDateTime.now() + Duration.ofSeconds(30)).timestamp())
}


private data class HiddenMessage(
    var gid: String,
    var uid: String,
    var cid: String,
    var mid: String,
    var isPrivateChannel: Boolean,
    var content: String,
    var hidden: Boolean
)

