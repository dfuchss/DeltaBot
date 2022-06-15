package org.fuchss.deltabot.cognitive.dialogmanagement

import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.utils.extensions.language
import org.fuchss.deltabot.utils.extensions.translate

/**
 * The manager for all registered [Dialogs][Dialog].
 */
class DialogListener(private val configuration: BotConfiguration) : EventListener {
    private val user2instance = mutableMapOf<User, UserBotInstance>()

    /**
     * Handle [Events][GenericEvent] for registered [Dialogs][Dialog]
     * @param[event] a discord event to handle
     */
    override fun onEvent(event: GenericEvent) {
        if (event !is MessageReceivedEvent)
            return

        if (event.message.author.isBot)
            return

        var respond = false
        respond = respond || event.channelType == ChannelType.PRIVATE
        respond = respond || event.jda.selfUser in event.message.mentions.users
        respond = respond || event.message.referencedMessage?.author == event.jda.selfUser

        if (!respond) {
            return
        }

        if (configuration.disableNlu) {
            event.message.reply("NLU is disabled by the Bot Admin. I can't interpret your talk".translate(event)).queue()
            return
        }

        val instance = user2instance.getOrPut(event.message.author) { UserBotInstance(configuration) }
        instance.handle(event.message, event.language())
    }
}
