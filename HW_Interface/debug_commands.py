from .loop import Loop
from . import config
import sys

DEBUG = True;

def handle_terminal_command(loop, safe, camera, on_scan):
    """ Reads one line typed in the terminal and runs it. For debugging only """
    line = sys.stdin.readline()
    if not line:                                  # Ctrl+D closed the input
        loop.unwatch(sys.stdin.fileno())
        return

    command = line.strip().lower()
    
    if command == "unlock":

        safe.unlock(config.DEBUG_USER_ID)

    elif command == "lock":

        print("locked" if safe.lock() else "could not lock")

    elif command == "status":

        print(safe.status())

    elif command == "scan":

        if camera is None:

            print("no camera available")
        
        else:

            print("scanning..." if camera.scan(on_scan) else "a scan is already running")

    else:
        
        print("commands: lock, unlock, status, scan")