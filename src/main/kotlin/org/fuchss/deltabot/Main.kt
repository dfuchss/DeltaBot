package org.fuchss.deltabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.cognitive.dialog.DialogListener
import org.fuchss.deltabot.command.CommandHandler
import org.fuchss.deltabot.utils.*
import org.slf4j.spi.LocationAwareLogger

/**
 * A listener that simply logs [GenericEvent] based on the log level in [Configuration.debug].
 */
class LoggerListener(private val configuration: Configuration) : EventListener {
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


fun main() {
    val configPath = System.getenv("CONF_PATH") ?: "./config.json"
    val config = Configuration().load(configPath)

    if (config.debug) {
        logger.setLogLevel(LocationAwareLogger.DEBUG_INT)
    }

    val token = System.getenv("DISCORD_TOKEN") ?: error("DISCORD_TOKEN not set")

    logger.info("Creating Bot ..")

    val scheduler = Scheduler()
    val builder = JDABuilder.createDefault(token)
    val jda = builder.addEventListeners(scheduler, LoggerListener(config), ActivityChanger(), CommandHandler(config, scheduler), DialogListener(config)).build()
    initHiddenMessages(jda, scheduler)

    jda.awaitReady()
}

