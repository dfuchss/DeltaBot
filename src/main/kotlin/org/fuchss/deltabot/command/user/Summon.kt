package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

class Summon(configuration: Configuration, private val scheduler: Scheduler) : BotCommand, EventListener {

    companion object {
        private val summonMsgs = listOf(
            "###USER###: Who wants to play ###MENTION### ###TIME###?",
            "###USER###: Who would be up for playing ###MENTION### ###TIME###?"
        )

        private val summonReactionsDefault = listOf(":+1:", ":thinking:", ":question:", ":pensive:", ":-1").map { e -> e.toEmoji() }
        private val summonReactionsDefaultStyle = listOf(ButtonStyle.SUCCESS, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.DANGER)

        private val finish = ":octagonal_sign:".toEmoji()
        private val delete = ":put_litter_in_its_place:".toEmoji()

        private const val pollFinished = "*Poll finished. You can't vote anymore :)*"
    }

    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = false

    private val summonState: SummonState = SummonState().load("./states/summon.json")

    private val ducklingService: DucklingService = DucklingService(configuration.ducklingUrl)

    override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
        initScheduler(jda)
    }

    private fun initScheduler(jda: JDA) {
        for (update in summonState.summons)
            scheduler.queue({ terminateSummon(jda.getGuildById(update.gid)!!.fetchMessage(update.cid, update.mid)!!, jda.fetchUser(update.uid)!!) }, update.timestamp)
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
            event.reply("No Server found .. this should not happen ..").setEphemeral(true).queue()
            return
        }

        val reply = event.deferReply().complete()

        val game = event.getOption("game")!!.asRole
        val time = event.getOption("time")?.asString ?: ""

        createSummon(event, event.guild!!, event.user, event.channel, game, time, reply)
    }

    private fun handleSummonButtonEvent(event: ButtonClickEvent, data: SummonData) {
        event.deferEdit().queue()

        val buttonId = event.button?.id ?: ""
        if (finish.name == buttonId) {
            summonState.remove(data)
            terminateSummon(event.message!!, event.jda.fetchUser(data.uid)!!)
            return
        }

        if (delete.name == buttonId) {
            summonState.remove(data)
            event.message!!.delete().queue()
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

        message.editMessage(finalMessage).queue()
    }

    private fun createSummon(event: SlashCommandEvent, guild: Guild, user: User, channel: MessageChannel, game: Role, time: String, reply: InteractionHook) {
        // TODO maybe specify default time to another time ..
        val extractedTime = findGenericTimespan(time, event.language(), ducklingService)?.first ?: LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 0))

        if (extractedTime < LocalDateTime.now()) {
            reply.setEphemeral(true).editOriginal("Your point in time shall be in the future :)".translate(event.language())).queue()
            return
        }

        var response = summonMsgs[Random.nextInt(summonMsgs.size)].translate(event.language())
        response = response.replace("###USER###", user.asMention)
        response = response.replace("###MENTION###", game.asMention)
        response = response.replace("###TIME###", "<t:${extractedTime.timestamp()}:R>")

        val components = getButtons(guild)
        val msg = channel.sendMessage(response).setActionRows(components).complete()
        msg.pinAndDelete()

        val data = SummonData(extractedTime.timestamp(), msg.guild.id, msg.channel.id, msg.id, user.id)
        summonState.add(data)
        scheduler.queue({ terminateSummon(msg, user) }, extractedTime.timestamp())
        reply.deleteOriginal().queue()
    }

    private fun terminateSummon(msg: Message, user: User) {
        val data = summonState.getSummonMessage(msg.id)
        if (data != null)
            summonState.remove(data)

        val newContent = msg.contentRaw + "\n\n${pollFinished.translate(language(msg.guild, user))}"
        msg.editMessage(newContent).setActionRows(listOf()).complete().hide()
        if (msg.isPinned)
            msg.unpin().complete()
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
        var userToReact: MutableMap<String, String> = mutableMapOf()
    )
}



