package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.Scheduler

class SimplePoll(configuration: Configuration, scheduler: Scheduler) : PollBase("./states/polls.json", scheduler) {


    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = false

    override fun createCommand(): CommandData {
        TODO("Not yet implemented")
    }

    override fun handle(event: SlashCommandEvent) {
        TODO("Not yet implemented")
    }

    override fun terminate(oldMessage: Message, uid: String) {
        TODO("Not yet implemented")
    }

}