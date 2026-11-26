import json
from websockets.sync.client import connect
#from ..Schemas.Inventory import Inventory, InventoryCreate

from pydantic import BaseModel, TypeAdapter

class Inventory(BaseModel):
    item_barcode: str
    item_name: str | None = None
    quantity: int

def item_to_dict(item) -> dict:
    return Inventory.model_validate(item).model_dump(mode="json")

def syncTest(): #sync doorStatus with HW program
    # Establish a persistent connection to the server
    with connect("ws://localhost:8765") as websocket:
        #test = "scanResult -results \"{\"list\": [{\"item_barcode\": \"FIT0096\", \"quantity\": 2}]}\""
        inventorys = [Inventory(item_barcode="123456789", item_name=None, quantity=2)]#, Inventory(item_barcode="987654321", item_name=None, quantity=4)]
        adapter = TypeAdapter(list[Inventory])
        jdump = adapter.dump_json(inventorys).decode("utf-8")
        jdump = jdump.replace('"', '\\"')
        print(jdump)

        msg = "scanResult -result \"{\\\"list\\\":" +  jdump + "}\""
        websocket.send(msg)
        response = websocket.recv()
        print(response)

syncTest()
