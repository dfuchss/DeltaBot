package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchUser
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.objectcasket.port.Session

/**
 * A [Poll][PollBase] that provides generic polls.
 */
class SimplePoll(scheduler: Scheduler, session: Session) : PollBase("SimplePoll", scheduler, session) {

    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        val command = CommandData("poll", "simple polls")
        command.addSubcommands(
            SubcommandData("new", "creates a new poll").addOptions(
                OptionData(OptionType.STRING, "name", "a unique name of the poll").setRequired(true),
                OptionData(OptionType.STRING, "question", "the question for the poll").setRequired(true),
                OptionData(OptionType.BOOLEAN, "only-one-answer", "whether only one answer shall be selectable per user (default: true)").setRequired(false)
            ),
            SubcommandData("del", "delete the poll").addOptions(
                OptionData(OptionType.STRING, "name", "a unique name of the poll").setRequired(true)
            ),
            SubcommandData("add-option", "add a new option to the poll").addOptions(
                OptionData(OptionType.STRING, "name", "the unique name of the poll").setRequired(true),
                OptionData(OptionType.STRING, "option", "your option").setRequired(true)
            ),
            SubcommandData("del-option", "remove an option to the poll").addOptions(
                OptionData(OptionType.STRING, "name", "the unique name of the poll").setRequired(true),
                OptionData(OptionType.STRING, "option", "your option").setRequired(true)
            ),
            SubcommandData("state", "shows the status of the poll in a hidden message").addOptions(
                OptionData(OptionType.STRING, "name", "the unique name of the poll").setRequired(false)
            ),
            SubcommandData("show", "shows the poll in the channel (changes will not be possible anymore)").addOptions(
                OptionData(OptionType.STRING, "name", "the unique name of the poll").setRequired(true)
            )
        )
        return command
    }

    private val internalPollState = InternalPollState()

    override fun handle(event: SlashCommandEvent) {
        when (event.subcommandName) {
            "new" -> handleNew(event)
            "del" -> handleDelete(event)
            "add-option" -> handleAddOption(event)
            "del-option" -> handleDelOption(event)
            "state" -> handleState(event)
            "show" -> handleShow(event)
            else -> event.reply("You must use a subcommand".translate(event)).setEphemeral(true).queue()
        }
    }


    private fun handleNew(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""
        val question = event.getOption("question")?.asString ?: ""
        val onlyOneAnswer = event.getOption("only-one-answer")?.asBoolean ?: true

        if (name.isBlank() || question.isBlank()) {
            event.reply("Neither name nor question can be blank!".translate(event)).setEphemeral(true).queue()
            return
        }

        needPollData(event, name, event.user.id, false) ?: return

        internalPollState.add(InternalPoll(name, event.user.id, question, onlyOneAnswer))
        event.reply("Poll *#* created!".translate(event, name)).setEphemeral(true).queue()
    }

    private fun handleDelete(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""
        val poll = needPollData(event, name, event.user.id, true) ?: return
        internalPollState.remove(poll)
        event.reply("Poll *#* deleted!".translate(event, name)).setEphemeral(true).queue()
    }

    private fun handleAddOption(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""
        val poll = needPollData(event, name, event.user.id, true) ?: return

        val option = event.getOption("option")?.asString ?: ""
        if (option.isBlank()) {
            event.reply("An option can't be blank!".translate(event)).setEphemeral(true).queue()
            return
        }

        poll.options.add(option)

        event.reply("Option '#' added to **#**".translate(event, option, name)).setEphemeral(true).queue()
    }

    private fun handleDelOption(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""
        val poll = needPollData(event, name, event.user.id, true) ?: return
        val option = event.getOption("option")?.asString ?: ""
        if (option.isBlank()) {
            event.reply("An option can't be blank!".translate(event)).setEphemeral(true).queue()
            return
        }

        poll.options.remove(option)

        event.reply("Option '#' removed from **#**".translate(event, option, name)).setEphemeral(true).queue()
    }

    private fun handleState(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""

        val polls =
            if (name.isBlank()) {
                internalPollState.polls.filter { p -> p.uid == event.user.id }
            } else {
                internalPollState.polls.filter { p -> p.uid == event.user.id && p.name == name }
            }

        if (polls.isEmpty()) {
            event.reply("No poll(s) found!".translate(event)).setEphemeral(true).queue()
            return
        }

        if (polls.size == 1) {
            var msg = "Poll **${polls[0].name}**: "
            msg += if (polls[0].options.isEmpty()) {
                "*No Options*".translate(event)
            } else {
                "\n" + polls[0].options.joinToString("\n") { s -> "- $s" }
            }
            event.reply(msg).setEphemeral(true).queue()
        } else {
            var msg = "**Polls**:"
            msg += "\n" + polls.joinToString("\n") { p -> "- ${p.name}" }
            event.reply(msg).setEphemeral(true).queue()
        }
    }

    private fun handleShow(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString ?: ""
        val poll = needPollData(event, name, event.user.id, true) ?: return

        if (poll.options.isEmpty()) {
            event.reply("Please add options to your poll!".translate(event)).setEphemeral(true).queue()
            return
        }

        val user = event.jda.fetchUser(poll.uid)!!
        val question = "${user.asMention}: ${poll.question}"
        val options = getOptions(poll.options)
        val onlyOneAnswer = poll.onlyOneAnswer
        internalPollState.remove(poll)

        val hook = event.deferReply().complete()
        createPoll(hook, null, user, question, options, onlyOneAnswer)
    }


    private fun needPollData(event: SlashCommandEvent, name: String, uid: String, shallBePresent: Boolean): InternalPoll? {
        val poll = internalPollState.getPollData(name, uid)
        if ((poll != null) == shallBePresent)
            return poll ?: InternalPoll("", "", "", false)

        if (shallBePresent) {
            event.reply("Poll not found!".translate(event)).setEphemeral(true).queue()
        } else {
            event.reply("Poll already found!".translate(event)).setEphemeral(true).queue()
        }
        return null
    }


    private data class InternalPollState(
        var polls: MutableList<InternalPoll> = mutableListOf()
    ) {

        fun getPollData(name: String, uid: String): InternalPoll? {
            return polls.find { d -> d.name == name && d.uid == uid }
        }

        fun add(data: InternalPoll) {
            this.polls.add(data)
        }

        fun remove(data: InternalPoll?) {
            this.polls.remove(data)
        }
    }

    private data class InternalPoll(
        var name: String,
        var uid: String,
        var question: String,
        var onlyOneAnswer: Boolean,
        var options: MutableList<String> = mutableListOf()
    )

}