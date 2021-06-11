import asyncio
from asyncio import iscoroutine
from threading import Thread, Lock
from time import sleep, time


class BotScheduler:
    def __init__(self, loop: asyncio.AbstractEventLoop, sleep_interval_s=5):
        self._loop = loop
        self._sleep = sleep_interval_s

        self._thread = None

        self._tasks = []
        self._lock = Lock()

    def queue(self, coroutine_or_func, at_timestamp=None):
        with self._lock:
            self._tasks.append((coroutine_or_func, at_timestamp))

    def start_scheduler(self):
        if self._thread is not None:
            return

        self._thread = Thread(target=lambda: self.__scheduler())
        self._thread.setName("BotSchedulerThread")
        self._thread.setDaemon(True)
        self._thread.start()

    def stop_scheduler(self):
        self._thread = None

    def __scheduler(self):
        while self._thread is not None:
            if len(self._tasks) != 0:
                self.__run_tasks()
            sleep(self._sleep)

    def __await(self, coroutine):
        res = asyncio.run_coroutine_threadsafe(coroutine, self._loop)
        return res.result()

    def __run_tasks(self):
        with self._lock:
            current_time = time()
            completed = []

            try:
                for (func, ts) in self._tasks:
                    if ts is not None and current_time < ts:
                        continue
                    self.__await(func) if iscoroutine(func) else func()
                    completed.append((func, ts))
            except Exception as e:
                print(e)

            for completed_task in completed:
                self._tasks.remove(completed_task)
