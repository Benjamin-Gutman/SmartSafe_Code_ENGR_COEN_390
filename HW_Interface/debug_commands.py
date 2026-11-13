from loop import Loop
import sys

DEBUG = True;

def handle_terminal_command(loop, safe):
    """ Reads one line typed in the terminal and runs it. For debugging only """
    line = sys.stdin.readline()
    if not line:                                  # Ctrl+D closed the input
        loop.unwatch(sys.stdin.fileno())
        return

    command = line.strip().lower()
    if command == "unlock":
        safe.unlock("debug")
    elif command == "lock":
        print("locked" if safe.lock() else "could not lock")
    elif command == "status":
        print(safe.status())
    else:
        print("commands: lock, unlock, status")