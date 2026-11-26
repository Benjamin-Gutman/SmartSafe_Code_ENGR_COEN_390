import threading
import time
import queue
import os

class Keypad:

    def __init__(self, bus, poll=0.1, buffer=8):
        self.bus = bus
        self.address = 0x20
        self.key_map = "DCBA#9630852*741"
        self.poll = poll
        self.passwords = queue.Queue(maxsize=buffer)
        self.stop = threading.Event()
        self.thread = None
        self._current = []
        self._rfd, self._wfd = os.pipe()
        os.set_blocking(self._rfd, False)
        os.set_blocking(self._wfd, False)

    def fileno(self):
        return self._rfd

    def scan_matrix(self):
        self.bus.write_byte(self.address, 0xF0)
        high = self.bus.read_byte(self.address) & 0xF0
        if high == 0xF0:
            return None
        self.bus.write_byte(self.address, 0x0F)
        low = self.bus.read_byte(self.address) & 0x0F
        if low == 0x0F:
            return None
        a = ((high ^ 0xF0) >> 4).bit_length() - 1
        b = (low ^ 0x0F).bit_length() - 1
        return self.key_map[a*4+b]

    def _handle_key(self, key):
        if key == '#':
            password = "".join(self._current)
            self._current.clear()
            try:
                self.passwords.put_nowait(password)
                os.write(self._wfd, b"\x01")    
            except (queue.Full, BlockingIOError):
                pass
        elif key == '*':
            self._current.clear()             
        else:
            self._current.append(key)

    def run(self):
        last = None
        next_time = time.monotonic()
        while not self.stop.is_set():
            key = self.scan_matrix()
            if key is not None and key != last:
                self._handle_key(key)
            last = key
            next_time += self.poll
            time.sleep(max(0, next_time - time.monotonic()))

    def start(self):
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def read_password(self):
        try:
            return self.passwords.get_nowait()
        except queue.Empty:
            return None
        finally:
            if self.passwords.empty():
                try:
                    while os.read(self._rfd, 1024):
                        pass
                except BlockingIOError:
                    pass

    def end(self):
        self.stop.set()
        if self.thread:
            self.thread.join()
        self.bus.close()
        os.close(self._rfd)
        os.close(self._wfd)