package org.fuchss.deltabot.cognitive.dialogmanagement

import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.command.CommandRegistry
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate

class DialogListener(private val configuration: Configuration, private val commandRegistry: CommandRegistry) : EventListener {
    private val user2instance = mutableMapOf<User, UserBotInstance>()

    override fun onEvent(event: GenericEvent) {
        if (event !is MessageReceivedEvent)
            return

        if (event.message.author.isBot)
            return

        var respond = false
        respond = respond || event.channelType == ChannelType.PRIVATE
        respond = respond || event.jda.selfUser in event.message.mentionedUsers
        respond = respond || event.message.referencedMessage?.author == event.jda.selfUser

        if (!respond) {
            return
        }

        if (configuration.disableNlu) {
            event.message.reply("NLU is disabled by the Bot Admin. I can't interpret your talk".translate(event)).queue()
            return
        }

        val instance = user2instance.getOrPut(event.message.author) { UserBotInstance(configuration, commandRegistry) }
        instance.handle(event.message, event.language())
    }
}