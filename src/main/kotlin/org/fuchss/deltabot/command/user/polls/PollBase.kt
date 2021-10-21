package org.fuchss.deltabot.command.user.polls

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
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.*

/**
 * A base for [BotCommands][BotCommand] that create / handles polls.
 * @param[configPath] the path to the config of the poll
 * @param[scheduler] the scheduler instance for the poll
 */
abstract class PollBase(configPath: String, protected val scheduler: Scheduler) : BotCommand, EventListener {

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
    protected val pollState: PollState = PollState().load(configPath)

    final override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
        initScheduler(jda)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonClickEvent)
            return
        val data = pollState.getPollData(event.messageId) ?: return
        handleSummonButtonEvent(event, data)
    }

    private fun initScheduler(jda: JDA) {
        for (update in pollState.polls)
            if (update.timestamp != null)
                scheduler.queue({ createTermination(jda, update) }, update.timestamp!!)
    }

    private fun createTermination(jda: JDA, update: PollData) {
        try {
            val guild = jda.getGuildById(update.gid)!!
            val message = guild.fetchMessage(update.cid, update.mid)!!
            terminate(message, update.uid)
        } catch (e: Exception) {
            logger.error(e.message, e)
            pollState.remove(update)
        }
    }

    private fun handleSummonButtonEvent(event: ButtonClickEvent, data: PollData) {
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
            pollState.remove(data)
            event.message.delete().queue()
            return
        }

        event.deferEdit().queue()

        updateUser(event.user.id, buttonId, data)
        pollState.store()

        recreateMessage(event.message, data)
    }

    private fun updateUser(uid: String, buttonId: String, data: PollData) {
        val reactions = data.user2React[uid] ?: listOf()

        if (data.onlyOneOption) {
            // Only one option allowed
            reactions.forEach { r -> data.react2User[r]?.remove(uid) }
            data.user2React.remove(uid)

            if (buttonId !in reactions) {
                data.user2React[uid] = mutableListOf(buttonId)
                data.react2User.getOrPut(buttonId) { mutableListOf() }.add(uid)
            }
        } else {
            // Multiple Options allowed
            if (buttonId in reactions) {
                // Remove reaction ..
                data.user2React.getOrPut(uid) { mutableListOf() }.remove(buttonId)
                data.react2User.getOrPut(buttonId) { mutableListOf() }.remove(uid)
            } else {
                // Add reaction ..
                data.user2React.getOrPut(uid) { mutableListOf() }.add(buttonId)
                data.react2User.getOrPut(buttonId) { mutableListOf() }.add(uid)
            }
        }

        // Cleanup maps ..
        data.user2React.entries.removeIf { (_, v) -> v.isEmpty() }
        data.react2User.entries.removeIf { (_, v) -> v.isEmpty() }
    }

    private fun isOwner(event: ButtonClickEvent, data: PollData): Boolean {
        if (data.uid == event.user.id)
            return true
        event.reply("Since you are not the owner of the poll, you can't do this action".translate(event)).setEphemeral(true).queue()
        return false
    }

    private fun recreateMessage(message: Message, data: PollData) {
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

        val data = PollData(terminationTimestamp, msg.guild.id, msg.channel.id, msg.id, author.id, options.keys.map { e -> EmojiDTO.create(e) }, onlyOneOption)
        pollState.add(data)
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
        val data = pollState.getPollData(msg.id)
        pollState.remove(data)
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

    protected data class PollState(
        var polls: MutableList<PollData> = mutableListOf()
    ) : Storable() {

        fun getPollData(messageId: String): PollData? {
            return polls.find { d -> d.mid == messageId }
        }

        fun add(data: PollData) {
            this.polls.add(data)
            this.store()
        }

        fun remove(data: PollData?) {
            this.polls.remove(data)
            this.store()
        }
    }

    protected data class PollData(
        var timestamp: Long?,
        var gid: String,
        var cid: String,
        var mid: String,
        var uid: String,
        var options: List<EmojiDTO> = mutableListOf(),
        var onlyOneOption: Boolean,
        var react2User: MutableMap<String, MutableList<String>> = mutableMapOf(),
        var user2React: MutableMap<String, MutableList<String>> = mutableMapOf()
    ) {
        fun getEmojis(guild: Guild): List<Emoji> {
            return options.map { dto -> dto.getEmoji(guild) }
        }
    }

    protected data class EmojiDTO(
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