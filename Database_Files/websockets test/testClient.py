import json
 
from websockets.exceptions import ConnectionClosed
from websockets.sync.client import connect
 
 
def run_client(url: str = "ws://100.93.188.101:8765") -> None:
    # Establish a persistent connection to the server
    with connect(url) as websocket:
        print(f"Connected to {url}. Type a command ('help' for usage, 'quit' to exit).")
        while True:
            try:
                command = input("> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
 
            if not command:
                continue
            if command.lower() in ("quit", "exit"):
                break
 
            try:
                websocket.send(command)
                # Await the response from the server
                response = websocket.recv()
            except ConnectionClosed:
                print("Connection to the server was closed.")
                break
 
            # Pretty-print JSON replies; fall back to raw text otherwise
            try:
                response = json.dumps(json.loads(response), indent=2, ensure_ascii=False)
            except (TypeError, ValueError):
                pass
            print(response.replace("\n", "\n"))
try:
    run_client()
except OSError as exc:
    print(f"Could not connect to the server: {exc}")
