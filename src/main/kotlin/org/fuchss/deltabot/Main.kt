package org.fuchss.deltabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogListener
import org.fuchss.deltabot.command.CommandHandler
import org.fuchss.deltabot.command.CommandRegistry
import org.fuchss.deltabot.command.react.ReactionHandler
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
    val commandRegistry = CommandRegistry(config, dbPath, scheduler, database)

    val builder = JDABuilder.createDefault(token).enableIntents(GatewayIntent.MESSAGE_CONTENT)
    val jda =
        builder.addEventListeners(
            scheduler,
            LoggerListener(config),
            ActivityChanger(),
            ReactionHandler(),
            commandRegistry,
            CommandHandler(config, database, commandRegistry),
            DialogListener(config)
        ).build()

    initHiddenMessages(jda, scheduler, database)
    jda.awaitReady()
}
