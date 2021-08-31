package org.fuchss.deltabot.command

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
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
    private var scheduler: Scheduler = Scheduler()

    init {
        commands = ArrayList()

        commands.add(Shutdown())
        commands.add(Echo())
        commands.add(Admin(configuration))
        commands.add(State(configuration))
        commands.add(Erase())

        commands.add(Help(configuration, commands))
        commands.add(PersistentHelp(configuration, commands))
        commands.add(Roll())
        commands.add(Teams())
        commands.add(Summon(scheduler))
        commands.add(Reminder(configuration, scheduler))

        nameToCommand = commands.associateBy { m -> m.name }
    }

    override fun onEvent(event: GenericEvent) {
        if (event is ReadyEvent) {
            initCommands(event)
            this.scheduler.start()
            return
        }

        if (event !is SlashCommandEvent)
            return
        handleSlashCommand(event)
    }

    private fun initCommands(event: ReadyEvent) {
        // TODO Enable Admin Commands for Admins!

        val activeCommands = event.jda.retrieveCommands().complete()
        val newCommands = findNewCommandsAndDeleteOldOnes(activeCommands, true)
        for (cmd in newCommands)
            event.jda.upsertCommand(cmd).complete()

        for (guild in event.jda.guilds) {
            val activeCommandsGuild = getCommands(guild) ?: continue
            val newCommandsGuild = findNewCommandsAndDeleteOldOnes(activeCommandsGuild, false)
            for (cmd in newCommandsGuild)
                guild.upsertCommand(cmd).complete()
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

    private fun findNewCommandsAndDeleteOldOnes(activeCommands: List<Command>, global: Boolean): List<CommandData> {
        val newCommands: MutableList<CommandData> = ArrayList()
        val oldCommands: MutableList<Command> = ArrayList()

        for (command in this.commands) {
            val cmdData = command.createCommand()
            val foundCommand = findCommand(activeCommands, cmdData)

            if (foundCommand != null && global == command.isGlobal) {
                oldCommands.add(foundCommand)
                continue
            }

            if (global == command.isGlobal)
                newCommands.add(cmdData)
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

            if (cmd.options.size != cmdData.options.size)
                continue

            var optionEqual = true
            for (i in 0 until cmd.options.size) {
                val o1: Command.Option = cmd.options[i]
                val o2: OptionData = cmdData.options[i]

                if (o1.name != o2.name || o1.isRequired != o2.isRequired || o1.type != o2.type || o1.description != o2.description || o1.choices != o2.choices) {
                    optionEqual = false
                    break
                }

            }

            if (!optionEqual)
                continue

            return cmd
        }
        return null
    }

    private fun handleSlashCommand(event: SlashCommandEvent) {
        logger.debug(event.toString())
        val command = nameToCommand[event.name] ?: UnknownCommand()

        if (command.isAdminCommand && !isAdmin(event)) {
            event.reply("You are not an admin!").setEphemeral(true).complete()
            return
        }

        command.handle(event)
    }

    private fun isAdmin(event: SlashCommandEvent): Boolean {
        return event.guild?.owner?.user == event.user || configuration.isAdmin(event.user)
    }

    private class UnknownCommand : BotCommand {
        override val isAdminCommand: Boolean get() = false
        override val isGlobal: Boolean get() = error("Command shall only be used internally")
        override fun createCommand(): CommandData = error("Command shall only be used internally")

        override fun handle(event: SlashCommandEvent) {
            event.reply("Unknown command .. please contact the admin of the bot!").setEphemeral(true).complete()
        }
    }
}