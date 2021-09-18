package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.Component
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.*

abstract class PollBase(configPath: String, protected val scheduler: Scheduler) : BotCommand, EventListener {

    companion object {
        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()
    }

    protected val pollState: PollState = PollState().load(configPath)

    override fun registerJDA(jda: JDA) {
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
                scheduler.queue({ terminate(jda.getGuildById(update.gid)!!.fetchMessage(update.cid, update.mid)!!, update.uid) }, update.timestamp!!)
    }


    private fun handleSummonButtonEvent(event: ButtonClickEvent, data: PollData) {
        val buttonId = event.button?.id ?: ""
        if (finish.name == buttonId) {
            if (!isOwner(event, data))
                return

            event.deferEdit().queue()
            pollState.remove(data)
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

        val uid = event.user.id
        if (data.userToReact.containsKey(uid) && data.userToReact[uid] == buttonId) {
            data.userToReact.remove(uid)
        } else {
            data.userToReact[uid] = buttonId
        }
        pollState.store()

        recreateMessage(event.message, data)
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
        val reactionsToUser: Map<String, List<String>> = data.userToReact.toMap().reverseMap()
        val reactions = emojis.map { e -> reactionsToUser.getOrDefault(e.name, emptyList()) }.zip(emojis).filter { (list, _) -> list.isNotEmpty() }
            .map { (list, emoji) -> emoji to list.mapNotNull { u -> jda.fetchUser(u)?.asMention } }

        val reactionText = reactions.filter { (_, l) -> l.isNotEmpty() }.joinToString("\n") { (emoji, list) -> "${emoji.asMention}: ${list.joinToString(" ")}" }

        var finalMessage = intro
        if (reactionText.isNotBlank())
            finalMessage += "\n\n${reactionText.trim()}"

        message.editMessage(finalMessage).queue()
    }

    protected fun createPoll(channel: MessageChannel, terminationTimestamp: Long?, author: User, response: String, options: Map<Emoji, Button>) {
        val components = options.values.toList<Component>().toActionRows().toMutableList()
        val globalActions = listOf(Button.secondary(finish.name + "", finish), Button.secondary(delete.name, delete))
        components.add(ActionRow.of(globalActions))

        val msg = channel.sendMessage(response).setActionRows(components).complete()
        msg.pinAndDelete()

        val data = PollData(terminationTimestamp, msg.guild.id, msg.channel.id, msg.id, author.id, options.keys.map { e -> EmojiDTO.create(e) })
        pollState.add(data)
        if (terminationTimestamp != null)
            scheduler.queue({ terminate(msg, author.id) }, terminationTimestamp)
    }

    protected abstract fun terminate(oldMessage: Message, uid: String)

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

        fun remove(data: PollData) {
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
        var userToReact: MutableMap<String, String> = mutableMapOf()
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