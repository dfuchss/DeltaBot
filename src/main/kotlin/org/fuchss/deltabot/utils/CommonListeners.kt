package org.fuchss.deltabot.utils

import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.utils.extensions.logger

/**
 * A listener that simply logs [GenericEvent] based on the log level in [BotConfiguration.debug].
 */
class LoggerListener(private val configuration: BotConfiguration) : EventListener {
    override fun onEvent(event: GenericEvent) {
        if (configuration.debug) {
            logger.debug(event.toString())
        }

        if (event !is MessageReceivedEvent)
            return

        logger.info("${event.message}")
    }
}

/**
 * A listener that changes the presence to the default one of the bot.
 */
class ActivityChanger : EventListener {
    override fun onEvent(event: GenericEvent) {
        if (event !is ReadyEvent)
            return

        event.jda.presence.setPresence(OnlineStatus.ONLINE, Activity.watching("/help"))
    }
}