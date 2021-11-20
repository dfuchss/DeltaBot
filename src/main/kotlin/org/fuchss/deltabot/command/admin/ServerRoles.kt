package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.translate

/**
 * A [BotCommand] that manages (add or remove) the roles of a server with linked channels.
 */
class ServerRoles : GuildCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN
    
    override fun createCommand(guild: Guild): CommandData {
        val command = CommandData("server-roles", "manage server roles & channels")
        command.addSubcommands(
            SubcommandData("add", "add a new role with text and voice channel").addOptions(
                OptionData(OptionType.STRING, "name", "the name of the role").setRequired(true),
                OptionData(OptionType.BOOLEAN, "text", "create text channel? (default: true)").setRequired(false),
                OptionData(OptionType.BOOLEAN, "voice", "create voice channel? (default: true)").setRequired(false)
            ),

            SubcommandData("del", "remove role and connected text and voice channels").addOptions(
                OptionData(OptionType.ROLE, "role", "the role to delete").setRequired(true)
            )
        )
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        when (event.subcommandName) {
            "add" -> handleAdd(event)
            "del" -> handleDelete(event)
            else -> event.reply("You must use a subcommand".translate(event)).setEphemeral(true).queue()
        }
    }

    private fun handleAdd(event: SlashCommandEvent) {
        val name = event.getOption("name")?.asString?.trim() ?: ""
        val text = event.getOption("text")?.asBoolean ?: true
        val voice = event.getOption("voice")?.asBoolean ?: true

        if (name.isBlank() || !name.matches(Regex("[A-Za-z0-9 ]+"))) {
            event.reply("You need to supply a name with no special characters!".translate(event)).setEphemeral(true).queue()
            return
        }

        val guild = event.guild!!

        if (guild.getRolesByName(name, true).isNotEmpty()) {
            event.reply("I've found some role that matches your provided name!".translate(event)).setEphemeral(true).queue()
            return
        }

        val hook = event.deferReply().complete()
        val everyone = guild.publicRole
        val role = guild.createRole().setName(name).setMentionable(true).complete()
        val group = guild.createCategory(name)
            .addMemberPermissionOverride(guild.selfMember.idLong, listOf(Permission.VIEW_CHANNEL), null)
            .addRolePermissionOverride(role.idLong, listOf(Permission.VIEW_CHANNEL), null)
            .addRolePermissionOverride(everyone.idLong, null, listOf(Permission.VIEW_CHANNEL))
            .complete()

        if (text)
            group.createTextChannel(name).queue()

        if (voice)
            group.createVoiceChannel(name).queue()

        hook.editOriginal("Welcome # on the server!".translate(event, role.asMention)).queue()
    }

    private fun handleDelete(event: SlashCommandEvent) {
        val role = event.getOption("role")?.asRole

        if (role == null) {
            event.reply("You need to supply a role!".translate(event)).setEphemeral(true).queue()
            return
        }

        val guild = event.guild!!
        val hook = event.reply("Purging # role on the server!".translate(event, role.name)).complete()

        try {

            val categories = guild.getCategoriesByName(role.name, false)
            for (cat in categories) {
                cat.textChannels.forEach { c -> c.delete().queue() }
                cat.voiceChannels.forEach { c -> c.delete().queue() }
                cat.delete().queue()
            }
            role.delete().queue()

        } catch (e: PermissionException) {
            logger.error(e.message, e)
            hook.editOriginal("I'm not allowed to do that!".translate(event)).queue()
        }
    }
}