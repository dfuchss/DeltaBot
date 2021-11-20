package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.*
import org.fuchss.deltabot.utils.findGenericTimespan
import org.fuchss.deltabot.utils.timestamp
import org.fuchss.objectcasket.port.Session
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.random.Random

/**
 * A [Poll][PollBase] that asks [Members][Member] to play together.
 */
class Summon(pollAdmin: IPollAdmin, configuration: BotConfiguration, scheduler: Scheduler, session: Session) : PollBase(pollAdmin, "summon", scheduler, session) {

    companion object {
        private val summonMessages = listOf(
            "###USER###: Who wants to play ###MENTION### ###TIME###?",
            "###USER###: Who would be up for playing ###MENTION### ###TIME###?"
        )

        private val summonReactionsDefault = listOf(":+1:", ":thinking:", ":question:", ":pensive:", ":-1").map { e -> e.toEmoji() }
        private val summonReactionsDefaultStyle = listOf(ButtonStyle.SUCCESS, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.SECONDARY, ButtonStyle.DANGER)
        private const val GraceTimeInMinutes = 15L
    }

    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    private val ducklingService: DucklingService = DucklingService(configuration.ducklingUrl)

    override fun createCommand(guild: Guild): CommandData {
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

        val game = event.getOption("game")!!.asRole
        val time = event.getOption("time")?.asString ?: ""

        createSummon(event, event.guild!!, event.user, game, time)
    }


    private fun createSummon(event: SlashCommandEvent, guild: Guild, user: User, game: Role, time: String) {
        // TODO maybe specify default time to another time ..
        val extractedTime = findGenericTimespan(time, event.language(), ducklingService) ?: LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 0))

        if (extractedTime < LocalDateTime.now()) {
            event.reply("Your point in time shall be in the future :)".translate(event.language())).setEphemeral(true).queue()
            return
        }

        val reply = event.deferReply().complete()

        var response = summonMessages[Random.nextInt(summonMessages.size)].translate(event.language())
        response = response.replace("###USER###", user.asMention)
        response = response.replace("###MENTION###", game.asMention)
        response = response.replace("###TIME###", "<t:${extractedTime.timestamp()}:R>")

        val options = getEmojis(guild).zip(getButtons(guild)).toMap()
        createPoll(reply, extractedTime.plusMinutes(GraceTimeInMinutes).timestamp(), user, response, options, true)
    }


    override fun terminate(data: Poll, jda: JDA, eventMessage: Message?, uid: String) {
        removePollFromDB(data)
        val msg = eventMessage?.refresh() ?: jda.getGuildById(data.gid)?.fetchMessage(data.cid, data.mid) ?: return

        val user = jda.fetchUser(uid)
        val newContent = msg.contentRaw + "\n\n${pollFinished.translate(language(msg.guild, user))}"
        msg.editMessage(newContent).setActionRows(listOf()).complete().hide(directHide = false)
        if (msg.isPinned)
            msg.unpin().complete()
    }


    private fun getButtons(guild: Guild): List<Button> {
        val emojis = getEmojis(guild)
        return emojis.zip(summonReactionsDefaultStyle).map { (emoji, style) -> Button.of(style, emoji.name, emoji) }
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


}
