package org.fuchss.deltabot.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


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
            mapper.writeValue(configFile, this)
        }
    } else {
        mapper.writeValue(configFile, this)
    }
    return this
}