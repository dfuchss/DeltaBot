package org.fuchss.deltabot.command.user.polls

import com.fasterxml.jackson.module.kotlin.readValue
import com.vdurmont.emoji.EmojiManager
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import net.dv8tion.jda.api.interactions.components.Component
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.*
import org.fuchss.objectcasket.port.Session
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

/**
 * A base for [BotCommands][BotCommand] that create / handles polls.
 * @param[pollType] the type of the poll (simply a uid of the class)
 * @param[scheduler] the scheduler instance for the poll
 */
abstract class PollBase(private val pollType: String, protected val scheduler: Scheduler, protected val session: Session) : BotCommand, EventListener {

    companion object {
        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()

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

    final override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
        initPolls()
        initScheduler(jda)
    }

    private fun initPolls() {
        val dbPolls = session.getAllObjects(Poll::class.java).filter { p -> p.pollType == pollType }
        logger.info("Loaded ${dbPolls.size} polls from DB for ${this.javaClass.simpleName}")
        polls.addAll(dbPolls)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonClickEvent)
            return
        val data = polls.find { p -> p.mid == event.messageId } ?: return
        handleButtonEvent(event, data)
    }

    private fun initScheduler(jda: JDA) {
        for (update in polls)
            if (update.timestamp != null)
                scheduler.queue({ createTermination(jda, update) }, update.timestamp!!)
    }

    private fun createTermination(jda: JDA, update: Poll) {
        try {
            val guild = jda.getGuildById(update.gid)!!
            val message = guild.fetchMessage(update.cid, update.mid)!!
            terminate(message, update.uid)
        } catch (e: Exception) {
            logger.error(e.message, e)
            removePoll(update)
        }
    }


    private fun handleButtonEvent(event: ButtonClickEvent, data: Poll) {
        val buttonId = event.button?.id ?: ""
        if (finish.name == buttonId) {
            if (!isOwner(event, data))
                return

            event.deferEdit().queue()
            terminate(event.message, event.user.id)
            return
        }

        if (delete.name == buttonId) {
            if (!isOwner(event, data))
                return

            event.deferEdit().queue()
            removePoll(data)
            event.message.delete().queue()
            return
        }

        event.deferEdit().queue()

        updateUser(event.user.id, buttonId, data)
        persistPoll(data)

        recreateMessage(event.message, data)
    }

    private fun updateUser(uid: String, buttonId: String, data: Poll) {
        val reactions = data.user2React[uid] ?: listOf()

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

    private fun isOwner(event: ButtonClickEvent, data: Poll): Boolean {
        if (data.uid == event.user.id)
            return true
        event.reply("Since you are not the owner of the poll, you can't do this action".translate(event)).setEphemeral(true).queue()
        return false
    }

    private fun recreateMessage(message: Message, data: Poll) {
        val jda = message.jda
        val intro = message.contentRaw.split("\n")[0]

        val emojis = data.getEmojis(message.guild)
        val reactions = emojis.map { e -> data.react2User.getOrDefault(e.name, mutableListOf()) }.zip(emojis).filter { (list, _) -> list.isNotEmpty() }
            .map { (list, emoji) -> emoji to list.mapNotNull { u -> jda.fetchUser(u)?.asMention } }

        val reactionText = reactions.filter { (_, l) -> l.isNotEmpty() }.joinToString("\n") { (emoji, list) -> "${emoji.asMention}: ${list.joinToString(" ")}" }

        var finalMessage = intro
        if (reactionText.isNotBlank())
            finalMessage += "\n\n${reactionText.trim()}"

        message.editMessage(finalMessage).queue()
    }

    protected fun createPoll(hook: InteractionHook, terminationTimestamp: Long?, author: User, response: String, options: Map<Emoji, Button>, onlyOneOption: Boolean) {
        val components = options.values.toList<Component>().toActionRows().toMutableList()
        val globalActions = listOf(Button.secondary(finish.name + "", finish), Button.secondary(delete.name, delete))
        components.add(ActionRow.of(globalActions))

        val msg = hook.editOriginal(response).setActionRows(components).complete()
        msg.pinAndDelete()

        val data = Poll(pollType, terminationTimestamp, msg.guild.id, msg.channel.id, msg.id, author.id, options.keys.map { e -> EmojiDTO.create(e) }, onlyOneOption)
        persistPoll(data)
        if (terminationTimestamp != null)
            scheduler.queue({ terminate(msg, author.id) }, terminationTimestamp)
    }

    protected fun getOptions(options: List<String>): Map<Emoji, Button> {
        val map = LinkedHashMap<Emoji, Button>()

        val usedEmoji = mutableListOf(finish.name, delete.name)
        for (o in options) {
            val randomEmoji = randomEmoji(usedEmoji)
            usedEmoji.add(randomEmoji.name)
            map[randomEmoji] = Button.of(ButtonStyle.SECONDARY, randomEmoji.name, o, randomEmoji)
        }
        return map
    }

    private fun randomEmoji(usedEmoji: MutableList<String>): Emoji {
        var random = EmojiManager.getAll().random().unicode
        while (random in usedEmoji)
            random = EmojiManager.getAll().random().unicode

        return Emoji.fromUnicode(random)
    }


    protected open fun terminate(oldMessage: Message, uid: String) {
        val msg = oldMessage.refresh()
        val data = polls.find { p -> p.mid == msg.id }
        removePoll(data)
        if (data == null) {
            msg.editMessageComponents(listOf()).complete().hide(directHide = false)
            if (msg.isPinned)
                msg.unpin().complete()
            return
        }

        val user = oldMessage.jda.fetchUser(uid)
        val buttonMapping = msg.buttons.associate { b -> b.id!! to b.label }
        val reactionsToUser: Map<String, List<String>> = data.react2User.mapKeys { (k, _) -> buttonMapping[k]!! }

        var finalMsg = oldMessage.contentRaw.split("\n")[0] + "\n\n"
        for ((d, users) in reactionsToUser.entries.sortedBy { (k, _) -> k }) {
            finalMsg += "$d: ${if (users.isEmpty()) "--" else users.mapNotNull { u -> oldMessage.jda.fetchUser(u)?.asMention }.joinToString(" ")}\n"
        }
        finalMsg += "\n${pollFinished.translate(language(msg.guild, user))}"

        msg.editMessage(finalMsg).setActionRows(listOf()).complete().hide(directHide = false)
        if (msg.isPinned)
            msg.unpin().complete()
    }

    private fun persistPoll(poll: Poll) {
        polls.add(poll)
        session.persist(poll)
    }

    protected fun removePoll(poll: Poll?) {
        if (poll == null)
            return
        polls.remove(poll)
        session.delete(poll)
    }

    @Entity
    @Table(name = "Poll")
    class Poll {
        @Id
        @GeneratedValue
        var id: Int? = null
        var pollType: String = ""
        var timestamp: Long? = null
        var gid: String = ""
        var cid: String = ""
        var mid: String = ""
        var uid: String = ""
        var onlyOneOption: Boolean = false

        private var optionData: String = "{}"
        val options: List<EmojiDTO>
            get() = om.readValue(optionData)

        private var react2UserData: String = "{}"
        val react2User: Map<String, List<String>>
            get() = om.readValue(react2UserData)

        private var user2ReactData: String = "{}"
        val user2React: Map<String, List<String>>
            get() = om.readValue(user2ReactData)

        companion object {
            private val om = createObjectMapper()
        }

        constructor()

        constructor(pollType: String, timestamp: Long?, gid: String, cid: String, mid: String, uid: String, options: List<EmojiDTO>, onlyOneOption: Boolean) {
            this.pollType = pollType
            this.timestamp = timestamp
            this.gid = gid
            this.cid = cid
            this.mid = mid
            this.uid = uid
            this.optionData = om.writeValueAsString(options)
            this.onlyOneOption = onlyOneOption
        }

        fun getEmojis(guild: Guild): List<Emoji> {
            return options.map { dto -> dto.getEmoji(guild) }
        }

        fun cleanup() {
            val newUser2React = user2React.filter { e -> e.value.isNotEmpty() }.toMap()
            val newReact2User = react2User.filter { e -> e.value.isNotEmpty() }.toMap()
            user2ReactData = om.writeValueAsString(newUser2React)
            react2UserData = om.writeValueAsString(newReact2User)
        }

        fun addReact2User(react: String, uid: String) {
            val newReact2User = react2User.toMutableMap()
            newReact2User[react] = (newReact2User[react] ?: emptyList()).withFirst(uid)
            react2UserData = om.writeValueAsString(newReact2User)
        }

        fun addUser2React(uid: String, react: String) {
            val newUser2React = user2React.toMutableMap()
            newUser2React[uid] = (newUser2React[uid] ?: emptyList()).withFirst(react)
            user2ReactData = om.writeValueAsString(newUser2React)
        }

        fun removeReact2User(react: String, uid: String) {
            val newReact2User = react2User.toMutableMap()
            newReact2User[react] = (newReact2User[react] ?: emptyList()).without(uid)
            react2UserData = om.writeValueAsString(newReact2User)
        }

        fun removeUser2React(uid: String) {
            val newUser2React = user2React.toMutableMap()
            newUser2React.remove(uid)
            user2ReactData = om.writeValueAsString(newUser2React)
        }

        fun removeUser2React(uid: String, react: String) {
            val newUser2React = user2React.toMutableMap()
            newUser2React[uid] = (newUser2React[uid] ?: emptyList()).without(react)
            user2ReactData = om.writeValueAsString(newUser2React)
        }

        fun setUser2React(uid: String, newReactions: MutableList<String>) {
            val newUser2React = user2React.toMutableMap()
            newUser2React[uid] = newReactions
            user2ReactData = om.writeValueAsString(newUser2React)
        }


    }

    data class EmojiDTO(
        var id: String,
        var name: String
    ) {

        fun getEmoji(guild: Guild): Emoji {
            if (id == "0")
                return Emoji.fromUnicode(name)
            val emote = guild.retrieveEmoteById(id).complete()
            return Emoji.fromEmote(emote)
        }

        companion object {
            fun create(emoji: Emoji): EmojiDTO {
                val id = emoji.id
                val name = emoji.name
                return EmojiDTO(id, name)
            }
        }
    }
}


