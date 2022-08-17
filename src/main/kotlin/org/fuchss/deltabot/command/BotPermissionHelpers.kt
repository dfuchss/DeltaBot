package org.fuchss.deltabot.command

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.deltabot.utils.extensions.fetchOwner
import org.fuchss.objectcasket.objectpacker.port.Session

fun isGuildAdmin(event: SlashCommandInteraction, configuration: BotConfiguration, session: Session): Boolean {
    if (event.guild?.fetchOwner() == event.user) return true

    if (configuration.isAdmin(event.user)) return true

    val guild = GuildDTO.findDBGuild(session, event.guild!!) ?: return false
    return guild.admins.any { admin -> admin.discordId == event.user.id }
}
