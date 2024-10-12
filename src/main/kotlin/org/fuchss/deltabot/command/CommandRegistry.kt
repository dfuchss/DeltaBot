package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.admin.Admin
import org.fuchss.deltabot.command.admin.Channels
import org.fuchss.deltabot.command.admin.Debug
import org.fuchss.deltabot.command.admin.Echo
import org.fuchss.deltabot.command.admin.GuildAdmin
import org.fuchss.deltabot.command.admin.GuildLanguage
import org.fuchss.deltabot.command.admin.InitialAdminCommand
import org.fuchss.deltabot.command.admin.PersistentHelp
import org.fuchss.deltabot.command.admin.ResetStateAndCommands
import org.fuchss.deltabot.command.admin.Shutdown
import org.fuchss.deltabot.command.admin.State
import org.fuchss.deltabot.command.admin.TransferText
import org.fuchss.deltabot.command.admin.UnhideAll
import org.fuchss.deltabot.command.user.Emojify
import org.fuchss.deltabot.command.user.Help
import org.fuchss.deltabot.command.user.Language
import org.fuchss.deltabot.command.user.Roles
import org.fuchss.deltabot.command.user.Roll
import org.fuchss.deltabot.command.user.Teams
import org.fuchss.deltabot.command.user.polls.IPollAdmin
import org.fuchss.deltabot.command.user.polls.PollAdmin
import org.fuchss.deltabot.command.user.polls.SimplePoll
import org.fuchss.deltabot.command.user.polls.WeekdayPoll
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchCommands
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.objectpacker.port.Session

/**
 * The registry for commands.
 * @param[configuration] the configuration of the bot
 * @param[dbLocation] the file location of the database
 * @param[scheduler] the scheduler of the bot
 * @param[session] the session / databse of the bot
 */
class CommandRegistry(
    private val configuration: BotConfiguration,
    dbLocation: String,
    scheduler: Scheduler,
    session: Session
) : ICommandRegistry,
    EventListener {
    private val pollAdmin: IPollAdmin = PollAdmin()

    private val globalCommands: MutableList<GlobalCommand> = mutableListOf()
    private val guildCommands: MutableList<GuildCommand> = mutableListOf()

    private val updateHooks = mutableListOf<Runnable>()
    private val nameToCommand: MutableMap<String, BotCommand> = mutableMapOf()

    init {
        val commands: MutableList<BotCommand> = mutableListOf()
        commands.add(Debug(configuration))
        commands.add(Shutdown())
        commands.add(GuildLanguage())
        commands.add(Echo())
        commands.add(Admin(configuration))
        commands.add(GuildAdmin(session))
        commands.add(State(configuration, scheduler, session))
        commands.add(Roles(session))
        commands.add(ResetStateAndCommands(configuration, dbLocation, session))
        commands.add(TransferText())
        commands.add(Channels())
        commands.add(UnhideAll())

        commands.add(Language())
        commands.add(Help(configuration, session, this))
        commands.add(PersistentHelp(configuration, session, this))
        commands.add(Roll())
        commands.add(Teams())
        commands.add(Emojify())

        commands.add(WeekdayPoll(pollAdmin, scheduler, session))
        commands.add(SimplePoll(pollAdmin, scheduler, session))

        if (!configuration.hasAdmins()) {
            logger.info("Missing initial admin .. adding initial admin command ..")
            commands.add(InitialAdminCommand(configuration) { jda, u -> initialUser(jda, u) })
        }

        commands.filterIsInstance<GlobalCommand>().forEach { gc -> globalCommands.add(gc) }
        commands.filterIsInstance<GuildCommand>().forEach { gc -> guildCommands.add(gc) }
    }

    private fun initialUser(
        jda: JDA,
        u: User
    ) {
        logger.info("Added initial admin $u")
        val command = globalCommands.filterIsInstance(InitialAdminCommand::class.java).firstOrNull() ?: return
        globalCommands.remove(command)
        updateHooks.forEach { r -> r.run() }

        jda
            .fetchCommands()
            .find { c -> c.name == command.createCommand().name }
            ?.delete()
            ?.complete()
    }

    override fun permissions(command: Command): CommandPermissions {
        if (!nameToCommand.containsKey(command.name)) {
            error("Command ${command.name} not suitable registered! Permissions not found!")
        }

        return nameToCommand[command.name]!!.permissions
    }

    override fun nameToCommand(): Map<String, BotCommand> = nameToCommand.toMap()

    /**
     * Register command update hooks that will be triggered on all changes of commands.
     */
    override fun registerUpdateHook(r: Runnable) {
        updateHooks += r
    }

    override fun onEvent(event: GenericEvent) {
        if (event is ReadyEvent) {
            event.jda.addEventListener(pollAdmin)
            initCommands(event.jda)
            updateHooks.forEach { r -> r.run() }
        }
    }

    private fun initCommands(jda: JDA) {
        globalCommands.forEach { nameToCommand[it.createCommand().name] = it }
        if (jda.guilds.isNotEmpty()) {
            guildCommands.forEach { nameToCommand[it.createCommand(jda.guilds[0]).name] = it }
        }

        val commands = jda.updateCommands()
        commands.addCommands(globalCommands.map { it.createCommand().setDefaultPermissions(DefaultMemberPermissions.ENABLED) }).queue()
        jda.guilds.forEach {
            it.updateCommands().addCommands(guildCommands.map { c -> c.createCommand(it).setDefaultPermissions(DefaultMemberPermissions.ENABLED) }).queue()
        }

        globalCommands.forEach { it.registerJDA(jda) }
        guildCommands.forEach { it.registerJDA(jda) }
    }
}
