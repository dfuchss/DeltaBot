package org.fuchss.deltabot.db

import org.fuchss.deltabot.BotConfiguration
import org.fuchss.deltabot.command.user.Reminder
import org.fuchss.deltabot.db.dto.GuildDTO
import org.fuchss.deltabot.db.dto.UserDTO
import org.fuchss.deltabot.db.settings.LanguageDTO
import org.fuchss.deltabot.db.settings.LanguageSettings
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.ObjectCasketFactory
import org.fuchss.objectcasket.port.Configuration
import org.fuchss.objectcasket.port.Session
import org.sqlite.JDBC
import java.io.File
import java.sql.Driver


val DRIVER: Class<out Driver?> = JDBC::class.java
const val DRIVER_NAME = "jdbc:sqlite:"

private val ocPort = ObjectCasketFactory.FACTORY.ObjectCasketPort()


fun getDatabase(location: String): Session {
    val dbFile = File(location)
    logger.info("DB file is ${dbFile.absolutePath}")
    val config = createConfig(dbFile)
    val session = ocPort.sessionManager().session(config)
    registerClasses(session)
    session.open()
    addShutdownHook(session)
    return session
}

fun <T> Session.load(type: Class<T>, initializer: (T, Session) -> Unit, defaultValue: () -> T): T {
    val elements = this.getAllObjects(type)
    val element =
        if (elements.isNotEmpty())
            elements.first()
        else {
            val newElement = defaultValue()
            this.persist(newElement)
            newElement
        }

    initializer(element, this)
    return element
}

private fun registerClasses(session: Session) {
    session.declareClass(
        UserDTO::class.java,
        GuildDTO::class.java,

        BotConfiguration::class.java,

        LanguageSettings::class.java,
        LanguageDTO::class.java,

        Reminder.ReminderData::class.java
    )
}

private fun createConfig(dbFile: File): Configuration {
    val config = ocPort.configurationBuilder().createConfiguration()
    config.setDriver(DRIVER, DRIVER_NAME)
    config.setUri(dbFile.toURI().path)
    config.setUser("")
    config.setPasswd("")
    config.setFlag(Configuration.Flag.CREATE, Configuration.Flag.MODIFY)
    return config
}

private fun addShutdownHook(session: Session) {
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() = ocPort.sessionManager().terminate(session)
    })
}
