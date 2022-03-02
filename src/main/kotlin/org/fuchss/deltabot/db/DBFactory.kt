package org.fuchss.deltabot.db

import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.ObjectCasketFactory
import org.fuchss.objectcasket.port.Configuration
import org.fuchss.objectcasket.port.Session
import org.reflections.Reflections
import org.sqlite.JDBC
import java.io.File
import java.sql.Driver
import javax.persistence.Entity

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
    val reflections = Reflections("org.fuchss.deltabot")
    val entities = reflections.getTypesAnnotatedWith(Entity::class.java)
    logger.info("Registering ${entities.size} entities to the DB: ${entities.map { e -> e.simpleName }.sorted()}")
    session.declareClass(*entities.toTypedArray())
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
