from websockets.sync.client import connect
import json
def run_client():
    # Establish a persistent connection to the server
    with connect("ws://localhost:8765") as websocket:
        #websocket.send("createUser --name admin --email admin@gmail.com --isAdmin true --hasOpenDoorPrivilege false --password admin123")
        #websocket.send("updateUserInfo --user_id 1 --name admin --email admin@yahoo.ca --isAdmin True --hasOpenDoorPrivilege True")
        #websocket.send("updateUserPrivileges --user_id 1 --isAdmin true --hasOpenDoorPrivilege false")
        #websocket.send("deleteUser --user_id 1")
        websocket.send("verifyUser --name go --password admin132")
        #websocket.send("listUsers")
        
        # Await the response from the server
        response = websocket.recv()
        print(response.replace('\\n', '\n'))

run_client()