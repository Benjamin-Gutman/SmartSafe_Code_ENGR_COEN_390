import argparse
import asyncio
import json
import shlex
import pydantic
from contextlib import contextmanager
 
import websockets
from sqlalchemy.orm import sessionmaker
 
import DatabaseHelper as db_helper
from Database import engine
from Schemas.Inventory import InventoryCreate, InventoryResponse, InventoryUpdate
from Schemas.Inventory_History import InventoryHistoryResponse

from Models.User import User
from Schemas.Users import UserUpdate, UserResponse, UserCreate

from Schemas.Access_History import AccessHistoryCreate, AccessHistoryDelete, AccessHistoryResponse, AccessHistoryUpdate
 
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)

class CommandError(Exception):
   #Raised for bad commands or when argparse wants to exit.
 
    def __init__(self, message: str, is_help: bool = False):
        super().__init__(message)
        self.message = message
        self.is_help = is_help
 
 
class SocketArgumentParser(argparse.ArgumentParser):
    _output: list[str] = []
 
    def _print_message(self, message, file=None):
        # Capture help text instead of printing it to the console.
        if message:
            SocketArgumentParser._output.append(message)
 
    def error(self, message):
        raise CommandError(message)
 
    def exit(self, status=0, message=None):
        text = "".join(SocketArgumentParser._output)
        SocketArgumentParser._output = []
        if message:
            text += message
        raise CommandError(text.strip(), is_help=(status == 0))
 
 
def build_command_parser() -> SocketArgumentParser:
    parser = SocketArgumentParser(prog="", description="Inventory websocket commands", formatter_class=argparse.RawTextHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
 
    p = sub.add_parser("create", help="create a new inventory item", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--name", required=True, help="item name")
    p.add_argument("--quantity", type=int, required=True, help="initial quantity (> 0)")
 
    p = sub.add_parser("add", help="add quantity to an item", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("item_id", type=int)
    p.add_argument("quantity", type=int)
 
    p = sub.add_parser("remove", help="remove quantity from an item", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("item_id", type=int)
    p.add_argument("quantity", type=int)
 
    p = sub.add_parser("get", help="get one item", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("item_id", type=int)
 
    sub.add_parser("list", help="list all items", formatter_class=argparse.RawTextHelpFormatter)
 
    p = sub.add_parser("update", help="set an item's name and quantity", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("item_id", type=int)
    p.add_argument("--name", required=True)
    p.add_argument("--quantity", type=int, required=True)
 
    p = sub.add_parser("delete", help="delete an item", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("item_id", type=int)
 
    p = sub.add_parser("history", help="show inventory history", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--item-id", type=int, default=None, help="filter by item id")
 
    sub.add_parser("help", help="show available commands", formatter_class=argparse.RawTextHelpFormatter)
    return parser
 
 
# Serialization helpers
def item_to_dict(item) -> dict:
    return InventoryResponse.model_validate(item).model_dump(mode="json")
 
 
def history_to_dict(entry) -> dict:
    return InventoryHistoryResponse.model_validate(entry).model_dump(mode="json")

def user_to_dict(user) -> dict:
    return UserResponse.model_validate(user).model_dump(mode = "json")


def access_history_to_dict(access) -> dict:
    return AccessHistoryResponse.model_validate(access).model_dump(mode = "json")
 
 
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
 
 
# Command execution
def execute(args: argparse.Namespace, parser: SocketArgumentParser) -> dict:
    cmd = args.command
 
    if cmd == "help":
        return ok(cmd, message=parser.format_help())
 
    with session_scope() as db:
        # Inventory Database, one  one word cmd will default to Inventory
        if cmd == "create":
            item = db_helper.create_inventory(
                db, InventoryCreate(item_name=args.name, quantity=args.quantity)
            )
            return ok(cmd, message="Item created", inventory=item_to_dict(item))
 
        if cmd in ("add", "remove"):
            func = db_helper.add_to_inventory if cmd == "add" else db_helper.remove_from_inventory
            item = func(db, args.item_id, args.quantity)
            if item is None:
                return error(f"Item {args.item_id} not found", cmd)
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
                item_id=args.item_id, item_name=args.name, quantity=args.quantity
            )
            item = db_helper.update_inventory(db, args.item_id, update)
            if item is None:
                return error(f"Item {args.item_id} not found", cmd)
            return ok(cmd, message="Item updated", inventory=item_to_dict(item))
 
        if cmd == "delete":
            deleted = db_helper.delete_inventory(db, args.item_id)
            if not deleted:
                return error(f"Item {args.item_id} not found", cmd)
            # Nothing to return for a deleted item, so send a confirmation.
            return ok(cmd, message=f"Item {args.item_id} deleted", item_id=args.item_id)
 
        if cmd == "history":
            entries = db_helper.get_inventory_history(db, args.item_id)
            return ok(cmd, history=[history_to_dict(e) for e in entries])

        # User database commands (will always have keyword User)
        if cmd == "createUser":
            #will automatically set user as admin if first one.
            if len(db_helper.get_all_users) == 0: 
                user = db_helper.create_user(
                    db, UserCreate(username=args.username, email=args.email,isAdmin= True,hasOpenDoorPrivilege= True)
                )
            else:
                user = db_helper.create_user(
                    db, UserCreate(username=args.username, email=args.email,isAdmin = False ,hasOpenDoorPrivilege= False)
                )
            return ok(cmd, message="User created", user=user_to_dict(user))
        
    
        if cmd == "getUser":
            user = db_helper.get_user(db, args.user_id)
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, user=user_to_dict(user))
    
        if cmd == "listUser":
            users = db_helper.get_all_users(db)
            return ok(cmd, users=[users_to_dict(i) for i in users])
    
        if cmd == "updateUser":
            update = UserUpdate(
                username=args.username, email=args.email,  isAdmin=args.isAdmin, hasOpenDoorPrivilege=args.hasOpenDoorPrivilege,password=args.password)
            user = db_helper.update_user(db, args.user_id, update)
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, message="User updated", user=user_to_dict(user))


        if cmd == "updateUserPrivileges" :
            update = db_helper.update_user_privileges(
                            user_id=args.user_id, isAdmin = args.idAdmin, hasOpenDoorPrivilege=args.hasOpenDoorPrilige
                        )
   
            if user is None:
                return error(f"Item {args.user_id} not found", cmd)
            return ok(cmd, message="User Permission updated", user=user_to_dict(user))
            
        if cmd == "updateUserPassword" :
            update = db_helper.update_user_password(
                user_id=args.user_id, new_password= args.new_password
            )
            
            if user is None:
                return error(f"User {args.user_id} not found", cmd)
            return ok(cmd, message="User Password  updated", user=user_to_dict(user))



        if cmd == "deleteUser":
            deleted = db_helper.delete_inventoryuser(db, args.user_id)
            if not deleted:
                return error(f"User {args.user_id} not found", cmd)
            # Nothing to return for a deleted user, so send a confirmation.
            return ok(cmd, message=f"User {args.user_id} deleted", user_id=args.user_id)

        # Access history 

        if cmd == "createAccessHistory":
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
        
        if cmd == "listAccessHistory":
                entries = db_helper.get_all_access_history(db)
                return ok(cmd, access_history=[access_history_to_dict(i) for i in entries])  

        if cmd == "updateAccessHistory":
            update = AccessHistoryUpdate( user_id = args.user_id, changes_made=args.changes_made, timestamp=args.timestamp)
            entry = db_helper.update_access_history(db, args.access_id, update)
            if entry is None:
                return error(f"Entry {args.entry_id} not found", cmd)
            return ok(cmd, message="Access history  updated", access_history=access_history_to_dict(entry))

        if cmd == "deleteAccessHistory":
            deleted = AccessHistoryDelete(db, args.access_id)
            if not deleted : 
                return error(f"Access History {args.access_id} not found", cmd)
            # Nothing to return for a deleted user, so send a confirmation.
            return ok(cmd, message=f"Access History {args.access_id} deleted", access_id=args.access_id)
              

        if cmd == "getAllAccessHistoryForUser" : 
            entries = db_helper.get_access_history_by_user(db, args.user_id)
            return ok(cmd, access_history = [access_history_to_dict(i) for i in entries])

 
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
    async with websockets.serve(handler, host, port):
        print(f"Inventory websocket listening on ws://{host}:{port}")
        await asyncio.Future()  # run forever

try:
    asyncio.run(main("0.0.0.0", 8765))
except KeyboardInterrupt:
    print("Server stopped")
