package org.fuchss.deltabot.command

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.ShutdownEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.admin.*
import org.fuchss.deltabot.command.user.*
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.logger

class CommandHandler(private val configuration: Configuration) : EventListener {
    private val commands: MutableList<BotCommand>
    private val nameToCommand: Map<String, BotCommand>
    private val scheduler: Scheduler = Scheduler()

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

        commands.add(Language())
        commands.add(Help(configuration, commands))
        commands.add(PersistentHelp(configuration, commands))
        commands.add(Roll())
        commands.add(Teams())
        commands.add(Summon(configuration, scheduler))
        commands.add(Reminder(configuration, scheduler))

        nameToCommand = commands.associateBy { m -> m.name }
    }

    override fun onEvent(event: GenericEvent) {
        if (event is ReadyEvent) {
            initCommands(event)
            this.scheduler.start()
            return
        }

        if (event is ShutdownEvent) {
            this.scheduler.stop()
            return
        }

        if (event !is SlashCommandEvent)
            return
        handleSlashCommand(event)
    }

    private fun initCommands(event: ReadyEvent) {
        var needFix = false

        val activeCommands = event.jda.retrieveCommands().complete()
        val newCommands = findNewCommandsAndDeleteOldOnes(activeCommands, true)
        needFix = needFix || newCommands.isNotEmpty()

        for ((_, cmdData) in newCommands)
            event.jda.upsertCommand(cmdData).complete()

        for (guild in event.jda.guilds) {
            val activeCommandsGuild = getCommands(guild) ?: continue
            val newCommandsGuild = findNewCommandsAndDeleteOldOnes(activeCommandsGuild, false)
            needFix = needFix || newCommandsGuild.isNotEmpty()

            for ((cmd, cmdData) in newCommandsGuild) {
                if (cmd.permissions == CommandPermissions.ALL) {
                    guild.upsertCommand(cmdData).complete()
                } else {
                    guild.upsertCommand(cmdData.setDefaultEnabled(false)).complete()
                }
            }
        }
        if (needFix) {
            logger.info("Fixing command permissions ..")
            fixCommandPermissions(event.jda, configuration, commands)
        }

        for (cmd in commands)
            cmd.registerJDA(event.jda)
    }

    private fun getCommands(guild: Guild): List<Command>? {
        return try {
            guild.retrieveCommands().complete()
        } catch (e: Exception) {
            logger.error(guild.name + ": " + e.message)
            null
        }
    }

    private fun findNewCommandsAndDeleteOldOnes(activeCommands: List<Command>, global: Boolean): List<Pair<BotCommand, CommandData>> {
        val newCommands: MutableList<Pair<BotCommand, CommandData>> = ArrayList()
        val oldCommands: MutableList<Command> = ArrayList()

        for (command in this.commands) {
            val cmdData = command.createCommand()
            val foundCommand = findCommand(activeCommands, cmdData)

            if (foundCommand != null && global == command.isGlobal) {
                oldCommands.add(foundCommand)
                continue
            }

            if (global == command.isGlobal)
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

            val subcommandsEqual =
                cmd.subcommands.size == cmdData.subcommands.size && cmd.subcommands.zip(cmdData.subcommands).all { (c1, c2) -> c1.name == c2.name && c1.options.optionEqualsData(c2.options) }

            if (!subcommandsEqual)
                continue

            return cmd
        }
        return null
    }

    private fun handleSlashCommand(event: SlashCommandEvent) {
        logger.debug(event.toString())
        val command = nameToCommand[event.name] ?: UnknownCommand()

        if (command.permissions == CommandPermissions.ADMIN && !isAdmin(event)) {
            event.reply("You are not an admin!").setEphemeral(true).queue()
            return
        }

        if (event.guild != null && command.permissions == CommandPermissions.GUILD_ADMIN && event.user !in configuration.getAdminsMembersOfGuild(event.guild!!)) {
            event.reply("You are not an admin!").setEphemeral(true).queue()
            return
        }

        command.handle(event)
    }

    private fun isAdmin(event: SlashCommandEvent): Boolean {
        return event.guild?.owner?.user == event.user || configuration.isAdmin(event.user)
    }

    private class UnknownCommand : BotCommand {
        override val permissions: CommandPermissions get() = CommandPermissions.ALL
        override val isGlobal: Boolean get() = error("Command shall only be used internally")
        override fun createCommand(): CommandData = error("Command shall only be used internally")

        override fun handle(event: SlashCommandEvent) {
            event.reply("Unknown command .. please contact the admin of the bot!").setEphemeral(true).complete()
        }
    }
}

private fun MutableList<Command.Option>.optionEqualsData(options: List<OptionData>) = this.size == options.size && this.zip(options).all { (o1, o2) -> o1.optionEqualsData(o2) }

private fun Command.Option.optionEqualsData(o2: OptionData) =
    this.name == o2.name && this.isRequired == o2.isRequired && this.type == o2.type && this.description == o2.description && this.choices == o2.choices
