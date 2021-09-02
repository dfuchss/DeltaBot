package org.fuchss.deltabot.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.vdurmont.emoji.EmojiManager
import com.vdurmont.emoji.EmojiParser
import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min


val logger: Logger = LoggerFactory.getLogger("DeltaBot")

fun createObjectMapper(): ObjectMapper {
    @Suppress("CAST_NEVER_SUCCEEDS") // IntelliJ says cast impossible .. but that's false!
    val objectMapper = ObjectMapper().registerModule(KotlinModule() as Module)
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.setVisibility(
        objectMapper.serializationConfig.defaultVisibilityChecker //
            .withFieldVisibility(JsonAutoDetect.Visibility.ANY)//
            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)//
            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)//
            .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
    )

    return objectMapper

}

fun <L : Storable> L.load(path: String): L {
    val mapper = createObjectMapper()

    this.path = path
    val configFile = File(path)

    if (configFile.exists()) {
        try {
            val config: L = mapper.readValue(configFile, this.javaClass)
            logger.info("Loaded $config")
            config.path = path
            return config
        } catch (e: Exception) {
            logger.error(e.message)
            // Try to overwrite corrupted data :)
            this.store()
        }
    } else {
        this.store()
    }
    return this
}

fun Message.pinAndDelete() {
    try {
        pin().complete()
        val history = channel.history.retrievePast(1).complete()
        if (history.isEmpty())
            return

        val pinned = history[0] ?: return
        if (!pinned.author.isBot)
            return
        if (pinned.id == this.id)
            return
        if (pinned.messageReference?.messageId != this.id)
            return

        pinned.delete().complete()
    } catch (e: Exception) {
        logger.error(e.message)
    }
}

fun <E : Component> List<E>.toActionRows(maxInRow: Int = 5, tryModZero: Boolean = true): List<ActionRow> {
    var maxItems = max(min(maxInRow, 5), 1)
    if (tryModZero && maxItems > 3) {
        for (i in maxItems downTo 3) {
            if (this.size % i == 0) {
                maxItems = i
                break
            }
        }
    }

    val rows = mutableListOf<ActionRow>()
    var row = mutableListOf<E>()
    for (c in this) {
        if (row.size >= maxItems) {
            rows.add(ActionRow.of(row))
            row = mutableListOf()
        }
        row.add(c)
    }

    if (row.isNotEmpty())
        rows.add(ActionRow.of(row))

    return rows
}

fun String.toEmoji(): Emoji = Emoji.fromUnicode(EmojiManager.getForAlias(this).unicode)

val discordEmojiRegex = Regex("<:[A-Za-z0-9-]+:\\d+>")
 
fun findAllEmojis(emoji: String): List<String> {
    val defaultEmojis = EmojiParser.extractEmojis(emoji)
    val discordEmojis = discordEmojiRegex.findAll(emoji).map { m -> m.value }

    return defaultEmojis + discordEmojis
}

fun <K, V> Map<K, V>.reverseMap(): Map<V, List<K>> = entries.map { e -> e.value to e.key }.groupBy { e -> e.first }.map { e -> e.key to e.value.map { v -> v.second } }.toMap()
