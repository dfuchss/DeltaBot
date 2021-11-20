package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.admin.*
import org.fuchss.deltabot.command.user.*
import org.fuchss.deltabot.command.user.polls.*
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.fetchCommands
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.port.Session

/**
 * The registry for commands.
 * @param[configuration] the configuration of the bot
 * @param[dbLocation] the file location of the database
 * @param[scheduler] the scheduler of the bot
 * @param[session] the session / databse of the bot
 */
class CommandRegistry(private val configuration: BotConfiguration, dbLocation: String, scheduler: Scheduler, session: Session) : ICommandRegistry, EventListener {

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
        commands.add(Admin(configuration, this))
        commands.add(State(configuration, scheduler, session))
        commands.add(Erase())
        commands.add(Roles(session))
        commands.add(ResetStateAndCommands(configuration, dbLocation, session))
        commands.add(ServerRoles())
        commands.add(UnhideAll())

        commands.add(Language())
        commands.add(Help(configuration, this))
        commands.add(PersistentHelp(configuration, this))
        commands.add(Roll())
        commands.add(Teams())
        commands.add(Emojify())

        commands.add(Summon(pollAdmin, configuration, scheduler, session))
        commands.add(Reminder(configuration, scheduler, session))
        commands.add(WeekdayPoll(pollAdmin, scheduler, session))
        commands.add(SimplePoll(pollAdmin, scheduler, session))

        if (!configuration.hasAdmins()) {
            logger.info("Missing initial admin .. adding initial admin command ..")
            commands.add(InitialAdminCommand(configuration) { jda, u -> initialUser(jda, u) })
        }

        commands.filterIsInstance(GlobalCommand::class.java).forEach { gc -> globalCommands.add(gc) }
        commands.filterIsInstance(GuildCommand::class.java).forEach { gc -> guildCommands.add(gc) }
    }

    private fun initialUser(jda: JDA, u: User) {
        logger.info("Added initial admin $u")
        val command = globalCommands.filterIsInstance(InitialAdminCommand::class.java).firstOrNull() ?: return
        globalCommands.remove(command)
        updateHooks.forEach { r -> r.run() }

        jda.fetchCommands().find { c -> c.name == command.createCommand().name }?.delete()?.complete()
        fixCommandPermissions(jda, configuration, this::permissions, u)
    }

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
        var needFix = false

        needFix = needFix or initGlobalCommands(jda)
        needFix = needFix or initGuildCommands(jda)

        if (needFix) {
            logger.info("Fixing command permissions ..")
            fixCommandPermissions(jda, configuration, this::permissions)
        }

        for (cmd in globalCommands + guildCommands)
            cmd.registerJDA(jda)
    }

    private fun initGuildCommands(jda: JDA): Boolean {
        var needFix = false
        for (guild in jda.guilds) {
            val activeCommandsGuild = guild.fetchCommands()

            val commands = guildCommands.map { c -> c to c.createCommand(guild) }
            commands.forEach { (c, impl) -> nameToCommand[impl.name] = c }

            val newCommandsGuild = findNewCommandsAndDeleteOldOnes(activeCommandsGuild, commands)
            needFix = needFix || newCommandsGuild.isNotEmpty()

            for ((cmd, cmdData) in newCommandsGuild) {
                if (cmd.permissions == CommandPermissions.ALL) {
                    guild.upsertCommand(cmdData).complete()
                } else {
                    guild.upsertCommand(cmdData.setDefaultEnabled(false)).complete()
                }
            }
        }

        return needFix
    }

    private fun initGlobalCommands(jda: JDA): Boolean {
        var needFix = false
        val activeCommands = jda.fetchCommands()

        val commands = globalCommands.map { c -> c to c.createCommand() }
        commands.forEach { (c, impl) -> nameToCommand[impl.name] = c }

        val newCommands = findNewCommandsAndDeleteOldOnes(activeCommands, commands)
        needFix = needFix || newCommands.isNotEmpty()

        for ((_, cmdData) in newCommands)
            jda.upsertCommand(cmdData).complete()
        return needFix
    }

    private fun findNewCommandsAndDeleteOldOnes(activeCommands: List<Command>, commands: List<Pair<BotCommand, CommandData>>): List<Pair<BotCommand, CommandData>> {
        val newCommands: MutableList<Pair<BotCommand, CommandData>> = ArrayList()
        val oldCommands: MutableList<Command> = ArrayList()

        for ((command, cmdData) in commands) {
            val foundCommand = findCommand(activeCommands, cmdData)

            if (foundCommand != null) {
                oldCommands.add(foundCommand)
                continue
            }
            newCommands.add(command to cmdData)
        }

        // Delete old commands
        val toDelete = activeCommands.filter { c -> !oldCommands.contains(c) }
        for (cmd in toDelete) {
            cmd.delete().complete()
        }
        return newCommands
    }


    private fun findCommand(activeCommands: List<Command>, cmdData: CommandData): Command? {
        for (cmd in activeCommands) {
            if (cmd.name != cmdData.name)
                continue
            if (!cmd.options.optionEqualsData(cmdData.options))
                continue
            if (!cmd.subcommands.subcommandEqualsData(cmdData.subcommands))
                continue
            return cmd
        }
        return null
    }

    override fun permissions(command: Command): CommandPermissions {
        if (!nameToCommand.containsKey(command.name))
            error("Command ${command.name} not suitable registered! Permissions not found!")

        return nameToCommand[command.name]!!.permissions
    }

    override fun nameToCommand(): Map<String, BotCommand> = nameToCommand.toMap()
}


private fun MutableList<Command.Subcommand>.subcommandEqualsData(subcommands: List<SubcommandData>) =
    this.size == subcommands.size && this.zip(subcommands).all { (c1, c2) -> c1.name == c2.name && c1.options.optionEqualsData(c2.options) }

private fun MutableList<Command.Option>.optionEqualsData(options: List<OptionData>) = this.size == options.size && this.zip(options).all { (o1, o2) -> o1.optionEqualsData(o2) }

private fun Command.Option.optionEqualsData(o2: OptionData) =
    this.name == o2.name && this.isRequired == o2.isRequired && this.type == o2.type && this.description == o2.description && this.choices == o2.choices
