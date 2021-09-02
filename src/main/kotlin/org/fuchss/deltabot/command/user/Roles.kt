package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.Button
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.utils.*

class Roles : BotCommand, EventListener {
    override val isAdminCommand: Boolean get() = true
    override val isGlobal: Boolean get() = false

    private val rolesState = RolesState().load("./states/roles.json")

    companion object {
        private const val switcherText = "**Please choose your roles on this server :)**"
        private const val noRoles = "*At the moment, no roles can be chosen .. please wait for your guild leader :)*"
    }

    override fun createCommand(): CommandData {
        val command = CommandData("roles", "manage the role changer message of this guild")
        command.addSubcommands(
            SubcommandData("init", "creates the role changer message in this channel"),
            SubcommandData("add", "adds an emoji for a specific role").addOptions(
                OptionData(OptionType.STRING, "emoji", "the emoji for the role").setRequired(true),
                OptionData(OptionType.ROLE, "role", "the role that shall be added to the message").setRequired(true)
            ),
            SubcommandData("del", "remove an emoji from the role changer message").addOptions(
                OptionData(OptionType.STRING, "emoji", "the emoji to delete").setRequired(true)
            ),
            SubcommandData("purge", "remove the whole message from the guild")
        )
        return command
    }

    override fun registerJDA(jda: JDA) {
        jda.addEventListener(this)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonClickEvent)
            return
        if (!rolesState.isGuildMessage(event.guild?.id ?: "", event.channel.id, event.messageId))
            return

        handleRolesClick(event)
    }


    override fun handle(event: SlashCommandEvent) {
        when (event.subcommandName) {
            "init" -> handleInit(event)
            "add" -> handleAdd(event)
            "del" -> handleDel(event)
            "purge" -> handlePurge(event)
            else -> event.reply("You must use a subcommand").setEphemeral(true).complete()
        }
    }

    private fun handleInit(event: SlashCommandEvent) {
        val guild = event.guild!!
        if (rolesState.hasRoleMessage(guild)) {
            event.reply("Role message already found").setEphemeral(true).complete()
            return
        }

        val initialText = "$switcherText\n$noRoles"
        val msg = event.channel.sendMessage(initialText).complete()
        rolesState.addRoleMessage(guild, msg)
        event.reply("Role message created ..").setEphemeral(true).complete()
    }

    private fun handlePurge(event: SlashCommandEvent) {
        val guild = event.guild!!
        if (!rolesState.hasRoleMessage(guild)) {
            event.reply("No role message found").setEphemeral(true).complete()
            return
        }

        val state = rolesState.getGuildState(guild)!!
        rolesState.removeRoleMessage(guild)

        val channel = guild.getTextChannelById(state.channelId)
        val message = channel?.retrieveMessageById(state.messageId)?.complete()
        message?.delete()?.complete()

        event.reply("Role message deleted ..").setEphemeral(true).complete()
    }

    private fun handleAdd(event: SlashCommandEvent) {
        val guild = event.guild!!
        if (!rolesState.hasRoleMessage(guild)) {
            event.reply("No role message found").setEphemeral(true).complete()
            return
        }

        val emoji = event.getOption("emoji")?.asString
        val role = event.getOption("role")?.asRole
        if (emoji == null || role == null) {
            event.reply("You must provide both .. role and emoji ..").setEphemeral(true).complete()
            return
        }
        val guildState = rolesState.getGuildState(guild)!!

        if (role.asMention in guildState.emojiToRole.values) {
            event.reply("Role already mapped ..").setEphemeral(true).complete()
            return
        }

        val emojis = findAllEmojis(emoji)
        if (emojis.size != 1) {
            event.reply("I've found ${emojis.size} emojis :(").setEphemeral(true).complete()
            return
        }

        if (guildState.emojiToRole.containsKey(emojis[0])) {
            event.reply("Emoji already used ..").setEphemeral(true).complete()
            return
        }

        guildState.emojiToRole[emojis[0]] = role.asMention
        rolesState.store()

        updateGuild(guild, guildState)
        event.reply("Updated role message").setEphemeral(true).complete()
    }


    private fun handleDel(event: SlashCommandEvent) {
        val guild = event.guild!!
        if (!rolesState.hasRoleMessage(guild)) {
            event.reply("No role message found").setEphemeral(true).complete()
            return
        }
        val emoji = event.getOption("emoji")?.asString


        if (emoji == null) {
            event.reply("You must provide an emoji ..").setEphemeral(true).complete()
            return
        }

        val emojis = findAllEmojis(emoji)
        if (emojis.size != 1) {
            event.reply("I've found ${emojis.size} emojis :(").setEphemeral(true).complete()
            return
        }

        val guildState = rolesState.getGuildState(guild)!!
        if (emojis[0] !in guildState.emojiToRole.keys) {
            event.reply("I've found no mapping to this emoji").setEphemeral(true).complete()
            return
        }

        guildState.emojiToRole.remove(emojis[0])
        rolesState.store()

        updateGuild(guild, guildState)
        event.reply("Updated role message").setEphemeral(true).complete()
    }

    private fun handleRolesClick(event: ButtonClickEvent) {
        val guild = event.guild!!
        val state = rolesState.getGuildState(guild)!!
        val clickedId = event.button?.id ?: ""

        val roleMention = state.emojiToRole[clickedId]
        if (roleMention == null) {
            event.reply("I can't find the role :( .. ask your bot admin").setEphemeral(true).complete()
            return
        }

        val roleId = roleMention.drop("<@&".length).dropLast(">".length)
        val role = guild.getRoleById(roleId)
        val member = event.member!!

        try {
            if (role in member.roles) {
                guild.removeRoleFromMember(member, role!!).complete()
                event.reply("I've removed ${role.asMention} from your roles").setEphemeral(true).complete()
            } else {
                guild.addRoleToMember(member, role!!).complete()
                event.reply("I've added ${role.asMention} to your roles").setEphemeral(true).complete()
            }
        } catch (e: Exception) {
            logger.error(e.message)
            event.reply("I'm not allowed to do that.").setEphemeral(true).complete()
        }
    }

    private fun updateGuild(guild: Guild, guildState: GuildState) {
        var message = "$switcherText\n$noRoles"
        var buttons = emptyList<Button>()

        if (guildState.emojiToRole.isNotEmpty()) {
            message = "$switcherText\n\n${guildState.emojiToRole.entries.joinToString(separator = "\n", transform = { (emoji, roleMention) -> "$emoji -> $roleMention" })}"
            message += "\n\nPlease choose buttons to select your roles .."
            buttons = guildState.emojiToRole.keys.map { e -> loadEmojiButton(guild, e) }
        }

        val msg = guild.getTextChannelById(guildState.channelId)!!.retrieveMessageById(guildState.messageId).complete()!!
        msg.editMessage(message).setActionRows(buttons.toActionRows()).complete()

    }

    private fun loadEmojiButton(guild: Guild, emoji: String): Button {
        if (!discordEmojiRegex.matches(emoji))
            return Button.secondary(emoji, Emoji.fromUnicode(emoji))

        val emojiId = emoji.split(":")[2].dropLast(1)
        val guildEmoji = guild.retrieveEmoteById(emojiId).complete()
        return Button.secondary(guildEmoji.asMention, Emoji.fromEmote(guildEmoji))
    }


    private data class RolesState(
        var guild2Message: MutableMap<String, GuildState> = mutableMapOf()
    ) : Storable() {

        fun addRoleMessage(guild: Guild, message: Message) {
            guild2Message[guild.id] = GuildState(message.channel.id, message.id)
            this.store()
        }

        fun removeRoleMessage(guild: Guild) {
            guild2Message.remove(guild.id)
            this.store()
        }

        fun hasRoleMessage(guild: Guild) = guild2Message.containsKey(guild.id)
        fun getGuildState(guild: Guild) = guild2Message[guild.id]

        fun isGuildMessage(guildId: String, channelId: String, messageId: String): Boolean {
            if (!guild2Message.containsKey(guildId))
                return false
            val guildState = guild2Message[guildId]
            return guildState != null && guildState.channelId == channelId && guildState.messageId == messageId
        }
    }

    private data class GuildState(
        var channelId: String,
        var messageId: String,
        var emojiToRole: MutableMap<String, String> = mutableMapOf()
    )
}


