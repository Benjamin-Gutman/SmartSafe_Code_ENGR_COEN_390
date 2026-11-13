import logging
import signal

import sys
import watchdog_service
import debug_commands

from safe_state_machine import Safe
from loop import Loop
from hw_drivers.gpio import Pins
 
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
 
    # -- Create your hardware objects here -- #
    pins = Pins()


    # -- Create safe state machine object and pass it the notify function -- #
    def notify(event, **details):
        """ Passed to state machine to notify on event change. May also call server specific commands """     
        log.info("event: %s %s", event, details)
    
    safe = Safe(loop, pins, notify)

    # -- Register the hardware in the loop watchlist and pass behavior function -- #
    loop.watch(pins.fd, lambda: handle_door_pin(pins, safe))

    if debug_commands.DEBUG == True:
        loop.watch(sys.stdin.fileno(), lambda: debug_commands.handle_terminal_command(loop, safe))

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

 
 
if __name__ == "__main__":
    main()
