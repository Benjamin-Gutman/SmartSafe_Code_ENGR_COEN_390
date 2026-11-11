"""Tells systemd the program is alive. If it stops reporting, systemd restarts it."""
import os
import socket
 
 
def tell_systemd(message):
    path = os.environ.get("NOTIFY_SOCKET")
    if not path:
        return                                     # not running under systemd
    if path.startswith("@"):
        path = "\0" + path[1:]
    with socket.socket(socket.AF_UNIX, socket.SOCK_DGRAM) as sock:
        sock.connect(path)
        sock.sendall(message.encode())
 
 
def start_watchdog(loop, every_seconds=5):
    def ping():
        tell_systemd("WATCHDOG=1")
        loop.after(every_seconds, ping)
    ping()
