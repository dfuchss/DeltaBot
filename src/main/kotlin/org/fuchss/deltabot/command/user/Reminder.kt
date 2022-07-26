package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GlobalCommand
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchChannel
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.language
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.deltabot.utils.timestamp
import org.fuchss.objectcasket.port.Session
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

/**
 * A [BotCommand] that creates & manages reminder messages.
 */
class Reminder(configuration: BotConfiguration, private val scheduler: Scheduler, private val session: Session) : GlobalCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    private val reminders: MutableSet<ReminderData> = mutableSetOf()
    private val ducklingService: DucklingService = DucklingService(configuration.ducklingUrl)

    override fun createCommand(): SlashCommandData {
        val command = Commands.slash("reminder", "create reminders for certain times")
        command.addOptions(
            OptionData(OptionType.STRING, "time", "a description of a time to interpret").setRequired(true),
            OptionData(OptionType.STRING, "message", "a message I'll send to you").setRequired(true)
        )
        return command
    }

    override fun registerJDA(jda: JDA) {
        initReminders()
        initScheduler(jda)
    }

    private fun initReminders() {
        val reminders = session.getAllObjects(ReminderData::class.java)
        logger.info("Loaded ${reminders.size} reminders from DB ..")
        this.reminders.addAll(reminders)
    }

    private fun initScheduler(jda: JDA) {
        for (reminder in reminders)
            scheduler.queue(null, { remind(reminder, jda) }, reminder.timestamp)
    }

    override fun handle(event: SlashCommandInteraction) {
        // Use Language of the user for reminders ..
        val language = event.language()

        val message = event.getOption("message")?.asString ?: ""
        val timeText = event.getOption("time")?.asString ?: ""

        if (message.isBlank() || timeText.isBlank()) {
            event.reply("I need both .. message and time ..".translate(language)).setEphemeral(true).queue()
            return
        }

        val times = ducklingService.interpretTime(timeText, language)
        if (times.size != 1) {
            event.reply("I've found # time(s) in your message :(".translate(language, times.size)).setEphemeral(true).queue()
            return
        }

        val time = times[0]
        val ts = time.timestamp()
        val reminder =
            if (event.channelType == ChannelType.PRIVATE) {
                ReminderData(null, ts, "", "", true, event.user.id, message)
            } else {
                ReminderData(null, ts, event.guild!!.id, event.channel.id, false, event.user.id, message)
            }

        persistReminder(reminder)
        scheduler.queue(null, { remind(reminder, event.jda) }, ts)
        event.reply("I'll remind you <t:#:R>: '#'".translate(language, ts, message)).setEphemeral(true).queue()
    }

    private fun remind(reminder: ReminderData, jda: JDA) {
        removeReminder(reminder)

        val user = jda.fetchUser(reminder.uid)!!
        val channel = if (reminder.isDirectChannel) user.openPrivateChannel().complete() else jda.fetchChannel(reminder.gid, reminder.cid)!!
        channel.sendMessage("**Reminder ${user.asMention}**\n${reminder.message}").queue()
    }

    private fun persistReminder(reminder: ReminderData) {
        reminders.add(reminder)
        session.persist(reminder)
    }

    private fun removeReminder(reminder: ReminderData) {
        reminders.remove(reminder)
        session.delete(reminder)
    }

    @Entity
    @Table(name = "Reminder")
    class ReminderData(
        @Id
        @GeneratedValue
        var id: Int? = null,
        var timestamp: Long = -1,
        var gid: String = "",
        var cid: String = "",
        var isDirectChannel: Boolean = false,
        var uid: String = "",
        var message: String = ""
    )
}
