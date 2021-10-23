package org.fuchss.deltabot.utils.extensions

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.PrivateChannel
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.timestamp
import org.fuchss.objectcasket.port.Session
import java.time.Duration
import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id


private val hiddenMessages = HiddenMessageManager()


fun initHiddenMessages(jda: JDA, scheduler: Scheduler, session: Session) {
    if (hiddenMessages.isInitialized())
        error("HM Manager already initialized")
    hiddenMessages.init(jda, session, scheduler)
}

fun unhideAll(jda: JDA) {
    if (!hiddenMessages.isInitialized())
        error("Hidden Messages are not initialized")
    hiddenMessages.unhideAll(jda)
}

private const val hideId = "hide-message"
private val hideEmote = ":arrow_down_small:".toEmoji()

private const val maxContent = 24

fun Message.hide(directHide: Boolean = true): Message {
    val rawMessage = this.channel.retrieveMessageById(this.id).complete()
    if (rawMessage.buttons.isNotEmpty()) {
        error("There shall be no buttons!")
    }

    if (!hiddenMessages.isInitialized())
        error("Hidden Messages are not initialized")

    val hiddenMessage = hiddenMessages.findMessage(rawMessage)
    if (hiddenMessage == null) {
        createHiddenMessage(rawMessage)
    }

    if (directHide) {
        return hideMessage(rawMessage, hiddenMessages.findMessage(rawMessage)!!)
    }

    return rawMessage
}


fun Message.unhide(): Message {
    if (!hiddenMessages.isInitialized())
        error("Hidden Messages are not initialized")

    val hiddenMessage = hiddenMessages.findMessage(this) ?: return this
    unhideMessage(null, this, hiddenMessage)
    this.editMessageComponents().queue()
    hiddenMessages.removeHM(hiddenMessage)
    return this
}

private fun createHiddenMessage(message: Message) {
    val hiddenMessage = if (message.channelType == ChannelType.PRIVATE) {
        HiddenMessage("", (message.channel as PrivateChannel).user.id, "", message.id, true, message.contentRaw, false)
    } else {
        HiddenMessage(message.guild.id, "", message.channel.id, message.id, false, message.contentRaw, false)
    }

    hiddenMessages.addHM(hiddenMessage)

    val hideButton = Button.of(ButtonStyle.SECONDARY, hideId, "Details", hideEmote)
    message.editMessageComponents(ActionRow.of(hideButton)).complete()
}

private fun hideMessage(message: Message, hiddenMessage: HiddenMessage): Message {
    if (hiddenMessage.hidden)
        return message

    val firstLine = message.contentDisplay.split("\n")[0]
    val newContent = (if (firstLine.length > maxContent) firstLine.substring(0, maxContent) else firstLine) + "..."
    val edited = message.editMessage(newContent).complete()
    hiddenMessage.hidden = true
    hiddenMessages.persist(hiddenMessage)
    return edited
}


private fun unhideMessage(scheduler: Scheduler?, message: Message, hiddenMessage: HiddenMessage) {
    if (!hiddenMessage.hidden)
        return

    message.editMessage(hiddenMessage.content).complete()
    hiddenMessage.hidden = false
    hiddenMessages.persist(hiddenMessage)
    scheduler?.queue({ hideMessage(message, hiddenMessage) }, (LocalDateTime.now() + Duration.ofSeconds(30)).timestamp())
}

private class HiddenMessageManager : EventListener {
    private var session: Session? = null
    private var scheduler: Scheduler? = null
    private val hiddenMessagesData: MutableList<HiddenMessage> = mutableListOf()

    fun init(jda: JDA, session: Session, scheduler: Scheduler) {
        val dbHiddenMessages = session.getAllObjects(HiddenMessage::class.java)
        hiddenMessagesData.addAll(dbHiddenMessages)
        this.scheduler = scheduler
        this.session = session
        jda.addEventListener(this)
    }

    fun isInitialized(): Boolean = scheduler != null && session != null

    fun findMessage(message: Message?): HiddenMessage? {
        if (message == null)
            return null
        val private = message.channelType == ChannelType.PRIVATE

        return if (private)
            hiddenMessagesData.find { hm -> hm.isPrivateChannel && hm.mid == message.id && hm.uid == (message.channel as PrivateChannel).user.id }
        else
            hiddenMessagesData.find { hm -> !hm.isPrivateChannel && hm.mid == message.id && hm.cid == message.channel.id && hm.gid == message.guild.id }
    }

    fun findMessage(messageDeleteEvent: MessageDeleteEvent): HiddenMessage? {
        val private = messageDeleteEvent.channelType == ChannelType.PRIVATE

        return if (private)
            hiddenMessagesData.find { hm -> hm.isPrivateChannel && hm.mid == messageDeleteEvent.messageId && hm.uid == (messageDeleteEvent.channel as PrivateChannel).user.id }
        else
            hiddenMessagesData.find { hm -> !hm.isPrivateChannel && hm.mid == messageDeleteEvent.messageId && hm.cid == messageDeleteEvent.channel.id && hm.gid == messageDeleteEvent.guild.id }
    }

    override fun onEvent(event: GenericEvent) {
        if (event is MessageDeleteEvent) {
            // Cleanup of hidden messages ..
            val hiddenMessage = findMessage(event) ?: return
            removeHM(hiddenMessage)
            return
        }

        if (event is MessageReactionAddEvent) {
            checkForHide(event)
            return
        }

        if (event !is ButtonClickEvent || event.button?.id != hideId)
            return

        val hiddenMessage = findMessage(event.message) ?: return
        handleHiddenMessageClick(event, hiddenMessage)
    }

    private fun handleHiddenMessageClick(event: ButtonClickEvent, hiddenMessage: HiddenMessage) {
        event.deferEdit().complete()
        if (hiddenMessage.hidden)
            unhideMessage(scheduler, event.message, hiddenMessage)
        else
            hideMessage(event.message, hiddenMessage)
    }

    private fun checkForHide(event: MessageReactionAddEvent) {
        if (!isInitialized())
            return

        if (!event.reactionEmote.isEmoji || event.reactionEmote.emoji != hideEmote.name)
            return

        val msg = event.retrieveMessage().complete()
        if (msg.author.id != event.jda.selfUser.id)
            return

        val hidden = findMessage(msg)

        if (hidden != null)
            msg.unhide()
        else
            msg.hide()

        msg.clearReactions().queue()

    }

    fun addHM(hiddenMessage: HiddenMessage) {
        if (!isInitialized())
            error("Hidden Messages are not initialized")
        session!!.persist(hiddenMessage)
        hiddenMessagesData.add(hiddenMessage)
    }

    fun removeHM(hiddenMessage: HiddenMessage) {
        if (!isInitialized())
            error("Hidden Messages are not initialized")
        session!!.delete(hiddenMessage)
        hiddenMessagesData.remove(hiddenMessage)
    }

    fun persist(hiddenMessage: HiddenMessage) {
        if (!isInitialized())
            error("Hidden Messages are not initialized")
        session!!.persist(hiddenMessage)
    }

    fun unhideAll(jda: JDA) {
        val messages = hiddenMessagesData.toList()
        messages.forEach { hm ->
            run {
                try {
                    val msg = if (hm.isPrivateChannel) {
                        val channel = jda.openPrivateChannelById(hm.uid).complete()
                        channel!!.retrieveMessageById(hm.mid).complete()
                    } else {
                        val channel = jda.fetchTextChannel(hm.gid, hm.cid)
                        channel!!.retrieveMessageById(hm.mid).complete()
                    }
                    msg!!.unhide()
                } catch (e: Exception) {
                    logger.error(e.message)
                }
            }
        }
    }
}

@Entity
class HiddenMessage {
    @Id
    @GeneratedValue
    var id: Int? = null
    var gid: String = ""
    var uid: String = ""
    var cid: String = ""
    var mid: String = ""
    var isPrivateChannel: Boolean = false
    var content: String = ""
    var hidden: Boolean = false

    constructor()

    constructor(gid: String, uid: String, cid: String, mid: String, isPrivateChannel: Boolean, content: String, hidden: Boolean) {
        this.gid = gid
        this.uid = uid
        this.cid = cid
        this.mid = mid
        this.isPrivateChannel = isPrivateChannel
        this.content = content
        this.hidden = hidden
    }
}
