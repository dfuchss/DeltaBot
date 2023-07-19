package org.fuchss.deltabot.command.admin

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.translate
import java.awt.Color

/**
 * A [BotCommand] that manages (add or remove) the roles of a server with linked channels.
 */
class Channels : GuildCommand {
    companion object {
        val COLORS: Map<String, Int> = mapOf(
            "White" to Color.white.rgb,
            "Light Gray" to Color.lightGray.rgb,
            "Gray" to Color.gray.rgb,
            "Dark Gray" to Color.darkGray.rgb,
            "Black" to Color.black.rgb,
            "Red" to Color.red.rgb,
            "Pink" to Color.pink.rgb,
            "Orange" to Color.orange.rgb,
            "Yellow" to Color.yellow.rgb,
            "Green" to Color.green.rgb,
            "Magenta" to Color.magenta.rgb,
            "Cyan" to Color.cyan.rgb,
            "Blue" to Color.blue.rgb
        )
    }

    override val permissions: CommandPermissions get() = CommandPermissions.GUILD_ADMIN

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("channels", "manage server roles & channels")
        command.addSubcommands(
            SubcommandData("new", "add a new role with text and voice channel").addOptions(
                OptionData(OptionType.STRING, "name", "the name of the role").setRequired(true),
                OptionData(OptionType.BOOLEAN, "only-text", "only create a text channel (default: false)").setRequired(false),
                OptionData(OptionType.STRING, "color", "the color to associate with the new role (default: none)").setRequired(false)
                    .addChoices(COLORS.toSortedMap().map { c -> Command.Choice(c.key, c.value.toString()) })
            ),

            SubcommandData("set", "add a new channel with a specific name for a certain role").addOptions(
                OptionData(OptionType.ROLE, "role", "the name of the role").setRequired(true),
                OptionData(OptionType.STRING, "name", "the name of the channel").setRequired(true),
                OptionData(OptionType.BOOLEAN, "only-text", "only create a text channel (default: false)").setRequired(false)
            )
        )
        return command
    }

    override fun handle(event: SlashCommandInteraction) {
        when (event.subcommandName) {
            "new" -> handleNew(event)
            "set" -> handleSet(event)
            else -> event.reply("You must use a subcommand".translate(event)).setEphemeral(true).queue()
        }
    }

    private fun handleNew(event: SlashCommandInteraction) {
        val name = event.getOption("name")?.asString?.trim() ?: ""
        val onlyText = event.getOption("only-text")?.asBoolean ?: false
        val color = event.getOption("color")?.asString?.toIntOrNull()

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
        val role = guild.createRole().setName(name).setMentionable(true).setColor(color).complete()

        createChannel(guild, role, name, onlyText)
        hook.editOriginal("Welcome # on the server!".translate(event, role.asMention)).queue()
    }

    private fun handleSet(event: SlashCommandInteraction) {
        val role = event.getOption("role")?.asRole!!
        val name = event.getOption("name")?.asString?.trim() ?: ""
        val onlyText = event.getOption("only-text")?.asBoolean ?: false
        val hook = event.deferReply().complete()
        val channel = createChannel(event.guild!!, role, name, onlyText)
        hook.editOriginal("Welcome # on the server!".translate(event, channel.asMention)).queue()
    }

    private fun createChannel(guild: Guild, role: Role, name: String, onlyText: Boolean): StandardGuildChannel {
        val everyone = guild.publicRole
        val guildRoleOfBot = guild.getRoleByBot(guild.jda.selfUser)!!

        return if (onlyText) {
            guild.createTextChannel(name).addRolePermissionOverride(role.idLong, listOf(Permission.VIEW_CHANNEL), null) //
                .addRolePermissionOverride(
                    guildRoleOfBot.idLong,
                    listOf(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MESSAGE_MANAGE),
                    null
                ) //
                .addRolePermissionOverride(everyone.idLong, null, listOf(Permission.VIEW_CHANNEL)) //
                .complete()
        } else {
            guild.createVoiceChannel(name).addRolePermissionOverride(role.idLong, listOf(Permission.VIEW_CHANNEL), null) //
                .addRolePermissionOverride(
                    guildRoleOfBot.idLong,
                    listOf(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL, Permission.MESSAGE_MANAGE),
                    null
                ) //
                .addRolePermissionOverride(everyone.idLong, null, listOf(Permission.VIEW_CHANNEL)) //
                .complete()
        }
    }
}
