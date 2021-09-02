package org.fuchss.deltabot.utils

import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class Scheduler {
    private val priorityQueue = PriorityQueue<QueueElement>()
    private val lock = ReentrantLock()

    private val sleepInterval: Int
    private var thread: Thread?

    constructor(sleepInterval: Int = 5) {
        this.thread = null
        this.sleepInterval = sleepInterval
    }


    fun start() {
        logger.debug("Start Scheduler")
        this.thread = Thread { -> loop() }
        this.thread!!.name = "BotScheduler"
        this.thread!!.isDaemon = true
        this.thread!!.start()
    }

    fun stop() {
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

    fun queue(runnable: Runnable, timestamp: Long) {
        lock.lock()
        priorityQueue.add(QueueElement(runnable, timestamp))
        lock.unlock()
    }

    private class QueueElement(val runnable: Runnable, val timestamp: Long) : Comparable<QueueElement> {
        override fun compareTo(other: QueueElement): Int = timestamp.compareTo(other.timestamp)
    }

    private fun sleepSilent(sleepInterval: Int) {
        try {
            Thread.sleep(sleepInterval.toLong())
        } catch (e: InterruptedException) {
            logger.error(e.message)
        }
    }
}