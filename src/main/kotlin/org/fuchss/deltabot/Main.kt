package org.fuchss.deltabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogListener
import org.fuchss.deltabot.command.CommandHandler
import org.fuchss.deltabot.command.CommandRegistry
import org.fuchss.deltabot.db.getDatabase
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.initHiddenMessages
import org.fuchss.deltabot.utils.logger
import org.fuchss.deltabot.utils.setLogLevel
import org.slf4j.spi.LocationAwareLogger

/**
 * A listener that simply logs [GenericEvent] based on the log level in [DeltaBotConfiguration.debug].
 */
class LoggerListener(private val configuration: DeltaBotConfiguration) : EventListener {
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
    val dbPath = System.getenv("DB_PATH") ?: "./bot.sqlite"
    val database = getDatabase(dbPath)

    val config = DeltaBotConfiguration.loadConfig(database)

    if (config.debug) {
        logger.setLogLevel(LocationAwareLogger.DEBUG_INT)
    }

    initLanguage(database)

    val token = System.getenv("DISCORD_TOKEN") ?: error("DISCORD_TOKEN not set")

    logger.info("Creating Bot ..")

    val scheduler = Scheduler()
    val commandRegistry = CommandRegistry(config, scheduler)

    val builder = JDABuilder.createDefault(token)
    val jda = builder.addEventListeners(scheduler, LoggerListener(config), ActivityChanger(), CommandHandler(config, commandRegistry), DialogListener(config, commandRegistry)).build()
    initHiddenMessages(jda, scheduler)

    jda.awaitReady()
}

