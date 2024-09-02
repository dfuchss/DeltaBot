package org.fuchss.deltabot.command.user.polls

import com.vdurmont.emoji.EmojiManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.CustomEmoji
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.ItemComponent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchMessage
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.hide
import org.fuchss.deltabot.utils.extensions.language
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.pinAndDelete
import org.fuchss.deltabot.utils.extensions.toActionRows
import org.fuchss.deltabot.utils.extensions.toEmoji
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * A base for [BotCommands][BotCommand] that create / handles polls.
 * @param[pollType] the type of the poll (simply a universal id of the class)
 * @param[scheduler] the scheduler instance for the poll
 */
abstract class PollBase(
    private val pollAdmin: IPollAdmin,
    private val pollType: String,
    protected val scheduler: Scheduler,
    protected val session: Session
) : GuildCommand,
    EventListener,
    IPollBase {
    companion object {
        private val admin = ":crown:".toEmoji()

        /**
         * The text that will be used to indicate the end of a poll.
         */
        @JvmStatic
        protected val pollFinished = "*Poll finished. You can't vote anymore :)*"
    }

    /**
     * The poll state of this type of poll.
     */
    protected val polls: MutableSet<Poll> = mutableSetOf()

    init {
        @Suppress("LeakingThis")
        pollAdmin.register(pollType, this)
    }

    // IPollBase
    override fun terminate(
        jda: JDA,
        user: User,
        mid: String
    ) {
        val data = polls.find { p -> p.mid == mid } ?: return
        terminate(data, jda, user.id)
    }

    override fun removePoll(
        jda: JDA,
        user: User,
        mid: String
    ) {
        val data = polls.find { p -> p.mid == mid } ?: return
        removePollFromDB(data)
        jda
            .getGuildById(data.gid)
            ?.fetchMessage(data.cid, data.mid)
            ?.delete()
            ?.queue()
    }

    override fun refreshPoll(
        jda: JDA,
        user: User,
        mid: String
    ) {
        val data = polls.find { p -> p.mid == mid } ?: return
        val message = jda.getGuildById(data.gid)?.fetchMessage(data.cid, data.mid) ?: return
        refreshPoll(message, data)
    }

    override fun postpone(
        jda: JDA,
        user: User,
        mid: String,
        minutes: Int
    ) {
        val data = polls.find { p -> p.mid == mid } ?: return

        data.timestamp = data.timestamp!! + (60 * minutes)
        savePollToDB(data)
        scheduler.reschedule(mid, data.timestamp!!)

        val message = jda.getGuildById(data.gid)?.fetchMessage(data.cid, data.mid) ?: return
        var rawMessage = message.contentRaw
        val timeRegex = Regex("<\\w:\\d+:\\w>")
        val time = timeRegex.find(rawMessage)!!.value
        val newTimeValue = time.substring(3, time.length - 3).toLong() + (60 * minutes)
        val newTime = "<${time[1]}:$newTimeValue:${time.reversed()[1]}>"
        rawMessage = rawMessage.replaceFirst(time, newTime)

        message.editMessage(rawMessage).queue()
    }

    override fun isOwner(
        event: ButtonInteractionEvent,
        mid: String
    ): Boolean {
        val data = polls.find { p -> p.mid == mid }
        if (data == null) {
            event.reply("Poll was not found!".translate(event)).setEphemeral(true).queue()
            return false
        }
        return isOwner(event, data)
    }

    final override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
        initPolls()
        initScheduler(jda)
    }

    private fun initPolls() {
        val dbPolls = session.getAllObjects(Poll::class.java).filter { p -> p.pollType == pollType }
        logger.info("Loaded ${dbPolls.size} polls from DB for ${this.javaClass.simpleName}")
        dbPolls.forEach { it.afterCreation() }
        polls.addAll(dbPolls)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonInteractionEvent) {
            return
        }
        val data = polls.find { p -> p.mid == event.messageId } ?: return
        handleButtonEvent(event, data)
    }

    private fun initScheduler(jda: JDA) {
        for (update in polls) {
            if (update.timestamp != null) {
                scheduler.queue(update.mid, { createTermination(jda, update) }, update.timestamp!!)
            }
        }
    }

    private fun createTermination(
        jda: JDA,
        update: Poll
    ) {
        try {
            terminate(update, jda, update.uid)
        } catch (e: Exception) {
            logger.error(e.message, e)
            removePollFromDB(update)
        }
    }

    private fun handleButtonEvent(
        event: ButtonInteractionEvent,
        data: Poll
    ) {
        val buttonId = event.button.id ?: ""

        if (admin.name == buttonId) {
            if (!isOwner(event, data)) {
                return
            }

            val reply = event.deferReply(true).complete()
            pollAdmin.createAdminArea(reply, data)
            return
        }

        event.deferEdit().queue()

        updateUser(event.user.id, buttonId, data)
        savePollToDB(data)

        recreateMessage(event.message, data)
    }

    private fun updateUser(
        uid: String,
        buttonId: String,
        data: Poll
    ) {
        val reactions = data.user2ReactData[uid] ?: listOf()

        if (data.onlyOneOption) {
            // Only one option allowed
            reactions.forEach { r -> data.removeReact2User(r, uid) }
            data.removeUser2React(uid)

            if (buttonId !in reactions) {
                data.setUser2React(uid, mutableListOf(buttonId))
                data.addReact2User(buttonId, uid)
            }
        } else {
            // Multiple Options allowed
            if (buttonId in reactions) {
                // Remove reaction ..
                data.removeUser2React(uid, buttonId)
                data.removeReact2User(buttonId, uid)
            } else {
                // Add reaction ..
                data.addUser2React(uid, buttonId)
                data.addReact2User(buttonId, uid)
            }
        }
        data.cleanup()
    }

    private fun isOwner(
        event: ButtonInteractionEvent,
        data: Poll
    ): Boolean {
        if (data.uid == event.user.id) {
            return true
        }
        event.reply("Since you are not the owner of the poll, you can't do this action".translate(event)).setEphemeral(true).queue()
        return false
    }

    private fun recreateMessage(
        message: Message,
        data: Poll
    ) {
        val jda = message.jda
        val intro = message.contentRaw.split("\n")[0]

        val emojis = data.getEmojis(message.guild)
        val reactions =
            emojis
                .map { e -> data.react2UserData.getOrDefault(e.name, mutableListOf()) }
                .zip(emojis)
                .filter { (list, _) -> list.isNotEmpty() }
                .map { (list, emoji) -> emoji to list.mapNotNull { u -> jda.fetchUser(u)?.asMention } }

        val reactionText = reactions.filter { (_, l) -> l.isNotEmpty() }.joinToString("\n") { (emoji, list) -> "${emoji.formatted}: ${list.joinToString(" ")}" }

        var finalMessage = intro
        if (reactionText.isNotBlank()) {
            finalMessage += "\n\n${reactionText.trim()}"
        }

        message.editMessage(finalMessage).queue()
    }

    protected fun createPoll(
        hook: InteractionHook,
        terminationTimestamp: Long?,
        author: User,
        response: String,
        options: Map<Emoji, Button>,
        onlyOneOption: Boolean
    ) {
        val components =
            options.values
                .toList<ItemComponent>()
                .toActionRows()
                .toMutableList()
        val globalActions = listOf(Button.of(ButtonStyle.PRIMARY, admin.name + "", "Admin Area", admin))
        components.add(ActionRow.of(globalActions))

        val msg = hook.editOriginal(response).setComponents(components).complete()
        msg.pinAndDelete()

        val data =
            Poll(pollType, terminationTimestamp, msg.guild.id, msg.channel.id, msg.id, author.id, options.keys.map { e -> EmojiDTO.create(e) }, onlyOneOption)
        savePollToDB(data)
        if (terminationTimestamp != null) {
            scheduler.queue(msg.id, { terminate(data, hook.jda, author.id) }, terminationTimestamp)
        }
    }

    protected fun getOptions(options: List<String>): Map<Emoji, Button> {
        val map = LinkedHashMap<Emoji, Button>()

        val usedEmoji = mutableListOf(admin.name)
        for (o in options) {
            val randomEmoji = randomEmoji(usedEmoji)
            usedEmoji.add(randomEmoji.name)
            map[randomEmoji] = Button.of(ButtonStyle.SECONDARY, randomEmoji.name, o, randomEmoji)
        }
        return map
    }

    private fun randomEmoji(usedEmoji: MutableList<String>): Emoji {
        var random = EmojiManager.getAll().random().unicode
        while (random in usedEmoji) random = EmojiManager.getAll().random().unicode

        return Emoji.fromUnicode(random)
    }

    protected open fun terminate(
        data: Poll,
        jda: JDA,
        uid: String
    ) {
        removePollFromDB(data)
        val msg = jda.getGuildById(data.gid)?.fetchMessage(data.cid, data.mid) ?: return

        val user = jda.fetchUser(uid)
        val buttonMapping = msg.buttons.associate { b -> b.id!! to b.label }
        val reactionsToUser: Map<String, List<String>> = data.react2UserData.mapKeys { (k, _) -> buttonMapping[k]!! }

        var finalMsg = msg.contentRaw.split("\n")[0] + "\n\n"
        for ((option, users) in reactionsToUser.entries) {
            finalMsg += "$option: ${if (users.isEmpty()) "--" else users.mapNotNull { u -> jda.fetchUser(u)?.asMention }.joinToString(" ")}\n"
        }
        finalMsg += "\n${pollFinished.translate(language(msg.guild, user))}"

        msg
            .editMessage(finalMsg)
            .setComponents(listOf())
            .complete()
            .hide(directHide = false)
        if (msg.isPinned) {
            msg.unpin().complete()
        }
    }

    private fun savePollToDB(poll: Poll) {
        polls.add(poll)
        session.persist(poll)
    }

    protected fun removePollFromDB(poll: Poll?) {
        if (poll == null) {
            return
        }
        polls.remove(poll)
        session.delete(poll)
    }

    private fun refreshPoll(
        pollMessage: Message,
        poll: Poll
    ) {
        val newMessage =
            pollMessage.channel
                .sendMessage(pollMessage.contentRaw.split("\n")[0])
                .setComponents(pollMessage.actionRows)
                .complete()
        newMessage.pinAndDelete()
        recreateMessage(newMessage, poll)
        poll.mid = newMessage.id
        session.persist(poll)
        pollMessage.delete().queue()
    }

    class EmojiDTO {
        var id: String = ""
        var name: String = ""

        constructor()
        constructor(id: String, name: String) {
            this.id = id
            this.name = name
        }

        fun getEmoji(guild: Guild): Emoji {
            if (id == "0") {
                return Emoji.fromUnicode(name)
            }
            return guild.retrieveEmojiById(id).complete()
        }

        companion object {
            fun create(emoji: Emoji): EmojiDTO =
                if (emoji is CustomEmoji) {
                    val id = emoji.id
                    val name = emoji.name
                    EmojiDTO(id, name)
                } else {
                    val name = emoji.name
                    EmojiDTO("0", name)
                }
        }
    }
}
