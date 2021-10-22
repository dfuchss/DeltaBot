package org.fuchss.deltabot.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.logger
import java.io.File

abstract class Storable {
    @JsonIgnore
    var path: String? = null

    fun store() {
        if (path == null) {
            error("path is not set")
        }

        val configFile = File(path as String)
        val mapper = createObjectMapper()
        try {
            configFile.parentFile.mkdirs()
            mapper.writeValue(configFile, this)
        } catch (e: Exception) {
            logger.error(e.message)
        }
    }
}