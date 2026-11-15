""" Receives commands from other programs on the Pi and calls the matching safe function.

It only reads. It never sends a reply.
"""
import json
import logging
import socket

from .command_helper import build_reply
import config

from websockets.frames import Frame, Opcode
from websockets.http11 import Request
from websockets.server import ServerProtocol

log = logging.getLogger("hw")

MAX_LINE_BYTES = 4096


class Server:

    def __init__(self, loop, safe):
        self.loop = loop
        self.safe = safe
        self.clients = {}                     

        # Listens on localhost only, so only programs running on this Pi can connect #
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)    # lets you restart right away
        self.listener.bind((config.SERVER_HOST, config.SERVER_PORT))
        self.listener.listen()
        self.listener.setblocking(False)

        loop.watch(self.listener.fileno(), self._accept)

# -- Public Functions -- #

    def handle_command(self, line, client):
        """ One command line has arrived. Add a case for each command you want to support """

        words = line.split()
        if not words:
            return

        name, args = words[0], words[1:]

        match name:

            case "status":
                self._reply(client, build_reply(self.safe.status()))
            case "unlock":
                self.safe.unlock("User") # Left generic for now
            case "lock":
                self.safe.lock()
            case _:
                log.warning("unknown command: %s", line)

    def close(self):
        """ Call this on shutdown. Closes every connection and stops listening """

        for client in list(self.clients):
            self._close_client(client)

        self.listener.close()

# -- Internal Functions -- #

    def _accept(self):
        """ Called by the loop when a new program connects """

        client, _ = self.listener.accept()
        client.setblocking(False)

        self.clients[client] = ServerProtocol(max_size=MAX_LINE_BYTES)   # WebSocket state for this connection
        self.loop.watch(client.fileno(), lambda: self._read(client))

    def _read(self, client):
        """ Called by the loop when a connected program has sent data """

        try:
            data = client.recv(MAX_LINE_BYTES)
        except OSError:
            data = b""

        if not data:                          # the other side disconnected
            self._close_client(client)
            return

        # Feed the bytes to the WebSocket layer: it does the handshake and splits out whole messages #
        protocol = self.clients[client]
        protocol.receive_data(data)

        for event in protocol.events_received():

            if isinstance(event, Request):        # the opening handshake
                protocol.send_response(protocol.accept(event))

            elif isinstance(event, Frame) and event.opcode == Opcode.TEXT:
                line = event.data

                try:
                    self.handle_command(line.decode(errors="replace").strip(), client)
                except Exception:
                    log.exception("error while handling command: %s", line)

            if client not in self.clients:        # a handler closed this connection
                return

        self._flush(client)


    def _reply(self, client, message):
        """ Sends one JSON line back to the program that sent the command """
 
        protocol = self.clients.get(client)
        if protocol is None:
            return

        protocol.send_text(json.dumps(message).encode())
        self._flush(client)


    def _flush(self, client):
        """ Writes out whatever the WebSocket layer has queued (handshake reply, messages, close frame) """

        protocol = self.clients.get(client)
        if protocol is None:
            return

        try:
            for chunk in protocol.data_to_send():
                if chunk:
                    client.send(chunk)
                else:                             # empty chunk = the WebSocket layer is done with this connection
                    self._close_client(client)
                    return
        except OSError:
            self._close_client(client)


    def _close_client(self, client):
        if client in self.clients:
            self.loop.unwatch(client.fileno())
            del self.clients[client]

        client.close()