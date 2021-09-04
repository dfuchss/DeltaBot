package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.cognitive.DucklingService
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.*

class Reminder(configuration: Configuration, private val scheduler: Scheduler) : BotCommand {
    override val isAdminCommand: Boolean get() = false
    override val isGlobal: Boolean get() = true

    private val reminderState = ReminderState().load("./states/reminder.json")
    private val ducklingService: DucklingService = DucklingService(configuration.ducklingUrl)

    override fun createCommand(): CommandData {
        val command = CommandData("reminder", "create reminders for certain times")
        command.addOptions(
            OptionData(OptionType.STRING, "time", "a description of a time to interpret").setRequired(true),
            OptionData(OptionType.STRING, "message", "a message I'll send to you").setRequired(true)
        )
        return command
    }

    override fun registerJDA(jda: JDA) {
        initScheduler(jda)
    }

    private fun initScheduler(jda: JDA) {
        for (reminder in reminderState.reminders)
            scheduler.queue({ remind(reminder, jda) }, reminder.timestamp)
    }


    override fun handle(event: SlashCommandEvent) {
        val message = event.getOption("message")?.asString ?: ""
        val timeText = event.getOption("time")?.asString ?: ""

        if (message.isBlank() || timeText.isBlank()) {
            event.reply("I need both .. message and time ..".translate(event)).setEphemeral(true).complete()
            return
        }

        val times = ducklingService.interpretTime(timeText, event.user.language())
        if (times.size != 1) {
            event.reply("I've found # time(s) in your message :(".translate(event, times.size)).setEphemeral(true).complete()
            return
        }

        val (time, _) = times[0]
        val ts = time.timestamp()
        val reminder =
            if (event.channelType == ChannelType.PRIVATE)
                ReminderData(ts, "", "", true, event.user.id, message)
            else
                ReminderData(ts, event.guild!!.id, event.channel.id, false, event.user.id, message)

        reminderState.add(reminder)
        scheduler.queue({ remind(reminder, event.jda) }, ts)
        event.reply("I'll remind you at #: '#'".translate(event, time, message)).setEphemeral(true).complete()
    }

    private fun remind(reminder: ReminderData, jda: JDA) {
        reminderState.remove(reminder)

        val user = jda.fetchUser(reminder.uid)!!
        val channel = if (reminder.isDirectChannel) user.openPrivateChannel().complete() else jda.fetchTextChannel(reminder.gid, reminder.cid)!!
        channel.sendMessage("**Reminder #**\n#".translate(user, user.asMention, reminder.message)).complete()
    }


    private data class ReminderState(
        var reminders: MutableList<ReminderData> = mutableListOf()
    ) : Storable() {

        fun add(data: ReminderData) {
            this.reminders.add(data)
            this.store()
        }

        fun remove(data: ReminderData) {
            this.reminders.remove(data)
            this.store()
        }
    }

    private data class ReminderData(
        var timestamp: Long,
        var gid: String,
        var cid: String,
        var isDirectChannel: Boolean,
        var uid: String,
        var message: String
    )
}


