import asyncio
import traceback
from asyncio import iscoroutine
from threading import Thread, RLock
from time import sleep, time


class BotScheduler:
    """The task scheduler of the bot"""

    def __init__(self, loop: asyncio.AbstractEventLoop, sleep_interval_s=5):
        """
        Create the task scheduler of the bot.

        :param loop: the loop from the discord client for async
        :param sleep_interval_s: the sleep time for busy waiting of the scheduler in seconds
        """
        self._loop = loop
        self._sleep = sleep_interval_s

        self._thread = None

        self._tasks = []
        self._lock = RLock()

    def queue(self, coroutine_or_func, at_timestamp=None):
        """
        Queue a new task at a certain time

        :param coroutine_or_func: a coroutine or function that shall be executed (no parameters)
        :param at_timestamp: the timestamp for execution (None means now)
        """
        with self._lock:
            self._tasks.append((coroutine_or_func, at_timestamp))

    def start_scheduler(self):
        """Start the scheduler thread"""
        if self._thread is not None:
            return

        self._thread = Thread(target=lambda: self.__scheduler())
        self._thread.setName("BotSchedulerThread")
        self._thread.setDaemon(True)
        self._thread.start()

    def stop_scheduler(self):
        """Stop the scheduler thread"""
        self._thread = None

    def __scheduler(self):
        """The function invoked by the scheduler thread"""
        while self._thread is not None:
            if len(self._tasks) != 0:
                self.__run_tasks()
            sleep(self._sleep)

    def __await(self, coroutine):
        """
        This code is equivalent to 'await' but in synchronous context.

        :param coroutine: the coroutine to await
        :return: the result of the coroutine
        """
        res = asyncio.run_coroutine_threadsafe(coroutine, self._loop)
        return res.result()

    def __run_tasks(self):
        """Execute tasks that have to be executed (by timestamp)"""
        with self._lock:
            copy = list(self._tasks)

        current_time = time()
        completed = []

        for (func, ts) in copy:
            if ts is not None and current_time < ts:
                continue
            try:
                self.__await(func) if iscoroutine(func) else func()
            except Exception:
                traceback.print_exc()
                
            # If executed or failed it's completed
            completed.append((func, ts))

        with self._lock:
            for completed_task in completed:
                self._tasks.remove(completed_task)
