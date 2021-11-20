package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.utils.extensions.fetchCommands

/**
 * Fix the permissions of different commands as user config have been changed.
 * @param[jda] the JDA
 * @param[configuration] the configuration of the bot
 * @param[permissions] a function that determines the permission of a command
 * @param[changedUser] the user that have been changed
 */
fun fixCommandPermissions(jda: JDA, configuration: BotConfiguration, permissions: (Command) -> CommandPermissions, changedUser: User? = null) {
    val globalAdmins = configuration.getAdmins(jda)
    for (guild in jda.guilds) {
        val guildCommands = guild.fetchCommands()
        val guildAdmins = configuration.getAdminMembersOfGuildWithGlobalAdmins(guild)

        allowCommands(guild, guildAdmins, changedUser, guildCommands.filter { gc -> permissions(gc) == CommandPermissions.GUILD_ADMIN })
        allowCommands(guild, globalAdmins, changedUser, guildCommands.filter { gc -> permissions(gc) == CommandPermissions.ADMIN })
    }
}

private fun allowCommands(guild: Guild, admins: List<User>, changedUser: User?, commands: List<Command>) {
    if (changedUser == null) {
        for (admin in admins) {
            commands.forEach { c -> guild.updateCommandPrivilegesById(c.id, CommandPrivilege.enable(admin)).queue() }
        }
    } else {
        val privilege = if (changedUser in admins) CommandPrivilege.enable(changedUser) else CommandPrivilege.disable(changedUser)
        commands.forEach { c -> guild.updateCommandPrivilegesById(c.id, privilege).queue() }
    }
}
