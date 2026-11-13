"""A small single-threaded event loop.
 
It sleeps until a file descriptor has data or a timer is due, then runs the matching function.
"""
import logging
import os
import queue
import selectors
import time
 
log = logging.getLogger("hw")
 
MAX_SLEEP_SECONDS = 1.0            # wake at least this often so a stop request is noticed
 
 
class Timer:
    def __init__(self, due, function):
        self.due = due
        self.function = function
        self.cancelled = False
 
 
class Loop:
    def __init__(self):
        self.selector = selectors.DefaultSelector()
        self.timers = []
        self.running = False
 
        # Worker threads use this pipe to wake the loop up (see call_from_thread).
        self._thread_calls = queue.SimpleQueue()
        self._wake_read, self._wake_write = os.pipe()
        self.watch(self._wake_read, self._run_calls_from_threads)
 
    # ---------- what the rest of the program uses ----------
 
    def watch(self, fd, callback):
        """Call callback() whenever fd has data to read."""
        self.selector.register(fd, selectors.EVENT_READ, callback)
 
    def unwatch(self, fd):
        self.selector.unregister(fd)
 
    def after(self, seconds, function):
        """Run function() once, 'seconds' from now. Returns a Timer that can be cancelled."""
        timer = Timer(time.monotonic() + seconds, function)
        self.timers.append(timer)
        return timer
 
    def cancel(self, timer):
        if timer is not None:
            timer.cancelled = True
 
    def call_from_thread(self, function):
        """Safe to call from any thread. function() runs later, in the loop's own thread."""
        self._thread_calls.put(function)
        os.write(self._wake_write, b"x")
 
    def run(self):
        self.running = True
        while self.running:
            ready = self.selector.select(self._seconds_until_next_timer())
            for key, _ in ready:
                self._run_safely(key.data)
            self._run_due_timers()
 
    def stop(self):
        self.running = False
 
    # ---------- inside the loop ----------
 
    def _seconds_until_next_timer(self):
        if not self.timers:
            return MAX_SLEEP_SECONDS
        soonest = min(timer.due for timer in self.timers)
        return max(0.0, min(soonest - time.monotonic(), MAX_SLEEP_SECONDS))
 
    def _run_due_timers(self):
        now = time.monotonic()
        due = [timer for timer in self.timers if timer.due <= now]
        self.timers = [timer for timer in self.timers if timer.due > now]
 
        for timer in due:
            if not timer.cancelled:
                self._run_safely(timer.function)
 
    def _run_calls_from_threads(self):
        os.read(self._wake_read, 4096)
        while not self._thread_calls.empty():
            self._run_safely(self._thread_calls.get())
 
    def _run_safely(self, function):
        """One failing callback must never stop the whole program."""
        try:
            function()
        except Exception:
            log.exception("error in %s", getattr(function, "__name__", function))
