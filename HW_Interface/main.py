import logging
import signal
import sys

from . import watchdog_service
from . import debug_commands

from .safe_state_machine import Safe
from .loop import Loop
from .hw_drivers.gpio import Pins
from .websocket.client import Client
from .websocket.server import Server
from .hw_drivers.camera import make_camera

log = logging.getLogger("hw")



def handle_door_pin(pins, safe):
    """ Responds to changes in door state """
    for change in pins.door_events():
        if change == "opened":
            safe.door_opened()
        else:
            safe.door_closed()


def main():

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")

    loop = Loop()
    camera = make_camera(loop)
    client = Client()
  
    
    # -- Create safe state machine object and pass it the notify function -- #
    def notify(event, **details):
        
        if event == "scan":

            log.info("scan: %s", [(item.item_barcode, item.quantity) for item in details["inventory"]])
            client.send_event(event, **details)
            return

        log.info("event: %s %s", event, details)
        client.send_event(event, status=safe.status(), **details)

        if event == "locked" and camera:
            camera.scan(on_scan)


         
    def on_scan(inventory):
        """ Runs in the loop's thread when a scan finishes """
        if inventory is None:   
            log.warning("scan failed")
            return

        notify("scan", inventory=inventory)
    
    pins = Pins()
    safe = Safe(loop, pins, notify)
    server = Server(loop, safe)
    
    # -- Register the hardware in the loop watchlist and pass behavior function -- #
    loop.watch(pins.fd, lambda: handle_door_pin(pins, safe))



    if debug_commands.DEBUG == True:
        loop.watch(sys.stdin.fileno(), lambda: debug_commands.handle_terminal_command(loop, safe, camera, on_scan))

    signal.signal(signal.SIGTERM, lambda *_: loop.stop()) # Handle kill and systemctl stop / restart commands 
    signal.signal(signal.SIGINT, lambda *_: loop.stop()) # Handle Ctrl + C response

    # -- Activate watchdog service -- #
    watchdog_service.tell_systemd("READY=1")
    watchdog_service.start_watchdog(loop)

 
    log.info("running; press Ctrl+C to stop")
    try:
        loop.run()
    finally:
        # -- Puts outputs in safe state -- #
        pins.release()

        # -- Close server -- #
        server.close()

 
 
if __name__ == "__main__":
    main()
