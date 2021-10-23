package org.fuchss.deltabot.command.user

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import org.fuchss.deltabot.command.BotCommand
import org.fuchss.deltabot.command.CommandPermissions
import org.fuchss.deltabot.utils.extensions.translate
import kotlin.random.Random

/**
 * A [BotCommand] that rolls a dice.
 */
class Roll : BotCommand {
    override val permissions: CommandPermissions get() = CommandPermissions.ALL
    override val isGlobal: Boolean get() = true

    override fun createCommand(): CommandData {
        val command = CommandData("roll", "roll a dice")
        command.addOptions(OptionData(OptionType.INTEGER, "sides", "the amount of sides of the dice (default: 6)").setRequired(false))
        return command
    }

    override fun handle(event: SlashCommandEvent) {
        val sides = event.getOption("sides")?.asLong?.toInt() ?: 6
        if (sides < 2) {
            event.reply("A dice shall have at least 2 sides ..".translate(event)).setEphemeral(true).queue()
            return
        }

        val choice = Random.nextInt(1, sides + 1)
        event.reply("Your roll was #".translate(event, choice)).queue()
    }
}