package org.fuchss.deltabot.utils

import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.utils.extensions.logger
import java.time.Instant
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock

class Scheduler(
    private val sleepInterval: Int = 5
) : EventListener {
    private val priorityQueue = PriorityQueue<QueueElement>()
    private val lock = ReentrantLock()

    private var thread: Thread? = null

    override fun onEvent(event: GenericEvent) {
        if (event is ReadyEvent) {
            this.start()
        }

        if (event is ShutdownEvent) {
            this.stop()
        }
    }

    private fun start() {
        logger.debug("Start Scheduler")
        this.thread = Thread { loop() }
        this.thread!!.name = "BotScheduler"
        this.thread!!.isDaemon = true
        this.thread!!.start()
    }

    private fun stop() {
        logger.debug("Terminate Scheduler")
        this.thread = null
    }

    private fun loop() {
        while (this.thread != null) {
            if (priorityQueue.isNotEmpty()) {
                execute()
            }
            this.sleepSilent(sleepInterval)
        }
    }

    private fun execute() {
        val currentTime = Instant.now().epochSecond
        while (priorityQueue.isNotEmpty()) {
            lock.lock()
            val task = priorityQueue.poll()
            lock.unlock()
            if (task.timestamp > currentTime) {
                priorityQueue.add(task)
                return
            }

            try {
                task.runnable.run()
            } catch (e: Exception) {
                logger.error(e.message)
            }
        }
    }

    fun queue(
        id: String?,
        runnable: Runnable,
        timestamp: Long
    ) {
        lock.lock()
        priorityQueue.add(QueueElement(id, runnable, timestamp))
        lock.unlock()
    }

    fun reschedule(
        id: String,
        newTimestamp: Long
    ) {
        lock.lock()
        val element = priorityQueue.find { qe -> qe.id == id }
        if (element != null) {
            priorityQueue.remove(element)
            priorityQueue.add(QueueElement(id, element.runnable, newTimestamp))
        }
        lock.unlock()
    }

    private class QueueElement(
        val id: String?,
        val runnable: Runnable,
        val timestamp: Long
    ) : Comparable<QueueElement> {
        override fun compareTo(other: QueueElement): Int = timestamp.compareTo(other.timestamp)
    }

    private fun sleepSilent(sleepInterval: Int) {
        try {
            Thread.sleep(sleepInterval.toLong())
        } catch (e: InterruptedException) {
            logger.error(e.message)
        }
    }

    fun size(): Int = priorityQueue.size
}
