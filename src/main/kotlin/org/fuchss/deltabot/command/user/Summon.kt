package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.utils.*
import java.time.Duration
import kotlin.random.Random

class Summon(configuration: Configuration, private val scheduler: Scheduler) : BotCommand, EventListener {

    companion object {
        private val summonMsgs = listOf(
            "###USER###: Who wants to play ###MENTION### ###DAY### ###TIME###?",
            "###USER###: Who would be up for playing ###MENTION### ###DAY### ###TIME###?"
        )

        private val summonReactionsDefault = listOf(":+1:", ":thinking:", ":question:", ":pensive:", ":-1").map { e -> e.toEmoji() }
        private val summonReactionsDefaultStyle = listOf(ButtonStyle.SUCCESS, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.DANGER)

        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()

        private const val pollFinished = "*Poll finished. You can't vote anymore :)*"
    }

    override val isAdminCommand: Boolean get() = false
    override val isGlobal: Boolean get() = false

    private val summonState: SummonState = SummonState().load("./states/summon.json")

    private val ducklingService: DucklingService = DucklingService(configuration.ducklingUrl)

    override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
        initScheduler(jda)
    }

    private fun initScheduler(jda: JDA) {
        for (update in summonState.summons)
            scheduler.queue({ summonUpdate(update, jda) }, update.timestamp)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonClickEvent)
            return
        val data = summonState.getSummonMessage(event.messageId) ?: return
        handleSummonButtonEvent(event, data)
    }

    override fun createCommand(): CommandData {
        val command = CommandData("summon", "summon players and make a poll to play a game")
        command.addOptions(//
            OptionData(OptionType.ROLE, "game", "the game as a role you want to play").setRequired(true),
            OptionData(OptionType.STRING, "time", "an optional time for the gameplay").setRequired(false)
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        if (event.guild == null) {
            event.reply("No Server found .. this should not happen ..").setEphemeral(true).complete()
            return
        }

        val reply = event.deferReply().complete()

        val game = event.getOption("game")!!.asRole
        val time = event.getOption("time")?.asString ?: "today at the usual time"

        createSummon(event.guild!!, event.user, event.channel, game, time, event.jda)

        reply.deleteOriginal().complete()
    }

    private fun handleSummonButtonEvent(event: ButtonClickEvent, data: SummonData) {
        event.deferEdit().complete()

        val buttonId = event.button?.id ?: ""
        if (finish.name == buttonId) {
            summonState.remove(data)
            terminateSummon(event.message!!)
            return
        }

        if (delete.name == buttonId) {
            summonState.remove(data)
            event.message!!.delete().complete()
            return
        }

        val uid = event.user.id
        if (data.userToReact.containsKey(uid) && data.userToReact[uid] == buttonId) {
            data.userToReact.remove(uid)
        } else {
            data.userToReact[uid] = buttonId
        }
        summonState.store()

        recreateMessage(event.message!!, data)
    }

    private fun recreateMessage(message: Message, data: SummonData) {
        val jda = message.jda
        val intro = message.contentRaw.split("\n")[0]

        val emojis = getEmojis(message.guild)
        val reactionsToUser: Map<String, List<String>> = data.userToReact.toMap().reverseMap()
        val reactions = emojis.map { e -> reactionsToUser.getOrDefault(e.name, emptyList()) }.zip(emojis).filter { (list, _) -> list.isNotEmpty() }
            .map { (list, emoji) -> emoji to list.mapNotNull { u -> jda.fetchUser(u)?.asMention } }

        val reactionText = reactions.filter { (_, l) -> l.isNotEmpty() }.joinToString("\n") { (emoji, list) -> "${emoji.asMention}: ${list.joinToString(" ")}" }

        var finalMessage = intro
        if (reactionText.isNotBlank())
            finalMessage += "\n\n${reactionText.trim()}"

        message.editMessage(finalMessage).complete()
    }

    private fun createSummon(guild: Guild, user: User, channel: MessageChannel, game: Role, time: String, jda: JDA) {
        var timeString = time
        var day = "today"
        var offset = 0

        val extractedDay = findGenericDayTimespan(time, ducklingService)

        if (extractedDay != null) {
            val range = extractedDay.second.first

            timeString = ""
            if (range.first - 1 > 0)
                timeString += time.substring(0, range.first - 1)
            if (range.last + 1 < time.length)
                timeString += time.substring(range.last + 1)

            timeString = timeString.trim()
            day = extractedDay.second.second
            offset = extractedDay.first.toDays().toInt()
        }
        day = "**$day**"

        var response = summonMsgs[Random.nextInt(summonMsgs.size)]
        response = response.replace("###USER###", user.asMention)
        response = response.replace("###MENTION###", game.asMention)
        response = response.replace("###TIME###", timeString)
        response = response.replace("###DAY###", day)

        val components = getButtons(guild)
        val msg = channel.sendMessage(response).setActionRows(components).complete()
        msg.pinAndDelete()

        addToScheduler(user.id, msg, offset, day, jda)
    }

    private fun addToScheduler(authorId: String, responseMessage: Message, dayOffset: Int, dayValue: String, jda: JDA) {
        if (dayOffset <= -1)
            return

        val nextDay = nextDayTS()
        val data = SummonData(nextDay, responseMessage.guild.id, responseMessage.channel.id, responseMessage.id, authorId, dayOffset, dayValue)
        summonState.add(data)
        scheduler.queue({ summonUpdate(data, jda) }, nextDay)
    }

    private fun summonUpdate(data: SummonData, jda: JDA) {
        summonState.remove(data)
        try {
            val channel = jda.fetchTextChannel(data.gid, data.cid)!!
            val msg = channel.retrieveMessageById(data.mid).complete()

            val newDayOffset = data.dayOffset - 1
            if (newDayOffset < 0) {
                terminateSummon(msg)
                return
            }
            val newDayValue = "**${daysText(Duration.ofDays(newDayOffset.toLong()))}**"
            val newContent = msg.contentRaw.replace(data.dayValue, newDayValue)
            msg.editMessage(newContent).complete()
            addToScheduler(data.uid, msg, newDayOffset, newDayValue, jda)
        } catch (e: Exception) {
            logger.error(e.message)
        }
    }

    private fun terminateSummon(msg: Message) {
        val newContent = msg.contentRaw + "\n\n$pollFinished"
        msg.editMessage(newContent).setActionRows(listOf()).complete()
        if (msg.isPinned)
            msg.unpin().complete()
        msg.hide()
    }

    private fun getButtons(guild: Guild): List<ActionRow> {
        val emojis = getEmojis(guild)
        val buttons = emojis.zip(summonReactionsDefaultStyle).map { (emoji, style) -> Button.of(style, emoji.name, emoji) }
        val actions = listOf(Button.secondary(finish.name + "", finish), Button.secondary(delete.name, delete))
        return listOf(ActionRow.of(buttons), ActionRow.of(actions))
    }

    private fun getEmojis(guild: Guild): List<Emoji> {
        val emojis = summonReactionsDefault.toMutableList()

        try {
            val assets = listOf("ThumbsUp45", "ThumbsUp135")
            var emotes = guild.retrieveEmotes().complete()
            val newAssets = assets.filter { a -> !emotes.any { e -> e.name == a } }
            for (asset in newAssets) {
                guild.createEmote(asset, Icon.from(this.javaClass.getResourceAsStream("/assets/$asset.png")!!)).complete()
            }

            if (newAssets.isNotEmpty())
                emotes = guild.retrieveEmotes().complete()
            emotes = assets.mapNotNull { a -> emotes.find { e -> e.name == a } }

            emojis[1] = Emoji.fromEmote(emotes[0].name, emotes[0].idLong, emotes[0].isAnimated)
            emojis[3] = Emoji.fromEmote(emotes[1].name, emotes[1].idLong, emotes[1].isAnimated)
            return emojis
        } catch (e: Exception) {
            return emojis
        }

    }


    private data class SummonState(
        var summons: MutableList<SummonData> = mutableListOf()
    ) : Storable() {

        fun getSummonMessage(messageId: String): SummonData? {
            return summons.find { s -> s.mid == messageId }
        }

        fun add(data: SummonData) {
            this.summons.add(data)
            this.store()
        }

        fun remove(data: SummonData) {
            this.summons.remove(data)
            this.store()
        }
    }

    private data class SummonData(
        var timestamp: Long,
        var gid: String,
        var cid: String,
        var mid: String,
        var uid: String,
        var dayOffset: Int,
        var dayValue: String,
        var userToReact: MutableMap<String, String> = mutableMapOf()
    )
}



