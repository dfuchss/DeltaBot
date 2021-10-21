package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.DeltaBotConfiguration
import org.fuchss.deltabot.command.admin.*
import org.fuchss.deltabot.command.user.*
import org.fuchss.deltabot.command.user.polls.SimplePoll
import org.fuchss.deltabot.command.user.polls.Summon
import org.fuchss.deltabot.command.user.polls.WeekdayPoll
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.fetchCommands
import org.fuchss.deltabot.utils.logger

/**
 * The registry for commands.
 * @param[configuration] the configuration of the bot
 * @param[scheduler] the scheduler of the bot
 */
class CommandRegistry(val configuration: DeltaBotConfiguration, val scheduler: Scheduler) {

    private val commands: MutableList<BotCommand>
    private val updateHooks = mutableListOf<Runnable>()

    init {
        commands = ArrayList()

        commands.add(Debug(configuration))
        commands.add(Shutdown())
        commands.add(GuildLanguage())
        commands.add(Echo())
        commands.add(Admin(configuration, commands))
        commands.add(State(configuration))
        commands.add(Erase())
        commands.add(Roles())
        commands.add(ResetStateAndCommands(configuration))
        commands.add(ServerRoles())

        commands.add(Language())
        commands.add(Help(configuration, commands))
        commands.add(PersistentHelp(configuration, commands))
        commands.add(Roll())
        commands.add(Teams())
        commands.add(Summon(configuration, scheduler))
        commands.add(Reminder(configuration, scheduler))
        commands.add(WeekdayPoll(scheduler))
        commands.add(SimplePoll(scheduler))

        if (!configuration.hasAdmins()) {
            logger.info("Missing initial admin .. adding initial admin command ..")
            commands.add(InitialAdminCommand(configuration) { jda, u -> initialUser(jda, u) })
        }
    }

    private fun initialUser(jda: JDA, u: User) {
        logger.info("Added initial admin $u")
        val command = commands.find { c -> c is InitialAdminCommand } ?: return
        commands.remove(command)
        updateHooks.forEach { r -> r.run() }

        jda.fetchCommands().find { c -> c.name == command.name }?.delete()?.complete()
        fixCommandPermissions(jda, configuration, commands, u)
    }

    /**
     * Register command update hooks that will be triggered on all changes of commands.
     */
    fun registerUpdateHook(r: Runnable) {
        updateHooks += r
    }

    /**
     * Get a list of all registered commands.
     */
    fun getCommands() = commands.toList()
}