import asyncio
import json
from pydantic import BaseModel, ConfigDict
from datetime import datetime
from websockets.asyncio.server import serve
from Schemas.Inventory import  InventoryResponse
from Schemas.Access_History import InventoryHistoryResponse

async def echo_handler(websocket):
    # Iterate over incoming messages from the client
    async for message in websocket:
        commands = message.split(",")
        match commands[0]:
            case "list":
                #list
                match commands[1]:
                    case "all":
                        #return list of both tables in JASON
                    case "inventor":
                        match commands[2]:
                            case "all":
                                #return list of all inventory objects
                            case _:
                                id = commands[2]
                    case "inventory history":
                        match commands[2]:
                            case "all":
                                #return list of all inventoryHidtory objects
                            case _:
                                id = commands[2]             
                                      
            #case "modify":
                #modify
            case _:
                retMsg = "error"
        # Send the message back to the client
        await websocket.send(retMsg)

async def main():
    # Start the server on localhost port 8765
    server = await serve(echo_handler, "localhost", 8765)
    print("WebSocket server running on ws://localhost:8765")
    await server.serve_forever()

def getAllInventory():
    #get list of inventry
    return invList

def getAllInventoryHistory(int id):
    if id == -1:
        #get list of all inventory history's
        return invHisList
    else:
        #get list of all inv his with id
        return invHisList 

#sample test data
allInventory = [
    InventoryResponse(item_id=1, item_name="book", quantity=25),
    InventoryResponse(item_id=2, item_name="dildo", quantity=3),
    ]

allInventoryHistory = [
    InventoryHistoryResponse(item_id=1, item_name="book", quantity=25, action="add", timestamp=datetime.now),
    InventoryHistoryResponse(item_id=2, item_name="dildo", quantity=3, action="add", timestamp=datetime.now),
]

asyncio.run(main())