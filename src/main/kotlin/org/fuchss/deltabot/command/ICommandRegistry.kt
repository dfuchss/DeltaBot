package org.fuchss.deltabot.command

import net.dv8tion.jda.api.interactions.commands.Command

interface ICommandRegistry {
    fun permissions(command: Command): CommandPermissions

    fun registerUpdateHook(r: Runnable)

    fun nameToCommand(): Map<String, BotCommand>
}
