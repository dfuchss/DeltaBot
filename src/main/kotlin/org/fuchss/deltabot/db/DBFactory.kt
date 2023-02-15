package org.fuchss.deltabot.db

import jakarta.persistence.Entity
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.objectcasket.objectpacker.PackerPort
import org.fuchss.objectcasket.objectpacker.port.Configuration
import org.fuchss.objectcasket.objectpacker.port.Session
import org.fuchss.objectcasket.sqlconnector.port.DialectSqlite
import org.reflections.Reflections
import org.sqlite.JDBC
import java.io.File
import java.sql.Driver

val DRIVER: Class<out Driver?> = JDBC::class.java
const val DRIVER_NAME = "jdbc:sqlite:"

fun getDatabase(location: String): Session {
    val dbFile = File(location)
    logger.info("DB file is ${dbFile.absolutePath}")
    val config = createConfig(dbFile)
    val classesToRegister = registeredClasses()
    val manager = PackerPort.PORT.sessionManager()

    if (!dbFile.exists()) {
        logger.info("Database does not exist. Creating Domain.")
        val domain = manager.mkDomain(config)
        manager.addEntity(domain, *classesToRegister.toTypedArray())
        manager.finalizeDomain(domain)
    }
    val session = manager.session(config)
    session.declareClass(*classesToRegister.toTypedArray())

    addShutdownHook(session)
    return session
}

private fun registeredClasses(): MutableSet<Class<*>> {
    val reflections = Reflections("org.fuchss.deltabot")
    val entities = reflections.getTypesAnnotatedWith(Entity::class.java)
    logger.info("Registering ${entities.size} entities to the DB: ${entities.map { e -> e.simpleName }.sorted()}")
    return entities
}

private fun createConfig(dbFile: File): Configuration {
    val config = PackerPort.PORT.sessionManager().createConfiguration()
    config.setDriver(DRIVER, DRIVER_NAME, DialectSqlite())
    config.setUri(dbFile.toURI().path)
    config.setUser("")
    config.setPassword("")
    config.setFlag(Configuration.Flag.CREATE, Configuration.Flag.WRITE, Configuration.Flag.ALTER)
    return config
}

private fun addShutdownHook(session: Session) {
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() = PackerPort.PORT.sessionManager().terminate(session)
    })
}
