import argparse
import asyncio
import json
import re
import shlex
import pydantic
from contextlib import contextmanager
from pydantic import BaseModel
from argparseCommands import SocketArgumentParser, CommandError, build_command_parser
from datetime import datetime

import websockets
from websockets.sync.client import connect
from sqlalchemy.orm import sessionmaker
from sqlalchemy.orm import Session
 
import DatabaseHelper as db_helper
from Database import engine
from Schemas.Inventory import InventoryCreate, InventoryResponse, InventoryUpdate, Inventory
from Schemas.Inventory_History import InventoryHistoryResponse, InventoryHistory
from Schemas.Users import UserUpdate, UserResponse, UserCreate, UserLogin
from Schemas.Access_History import AccessHistoryCreate, AccessHistoryDelete, AccessHistoryResponse, AccessHistoryUpdate
 
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

tempAccessHistory = AccessHistoryCreate(user_id=0, changes_made=[], timestamp=datetime.now())

class Status(BaseModel):
    isLocked: bool = True
    isOpen: bool = False
    alarm: bool = False
doorStatus = Status()

def syncStatus(): #sync doorStatus with HW program
    # Establish a persistent connection to the server
    with connect("ws://localhost:8766") as websocket:
        websocket.send("status")
        response = websocket.recv()

        state = json.loads(response)

       # state = {k: v.lower() == "true" for k, v in re.findall(r"(\w+)\s*:\s*(\w+)", response)}
        doorStatus.isLocked = state["locked"]
        doorStatus.isOpen = state["open"]
        doorStatus.alarm = state["alarm"]

def sendHW(msg): #sync doorStatus with HW program
    with connect("ws://localhost:8766") as websocket:
        websocket.send(msg)
 
# Serialization helpers
def item_to_dict(item) -> dict:
    return InventoryResponse.model_validate(item).model_dump(mode="json")
 
def history_to_dict(entry) -> dict:
    return InventoryHistoryResponse.model_validate(entry).model_dump(mode="json")

def user_to_dict(user) -> dict:
    return UserResponse.model_validate(user).model_dump(mode = "json")

def access_history_to_dict(access) -> dict:
    return AccessHistoryResponse.model_validate(access).model_dump(mode = "json")

def status_to_dict(status) -> dict:
    return Status.model_validate(status).model_dump(mode = "json")
 
def ok(command: str, **payload) -> dict:
    return {"status": "ok", "command": command, **payload}
 
def error(message: str, command: str | None = None) -> dict:
    return {"status": "error", "command": command, "message": message}
 
 
@contextmanager
def session_scope():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


# def sync_inventory(db, items: list[Inventory]) -> list[InventoryHistory]:

#     history: list[InventoryHistory] = []

#     # DatabaseHelper has no lookup by barcode, so index the existing items once.
#     existing_by_barcode = {
#         item.item_barcode: item for item in db_helper.get_all_inventory(db)
#     }

#     for incoming in items:
#         existing = existing_by_barcode.get(incoming.item_barcode)

#         if existing is not None:
#             if incoming.quantity == existing.quantity:
#                 continue

#             # Remember which history rows already exist so only the new ones are returned.
#             known_ids = {
#                 h.history_id for h in db_helper.get_inventory_history(db, existing.item_id)
#             }
#             updated = db_helper.update_inventory(
#                 db,
#                 existing.item_id,
#                 InventoryUpdate(
#                     item_id=existing.item_id,
#                     item_barcode=existing.item_barcode,
#                     item_name=existing.item_name,
#                     quantity=incoming.quantity,
#                 ),
#             )
#             if updated is None:  # deleted by someone else mid-run
#                 existing_by_barcode.pop(incoming.item_barcode, None)
#                 continue

#             history.extend(
#                 h
#                 for h in db_helper.get_inventory_history(db, updated.item_id)
#                 if h.history_id not in known_ids
#             )
#         else:
#             new_item = db_helper.create_inventory(
#                 db,
#                 InventoryCreate(
#                     item_barcode=incoming.item_barcode,
#                     item_name="",  # required by the schema, left blank on purpose
#                     quantity=incoming.quantity,
#                 ),
#             )
#             # Register it so a repeated barcode later in the list updates this
#             # item instead of creating a duplicate.
#             existing_by_barcode[new_item.item_barcode] = new_item

#             # A brand new item has no history other than what create_inventory just wrote.
#             history.extend(db_helper.get_inventory_history(db, new_item.item_id))

#     return history

def sync_inventory(db, items: list[Inventory]) -> list[InventoryHistory]:

    history: list[InventoryHistory] = []

    # DatabaseHelper has no lookup by barcode, so index the existing items once.
    existing_by_barcode = {
        item.item_barcode: item for item in db_helper.get_all_inventory(db)
    }
    scanned_barcodes: set[str] = set()

    def set_quantity(existing, quantity: int) -> None:
        """Set an existing item's quantity and collect the history rows the database writes."""
        if quantity == existing.quantity:
            return

        # Remember which history rows already exist so only the new ones are returned.
        known_ids = {
            h.history_id for h in db_helper.get_inventory_history(db, existing.item_id)
        }
        updated = db_helper.update_inventory(
            db,
            existing.item_id,
            InventoryUpdate(
                item_id=existing.item_id,
                item_barcode=existing.item_barcode,
                item_name=existing.item_name,
                quantity=quantity,
            ),
        )
        if updated is None:  # deleted by someone else mid-run
            existing_by_barcode.pop(existing.item_barcode, None)
            return

        history.extend(
            h
            for h in db_helper.get_inventory_history(db, updated.item_id)
            if h.history_id not in known_ids
        )

    for incoming in items:
        scanned_barcodes.add(incoming.item_barcode)
        existing = existing_by_barcode.get(incoming.item_barcode)

        if existing is not None:
            set_quantity(existing, incoming.quantity)
        else:
            new_item = db_helper.create_inventory(
                db,
                InventoryCreate(
                    item_barcode=incoming.item_barcode,
                    item_name="",  # required by the schema, left blank on purpose
                    quantity=incoming.quantity,
                ),
            )
            # Register it so a repeated barcode later in the list updates this
            # item instead of creating a duplicate.
            existing_by_barcode[new_item.item_barcode] = new_item

            # A brand new item has no history other than what create_inventory just wrote.
            history.extend(db_helper.get_inventory_history(db, new_item.item_id))

    # Anything in the inventory that wasn't scanned is set to 0.
    # list() because set_quantity can remove entries from the dict.
    for barcode, existing in list(existing_by_barcode.items()):
        if barcode not in scanned_barcodes:
            set_quantity(existing, 0)

    return history

def createAccess(inv: InventoryResponse, userID: int):
    with session_scope() as db:
        history = db_helper.get_inventory_history(db, inv.item_id)
        histories = [InventoryHistory.model_validate(r.model_dump()) for r in history]
        slimdHistories = [histories[-1]]
        newAccess = AccessHistoryCreate
        newAccess.user_id = userID
        newAccess.changes_made = slimdHistories
        newAccess.timestamp = datetime.now()
        temp = db_helper.create_access_history(db, newAccess)
        return
 
# Command execution
def execute(args: argparse.Namespace, parser: SocketArgumentParser) -> dict:
    cmd = args.command
 
    if cmd == "help":
        return ok(cmd, message=parser.format_help())
 
    with session_scope() as db:
        # Inventory Database, one  one word cmd will default to Inventory
        if cmd == "create":
            item = db_helper.create_inventory(
                db, InventoryCreate(item_name=args.name, quantity=args.quantity, item_barcode=args.barcode)
            )
            createAccess(item, args.user_id)
            return ok(cmd, message="Item created", inventory=item_to_dict(item))
 
        if cmd in ("add", "remove"):
            func = db_helper.add_to_inventory if cmd == "add" else db_helper.remove_from_inventory
            item = func(db, args.item_id, args.quantity)
            if item is None:
                return error(f"Item {args.item_id} not found", cmd)
            createAccess(item, args.user_id)
            return ok(cmd, message=f"Quantity {cmd}ed", inventory=item_to_dict(item))
 
        if cmd == "get":
            item = db_helper.get_inventory(db, args.item_id)
            if item is None:
                return error(f"Item {args.item_id} not found", cmd)
            return ok(cmd, inventory=item_to_dict(item))
 
        if cmd == "list":
            items = db_helper.get_all_inventory(db)
            return ok(cmd, inventory=[item_to_dict(i) for i in items])
 
        if cmd == "update":
            update = InventoryUpdate(
                item_id=args.item_id, item_name=args.name, quantity=args.quantity, item_barcode=args.barcode
            )
            item = db_helper.update_inventory(db, args.item_id, update)
            if item is None:
                return error(f"Item {args.item_id} not found", cmd)
            createAccess(item, args.user_id)
            return ok(cmd, message="Item updated", inventory=item_to_dict(item))
 
        if cmd == "delete":
            deleted = db_helper.delete_inventory(db, args.item_id)
            if deleted == None:
                return error(f"Item {args.item_id} not found", cmd)
            createAccess(deleted, args.user_id)
            return ok(cmd, message=f"Item {args.item_id} deleted", item_id=args.item_id)
 
        if cmd == "history":
            entries = db_helper.get_inventory_history(db, args.item_id)
            return ok(cmd, history=[history_to_dict(e) for e in entries])

#--------------------------------------------------------------------
# User database commands (will always have keyword User)
#--------------------------------------------------------------------
        if cmd == "createUser":
            if args.name == "Keypad":
                return error(f"Username {args.name} not permitted", cmd)
            if len(db_helper.get_all_users(db)) <= 1:
                user = db_helper.create_user(
                    db, UserCreate(username=args.name, email=args.email,password=args.password, isAdmin=True, hasOpenDoorPrivilege=True)
                )
            else:
                user = db_helper.create_user(
                    db, UserCreate(username=args.name, email=args.email,password=args.password, isAdmin=args.isAdmin, hasOpenDoorPrivilege=args.hasOpenDoorPrivilege)
                )
            return ok(cmd, message="User created", user=user_to_dict(user))
    
        if cmd == "getUserByID":
            user = db_helper.get_user(db, args.user_id)
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, user=user_to_dict(user))

        if cmd == "getUserByUsername":
            user = db_helper.get_user_by_username(db, args.username)
            if user is None:
                return error(f"User {args.username} not found", cmd)
            return ok(cmd, user=user_to_dict(user))
    
        if cmd == "listUsers":
            users = db_helper.get_all_users(db)
            return ok(cmd, users=[user_to_dict(i) for i in users])
    
        if cmd == "updateUserInfo":
            update = UserUpdate(user_id=args.user_id, username=args.name, email=args.email, isAdmin=args.isAdmin, hasOpenDoorPrivilege=args.hasOpenDoorPrivilege)
            if hasattr(args, "password") and args.password is not None:
                update.password=args.password
            user = db_helper.update_user(db, args.user_id, update)
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, message="User updated", user=user_to_dict(user))

        if cmd == "updateUserPrivileges":
            user = db_helper.update_user_privileges(db, args.user_id, args.isAdmin, args.hasOpenDoorPrivilege)
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, message="User updated", user=user_to_dict(user))

        if cmd == "updatePassword" :
            update = db_helper.update_user_password(
                db, user_id=args.user_id, new_password= args.newpassword
            )
            
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, message="User Password  updated", user=user_to_dict(user))

        if cmd == "deleteUser":
            deleted = db_helper.delete_user(db, args.user_id)
            if not deleted:
                return error(f"User {args.user_id} not found", cmd)
            # Nothing to return for a deleted user, so send a confirmation.
            return ok(cmd, message=f"User {args.user_id} deleted", user_id=args.user_id)

        if cmd == "verifyUser":
            if args.username == "Keypad":
                return error(f"User {args.username} cannot be used", cmd)
            login = UserLogin(username=args.username, password=args.password)
            verification = db_helper.verify_login(db, login)
            if verification is None:
                return error(f"User {args.username} not found", cmd)
            if verification == True :
                return ok(cmd, message=f"User {args.username} is verified with this Password")
            else : 
                return ok(cmd, message=f"User {args.username} could not be verified with this Password")

#--------------------------------------------------------------------
#User Access history part
#--------------------------------------------------------------------

        if cmd == "createAccess":
            history_data = AccessHistoryCreate(
                user_id = args.user_id, changes_made = args.changes_made, timestamp = args.timestamp
            )
            entry = db_helper.create_access_history(db, history_data)
            return ok(cmd, message="Access history created", access_history=access_history_to_dict(entry))
        
    
        if cmd == "getAccessHistory":
                entry = db_helper.get_access_history(db, args.access_id)
                if entry is None:
                    return error(f"Access history {args.access_id} not found", cmd)  
                return ok(cmd, access_history=access_history_to_dict(entry))   
        
        if cmd == "getAccessList":
                entries = db_helper.get_all_access_history(db)
                return ok(cmd, access_history=[access_history_to_dict(i) for i in entries])  

        if cmd == "updateAccessHistory":
            update = AccessHistoryUpdate( user_id = args.user_id, changes_made=args.changes_made, timestamp=args.timestamp)
            entry = db_helper.update_access_history(db, args.access_id, update)
            if entry is None:
                return error(f"Entry {args.access_id} not found", cmd)
            return ok(cmd, message="Access history  updated", access_history=access_history_to_dict(entry))

        if cmd == "deleteAccessHistory":
            deleted = AccessHistoryDelete(db, args.access_id)
            if not deleted : 
                return error(f"Access History {args.access_id} not found", cmd)
            # Nothing to return for a deleted user, so send a confirmation.
            return ok(cmd, message=f"Access History {args.access_id} deleted", access_id=args.access_id)
              

        if cmd == "getAccessHistoryByUser" : 
            entries = db_helper.get_access_history_by_user(db, args.user_id)
            return ok(cmd, access_history = [access_history_to_dict(i) for i in entries])

#--------------------------------------------------------------------
#HW management part (MOSTLY COMPLETE)
#--------------------------------------------------------------------

#HW communication
        if cmd == "giveStatus": #will be changed later when scanning is implemented
            if args.isLocked == False and doorStatus.isLocked == True:
                if hasattr(args, "user_id") and args.user_id is not None:
                    tempAccessHistory.user_id = args.user_id
                else: 
                    return error("no user specified on unlock", cmd)
                
            doorStatus.isLocked = args.isLocked
            doorStatus.isOpen = args.isOpen
            doorStatus.alarm = args.alarm
            return ok(cmd, message="status updated")

        if cmd=="scanResult":
            response = json.loads(args.results)
            #items = [Inventory.model_validate(d) for d in response["inventory"]]
            items = [Inventory.model_validate({"item_name": "", **d}) for d in response["list"]]
            history = sync_inventory(db, items)
            tempAccessHistory.changes_made = history
            tempAccessHistory.timestamp = datetime.now()
            db_helper.create_access_history(db, tempAccessHistory)
            return ok(cmd, message="Inventory synced", history=[h.model_dump(mode="json") for h in history],)

        if cmd == "validatePin":
            name = db_helper.get_user(db, 1).username
            keyuser = UserLogin(username=name, password=args.pin)
            isCorrect = db_helper.verify_login(db, keyuser)
            if isCorrect is None:
                return error("cant fint keypad User", cmd)
            elif isCorrect:
                sendHW("unlock 1")
            return ok(cmd, message="status validated")

        if cmd == "setDoorPin":
            update = db_helper.update_user_password(
                db, user_id=1, new_password=args.pin
            )
            if user is None:
                return error("User keypad not found", cmd)
            return ok(cmd, message="Keypad Password updated", user=user_to_dict(user))

            
    
#Android comunication

        if cmd == "getStatus":
            return ok(cmd, doorStatus=status_to_dict(doorStatus))

        if cmd == "lockDoor":
            sendHW("lock")
            return ok(cmd, message="command sent")

        if cmd == "unlockDoor":
            user = db_helper.get_user(db, args.user_id)
            if user is None:
              return error("User not found", cmd)
            if not user.hasOpenDoorPrivilege:
              return error("You have not been granted permission to open the safe.", cmd)
             
            sendHW(f"unlock {args.user_id}")
            return ok(cmd, message="Unlock command sent")
        
 
    return error(f"Unhandled command '{cmd}'", cmd)

 
def handle_message(raw: str, parser: SocketArgumentParser) -> dict:
    try:
        tokens = shlex.split(raw)
    except ValueError as exc:
        return error(f"Could not parse command: {exc}")
    if not tokens:
        return error("Empty command. Send 'help' for usage.")
 
    try:
        SocketArgumentParser._output = []
        args = parser.parse_args(tokens)
        return execute(args, parser)
    except CommandError as exc:
        if exc.is_help:
            return ok("help", message=exc.message)
        return error(exc.message, tokens[0])
    except ValueError as exc:  # raised by DatabaseHelper validation
        return error(str(exc), tokens[0])
    except Exception as exc:  # database or unexpected errors
        return error(f"{type(exc).__name__}: {exc}", tokens[0])
 
 
# WebSocket server
async def handler(websocket) -> None:
    parser = build_command_parser()
    parser.formatter_class = argparse.RawTextHelpFormatter
    parser._get_formatter = lambda: argparse.RawTextHelpFormatter(prog=parser.prog)
    async for message in websocket:
        if isinstance(message, bytes):
            message = message.decode("utf-8", errors="replace")
        response = handle_message(message, parser)
        await websocket.send(json.dumps(response))
 
 
async def main(host: str, port: int) -> None:
    db_helper.create_tables()
    with session_scope() as db:
        has_users = len(db_helper.get_all_users(db)) > 0
        if not has_users:
            db_helper.create_user(db, UserCreate(
                    username="Keypad",
                    email="null",
                    password="1111",
                    isAdmin=False,
                    hasOpenDoorPrivilege=False,
                ),
            )
    async with websockets.serve(handler, host, port):
        print(f"Inventory websocket listening on ws://{host}:{port}")
        await asyncio.Future()  # run forever


try:
    # syncStatus() #only run when testing with HW program
    asyncio.run(main("0.0.0.0", 8765))
except KeyboardInterrupt:
    print("Server stopped")