import threading
import time
import queue

class Keypad:

    def __init__(self, bus, address, key_map, poll =0.1, buffer=32):
        self.bus = bus
        self.address = address
        self.key_map = "DCBA#9630852*741"
        self.poll = poll
        self.buffer = queue.Queue(maxsize=buffer)
        self.stop = threading.Event()
        self.thread= None

    def scan_matrix(self):
        self.bus.write_byte(self.address, 0xF0)
        high = self.bus.read_byte(self.address) & 0xF0
        if high == 0xF0:
            return None
        self.bus.write_byte(self.addres, 0x0F)
        low = self.bus.read_byte(self.address) & 0x0F
        if low == 0x0F:
            return None     
        a = ((high ^ 0xF0) >> 4).bit_length()-1
        b = ((low ^ 0x0F)).bit_length()-1
        return self.key_map[a*4+b]

    def run(self):
        candidate = None
        next_time = time.monotonic()
        while not self.stop.is_set():
            candidate = self.scan_matrix()
            if candidate is not None:
                self.buffer.put_nowait(candidate)
            next_time += self.poll
            time.sleep(max(0, next_time-time.monotonic()))

    def start(self):
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def read_keys(self):
        keys = []
        while True:
            try:
                keys.append(self.buffer.get_nowait())
            except queue.Empty:
                return keys

    def end(self):
        self.stop.set()
        self.bus.close()
