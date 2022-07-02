package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.command.GuildCommand
import org.fuchss.deltabot.utils.extensions.discordEmojiRegex
import org.fuchss.deltabot.utils.extensions.fetchMessage
import org.fuchss.deltabot.utils.extensions.findAllEmojis
import org.fuchss.deltabot.utils.extensions.internalLanguage
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.toActionRows
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.objectcasket.port.Session
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

/**
 * A [BotCommand] that provides tools to create messages that manages the [Roles][Role] of a [Member].
 */
class Roles(private val session: Session) : GuildCommand, EventListener {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL

    private val guildRoles: MutableSet<GuildRoleState> = mutableSetOf()

    companion object {
        private const val switcherText = "**Please choose your roles on this server :)**"
        private const val noRoles = "*At the moment, no roles can be chosen .. please wait for your guild leader :)*"
    }

    override fun createCommand(guild: Guild): SlashCommandData {
        val command = Commands.slash("roles", "manage the role changer message of this guild")
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
        initRoles()
    }

    private fun initRoles() {
        val dbRoles = session.getAllObjects(GuildRoleState::class.java)
        logger.info("Loaded ${dbRoles.size} guild role objects from the DB")
        guildRoles.addAll(dbRoles)
    }

    override fun onEvent(event: GenericEvent) {
        if (event !is ButtonInteractionEvent) {
            return
        }
        if (!isGuildMessage(event.guild?.id ?: "", event.channel.id, event.messageId)) {
            return
        }

        handleRolesClick(event)
    }

    override fun handle(event: SlashCommandInteraction) {
        when (event.subcommandName) {
            "init" -> handleInit(event)
            "add" -> handleAdd(event)
            "del" -> handleDel(event)
            "purge" -> handlePurge(event)
            else -> event.reply("You must use a subcommand".translate(event)).setEphemeral(true).queue()
        }
    }

    private fun handleInit(event: SlashCommandInteraction) {
        val guild = event.guild!!
        if (hasRoleMessage(guild)) {
            event.reply("Role message already found".translate(event)).setEphemeral(true).queue()
            return
        }

        val initialText = "$switcherText\n$noRoles"
        val msg = event.channel.sendMessage(initialText).complete()
        addRoleMessage(guild, msg)
        event.reply("Role message created ..".translate(event)).setEphemeral(true).queue()
    }

    private fun handlePurge(event: SlashCommandInteraction) {
        val guild = event.guild!!
        if (!hasRoleMessage(guild)) {
            event.reply("No role message found".translate(event)).setEphemeral(true).queue()
            return
        }

        val state = getGuildState(guild)!!
        removeRoleMessage(guild)

        val message = guild.fetchMessage(state.channelId, state.messageId)
        message?.delete()?.queue()

        event.reply("Role message deleted ..".translate(event)).setEphemeral(true).queue()
    }

    private fun handleAdd(event: SlashCommandInteraction) {
        val guild = event.guild!!
        if (!hasRoleMessage(guild)) {
            event.reply("No role message found".translate(event)).setEphemeral(true).queue()
            return
        }

        val emoji = event.getOption("emoji")?.asString
        val role = event.getOption("role")?.asRole
        if (emoji == null || role == null) {
            event.reply("You must provide both .. role and emoji ..".translate(event)).setEphemeral(true).queue()
            return
        }
        val guildState = getGuildState(guild)!!

        if (role.asMention in guildState.emojiToRole.values) {
            event.reply("Role already mapped ..".translate(event)).setEphemeral(true).queue()
            return
        }

        val emojis = findAllEmojis(emoji)
        if (emojis.size != 1) {
            event.reply("I've found # emojis :(".translate(event, emojis.size)).setEphemeral(true).queue()
            return
        }

        if (guildState.emojiToRole.containsKey(emojis[0])) {
            event.reply("Emoji already used ..".translate(event)).setEphemeral(true).queue()
            return
        }

        guildState.setEmojiToRole(emojis[0], role.asMention)
        session.persist(guildState)

        updateGuild(guild, guildState)
        event.reply("Updated role message".translate(event)).setEphemeral(true).queue()
    }

    private fun handleDel(event: SlashCommandInteraction) {
        val guild = event.guild!!
        if (!hasRoleMessage(guild)) {
            event.reply("No role message found".translate(event)).setEphemeral(true).queue()
            return
        }
        val emoji = event.getOption("emoji")?.asString

        if (emoji == null) {
            event.reply("You must provide an emoji ..".translate(event)).setEphemeral(true).queue()
            return
        }

        val emojis = findAllEmojis(emoji)
        if (emojis.size != 1) {
            event.reply("I've found # emojis :(".translate(event, emojis.size)).setEphemeral(true).queue()
            return
        }

        val guildState = getGuildState(guild)!!
        if (emojis[0] !in guildState.emojiToRole.keys) {
            event.reply("I've found no mapping to this emoji".translate(event)).setEphemeral(true).queue()
            return
        }

        guildState.removeEmoji(emojis[0])
        session.persist(guildState)

        updateGuild(guild, guildState)
        event.reply("Updated role message".translate(event)).setEphemeral(true).queue()
    }

    private fun handleRolesClick(event: ButtonInteractionEvent) {
        val guild = event.guild!!
        val state = getGuildState(guild)!!
        val clickedId = event.button.id ?: ""

        val roleMention = state.emojiToRole[clickedId]
        if (roleMention == null) {
            event.reply("I can't find the role :( .. ask your bot admin".translate(event)).setEphemeral(true).queue()
            return
        }

        val roleId = roleMention.drop("<@&".length).dropLast(">".length)
        val role = guild.getRoleById(roleId)
        val member = event.member!!

        try {
            if (role in member.roles) {
                guild.removeRoleFromMember(member, role!!).complete()
                event.reply("I've removed # from your roles".translate(event, role.asMention)).setEphemeral(true).queue()
            } else {
                guild.addRoleToMember(member, role!!).complete()
                event.reply("I've added # to your roles".translate(event, role.asMention)).setEphemeral(true).queue()
            }
        } catch (e: Exception) {
            logger.error(e.message)
            event.reply("I'm not allowed to do that.".translate(event)).setEphemeral(true).queue()
        }
    }

    private fun updateGuild(guild: Guild, guildState: GuildRoleState) {
        var message = "${switcherText.translate(guild.internalLanguage())}\n${noRoles.translate(guild.internalLanguage())}"
        var buttons = emptyList<Button>()

        val emoji2Role = guildState.emojiToRole.entries.sortedBy { (_, mention) -> guild.getRoleById(mention.drop("<@&".length).dropLast(">".length))?.name ?: "" }
        if (emoji2Role.isNotEmpty()) {
            message = "${switcherText.translate(guild.internalLanguage())}\n\n${
            emoji2Role.joinToString(separator = "\n", transform = { (emoji, roleMention) -> "$emoji → $roleMention" })
            }"
            message += "\n\n" + "Please choose buttons to select your roles ..".translate(guild.internalLanguage())
            buttons = emoji2Role.map { (e, _) -> loadEmojiButton(guild, e) }
        }

        val msg = guild.fetchMessage(guildState.channelId, guildState.messageId)!!
        msg.editMessage(message).setActionRows(buttons.toActionRows()).queue()
    }

    private fun loadEmojiButton(guild: Guild, emoji: String): Button {
        if (!discordEmojiRegex.matches(emoji)) {
            return Button.secondary(emoji, Emoji.fromUnicode(emoji))
        }

        val emojiId = emoji.split(":")[2].dropLast(1)
        val guildEmoji = guild.retrieveEmojiById(emojiId).complete()
        return Button.secondary(guildEmoji.asMention, guildEmoji)
    }

    private fun isGuildMessage(guildId: String, channelId: String, messageId: String): Boolean =
        guildRoles.any { gr -> gr.guildId == guildId && gr.channelId == channelId && gr.messageId == messageId }

    private fun hasRoleMessage(guild: Guild): Boolean = guildRoles.any { gr -> gr.guildId == guild.id }

    private fun getGuildState(guild: Guild) = guildRoles.find { gr -> gr.guildId == guild.id }

    private fun addRoleMessage(guild: Guild, msg: Message) {
        val state = GuildRoleState(guild.id, msg.channel.id, msg.id)
        session.persist(state)
        guildRoles.add(state)
    }

    private fun removeRoleMessage(guild: Guild) {
        val state = getGuildState(guild) ?: return
        session.delete(state)
        guildRoles.remove(state)
    }

    @Entity
    @Table(name = "GuildRole")
    class GuildRoleState {
        @Id
        @GeneratedValue
        var id: Int? = null
        var guildId: String = ""
        var channelId: String = ""
        var messageId: String = ""

        @Column(columnDefinition = "JSON")
        var emojiToRole: MutableMap<String, String> = mutableMapOf()

        constructor()

        constructor(guildId: String, channelId: String, messageId: String) {
            this.guildId = guildId
            this.channelId = channelId
            this.messageId = messageId
        }

        fun setEmojiToRole(emoji: String, roleAsMention: String) {
            emojiToRole[emoji] = roleAsMention
        }

        fun removeEmoji(emoji: String) {
            emojiToRole.remove(emoji)
        }
    }
}
