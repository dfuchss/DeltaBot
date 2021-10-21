package org.fuchss.deltabot.command

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege
import org.fuchss.deltabot.DeltaBotConfiguration

/**
 * Fix the permissions of different commands as user config have been changed.
 * @param[jda] the JDA
 * @param[configuration] the configuration of the bot
 * @param[commands] the commands to consider
 * @param[changedUser] the user that have been changed
 */
fun fixCommandPermissions(jda: JDA, configuration: DeltaBotConfiguration, commands: List<BotCommand>, changedUser: User? = null) {
    val globalAdminCommands = commands.filter { c -> c.permissions == CommandPermissions.ADMIN && !c.isGlobal }
    val guildAdminCommands = commands.filter { c -> c.permissions == CommandPermissions.GUILD_ADMIN && !c.isGlobal }

    val globalAdmins = configuration.getAdmins(jda)

    for (guild in jda.guilds) {
        val guildCommands = guild.retrieveCommands().complete()
        val guildAdmins = configuration.getAdminsMembersOfGuild(guild)
        allowCommands(guild, guildAdmins, changedUser, guildCommands.filter { gc -> guildAdminCommands.any { ac -> ac.name == gc.name } })
        allowCommands(guild, globalAdmins, changedUser, guildCommands.filter { gc -> globalAdminCommands.any { ac -> ac.name == gc.name } })
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
