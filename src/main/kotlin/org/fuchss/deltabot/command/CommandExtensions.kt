package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege
import org.fuchss.deltabot.Configuration

fun fixCommandPermissions(jda: JDA, configuration: Configuration, commands: List<BotCommand>, changedUser: User? = null) {
    val adminCommands = commands.filter { c -> c.isAdminCommand && !c.isGlobal }
    for (guild in jda.guilds) {
        val admins = configuration.getAdminsMembersOfGuild(guild)
        val guildCommands = guild.retrieveCommands().complete().filter { gc -> adminCommands.any { ac -> ac.name == gc.name } }

        if (changedUser == null) {
            for (admin in admins) {
                guildCommands.forEach { c -> guild.updateCommandPrivilegesById(c.id, CommandPrivilege.enable(admin)).complete() }
            }
        } else {
            val privilege = if (changedUser in admins) CommandPrivilege.enable(changedUser) else CommandPrivilege.disable(changedUser)
            guildCommands.forEach { c -> guild.updateCommandPrivilegesById(c.id, privilege).complete() }
        }
    }
}
