import json
import logging
import queue
import threading

from websockets.exceptions import WebSocketException
from websockets.sync.client import connect

from .command_helper import build_command
import config

log = logging.getLogger("hw")


class Client:

    def __init__(self):
        self._queue = queue.Queue(maxsize=100)
        threading.Thread(target=self._worker, daemon=True).start()

# -- Public Functions -- #

    def send_event(self, event, status):
        """ Call this from notify(). Returns immediately """

        command = build_command(event, status)

        if command is None:
            log.warning("no command defined for event: %s", event)
            return

        try:
            self._queue.put_nowait(command)
        except queue.Full:
            log.warning("event queue full, dropped: %s", command)

# -- Internal Functions -- #

    def _worker(self):
        """ Runs in its own thread. Sends queued commands one at a time """

        while True:
            command = self._queue.get()

            try:
                reply = self._send(command)

                log.info("reply: %s", reply)
            except (OSError, ValueError, WebSocketException) as error:
                log.warning("could not send '%s': %s", command, error)

    def _send(self, command):
        """ Connects, sends one command, reads one JSON reply, and disconnects """

        url = f"ws://{config.BACKEND_HOST}:{config.BACKEND_PORT}"

        with connect(url, open_timeout=config.CLIENT_TIMEOUT_SECONDS) as ws:
            ws.send(command)
            line = ws.recv(timeout=config.CLIENT_TIMEOUT_SECONDS)

        return json.loads(line)               # raises ValueError if the reply was empty or not JSON