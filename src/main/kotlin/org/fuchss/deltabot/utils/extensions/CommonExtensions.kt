package org.fuchss.deltabot.utils.extensions

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.vdurmont.emoji.EmojiManager
import com.vdurmont.emoji.EmojiParser
import net.dv8tion.jda.api.entities.Emoji
import org.fuchss.deltabot.utils.Storable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Field


/**
 * The one and only logger instance of the bot.
 */
val logger: Logger = LoggerFactory.getLogger("DeltaBot")

/**
 * Set the log level of a logger.
 * @param[level] the new log level
 */
fun Logger.setLogLevel(level: Int) {
    try {
        val f: Field = this.javaClass.getDeclaredField("currentLogLevel")
        f.isAccessible = true
        f.set(this, level)
    } catch (e: Exception) {
        println("Error while setting log level: ${e.message}")
    }
}

/**
 * Create a new [ObjectMapper].
 */
fun createObjectMapper(): ObjectMapper {
    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
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

/**
 * Read a value using the [ObjectMapper].
 * @param[data] the data
 * @param[instance] a instance of the result type to get the class
 * @return the deserialized instance
 */
fun <T : Any> ObjectMapper.readKtValue(data: String, instance: T): T = readValue(data, instance.javaClass)

/**
 * Read a value using the [ObjectMapper].
 * @param[data] the data
 * @param[instance] a instance of the result type to get the class
 * @return the deserialized instance
 */
fun <T : Any> ObjectMapper.readKtValue(data: ByteArray, instance: T): T = readValue(data, instance.javaClass)

/**
 * Load a [Storable] from a certain [path].
 */
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

/**
 * Convert a string emoji to an [Emoji].
 */
fun String.toEmoji(): Emoji = Emoji.fromUnicode(EmojiManager.getForAlias(this).unicode)

/**
 * The regex for discord emojis.
 */
val discordEmojiRegex = Regex("<:[A-Za-z0-9-]+:\\d+>")

/**
 * Find all Emojis in a Discord Message.
 * @param[text] the input text
 */
fun findAllEmojis(text: String): List<String> {
    val defaultEmojis = EmojiParser.extractEmojis(text)
    val discordEmojis = discordEmojiRegex.findAll(text).map { m -> m.value }

    return defaultEmojis + discordEmojis
}

/**
 * Create a revered map.
 */
fun <K, V> Map<K, V>.reverseMap(): Map<V, List<K>> = entries.map { e -> e.value to e.key }.groupBy { e -> e.first }.map { e -> e.key to e.value.map { v -> v.second } }.toMap()

/**
 * Create a copy of a list with an additional element at the first index.
 * @param[e] the new element
 * @return the new list
 */
fun <E> List<E>.withFirst(e: E): List<E> {
    val newList = mutableListOf(e)
    newList.addAll(this)
    return newList
}

/**
 * Create a copy of a list without a certain element.
 * @param[e] the new element
 * @return the new list
 */
fun <E> List<E>.without(e: E): List<E> {
    val newList = this.toMutableList()
    newList.remove(e)
    return newList
}