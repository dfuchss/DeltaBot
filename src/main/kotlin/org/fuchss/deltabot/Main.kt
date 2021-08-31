package org.fuchss.deltabot

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.command.CommandHandler
import org.fuchss.deltabot.utils.load
import org.fuchss.deltabot.utils.logger


class LoggerListener : EventListener {
    override fun onEvent(event: GenericEvent) {
        if (event !is MessageReceivedEvent)
            return

        logger.info("${event.message}")
    }
}

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

    val token = System.getenv("DISCORD_TOKEN") ?: error("DISCORD_TOKEN not set")

    logger.info("Creating Bot ..")
    val builder = JDABuilder.createDefault(token)
    val jda = builder.addEventListeners(LoggerListener(), ActivityChanger(), CommandHandler(config)).build()
    jda.awaitReady()
}
