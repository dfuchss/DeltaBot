package org.fuchss.deltabot

import net.dv8tion.jda.api.JDABuilder
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogListener
import org.fuchss.deltabot.command.CommandHandler
import org.fuchss.deltabot.command.CommandRegistry
import org.fuchss.deltabot.command.react.ReactionHandler
import org.fuchss.deltabot.command.user.polls.PollAdmin
import org.fuchss.deltabot.db.getDatabase
import org.fuchss.deltabot.utils.ActivityChanger
import org.fuchss.deltabot.utils.LoggerListener
import org.fuchss.deltabot.utils.Scheduler
import org.fuchss.deltabot.utils.extensions.initHiddenMessages
import org.fuchss.deltabot.utils.extensions.initLanguage
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.setLogLevel
import org.slf4j.spi.LocationAwareLogger


fun main() {
    val dbPath = System.getenv("DB_PATH") ?: "./bot.sqlite"
    val database = getDatabase(dbPath)

    val config = BotConfiguration.loadConfig(database)

    if (config.debug) {
        logger.setLogLevel(LocationAwareLogger.DEBUG_INT)
    }

    initLanguage(database)

    val token = System.getenv("DISCORD_TOKEN") ?: error("DISCORD_TOKEN not set")

    logger.info("Creating Bot ..")

    val scheduler = Scheduler()
    val pollAdmin = PollAdmin()
    val commandRegistry = CommandRegistry(config, scheduler, database, pollAdmin)

    val builder = JDABuilder.createDefault(token)
    val jda =
        builder.addEventListeners(scheduler, LoggerListener(config), ActivityChanger(), ReactionHandler(), pollAdmin, CommandHandler(config, commandRegistry), DialogListener(config, commandRegistry))
            .build()
    initHiddenMessages(jda, scheduler, database)

    jda.awaitReady()
}

