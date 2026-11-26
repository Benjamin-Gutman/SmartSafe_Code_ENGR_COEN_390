import json
from websockets.sync.client import connect

def syncStatus(): #sync doorStatus with HW program
    # Establish a persistent connection to the server
    with connect("ws://localhost:8766") as websocket:
        websocket.send(f'scanResult -results "{json.dumps({"list": items})}"')
        response = websocket.recv()
        print(response)

syncStatus()
