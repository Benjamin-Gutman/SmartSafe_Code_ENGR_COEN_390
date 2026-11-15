import argparse

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

def str2bool(v):
    if isinstance(v, bool):
        return v
    v = v.strip().lower()
    if v in ("true", "t", "yes", "y", "1"):
        return True
    if v in ("false", "f", "no", "n", "0"):
        return False
    raise argparse.ArgumentTypeError(f"Boolean value expected, got {v!r}")
 
 # reads stuff from the Commands.java file in the android App.
def build_command_parser() -> SocketArgumentParser:
    parser = SocketArgumentParser(prog="", description="Inventory websocket commands", formatter_class=argparse.RawTextHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

#--------------------------------------------------------------------
##inventory related commands
#--------------------------------------------------------------------
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

#--------------------------------------------------------------------
#User table related commands
#--------------------------------------------------------------------
    p = sub.add_parser("createUser", help= "create user", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--name", required=True)
    p.add_argument("--email") 
    p.add_argument("--password")
    p.add_argument("--isAdmin", type=str2bool)
    p.add_argument("--hasOpenDoorPrivilege",type=str2bool)

    p = sub.add_parser("getUserByID", help= "get user using their ID", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int)

    p = sub.add_parser("getUserByUsername", help= "get user using their Username", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--username")

    p = sub.add_parser("listUsers", help= "get list of users", formatter_class=argparse.RawTextHelpFormatter)

    p = sub.add_parser("updateUserInfo", help= "update a user's parameters(name, email)", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int, required=True)
    p.add_argument("--name", required=True)
    p.add_argument("--email") 
    p.add_argument("--isAdmin", type=str2bool, required=True)
    p.add_argument("--hasOpenDoorPrivilege", type=str2bool)
    p.add_argument("--password")

    p = sub.add_parser("updatePassword", help= "update a user's password", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--UserID", type=int, required=True)
    p.add_argument("--newpassword", required = True)

    p = sub.add_parser("updateUserPrivileges", help= "update a user's priviledges(Admin rights, SafeDoor rights)", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int, required=True)
    p.add_argument("--isAdmin", type=str2bool, required=True)
    p.add_argument("--hasOpenDoorPrivilege", type=str2bool)

    p = sub.add_parser("deleteUser", help= "Delete a user using their ID", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int, required=True)

    p = sub.add_parser("verifyUser", help= "for verifying a user", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--name", required=True)
    p.add_argument("--password")

#--------------------------------------------------------------------
#User Access history part (NOT COMPLETE NOR FUNCTIONAL)
#--------------------------------------------------------------------
    p = sub.add_parser("createAccess", help= "Create an entry of Access", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int, required=True)
    p.add_argument("--changes_made")
    p.add_argument("--timestamp")

    p = sub.add_parser("getAccessHistory", help= "Retrieve a specific Access entry", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--access_id", type=int, required=True)

    p = sub.add_parser("getAccessList", help= "Retrieve Access List", formatter_class=argparse.RawTextHelpFormatter)

    p = sub.add_parser("updateAccessHistory", help= "Update a specific Access entry", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--access_id", type=int, required=True)
    p.add_argument("--user_id" )
    p.add_argument("--changes_made" )
    p.add_argument("--timestamp")

    p = sub.add_parser("deleteAccessHistory", help= "Deletes a specific Access entry", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--access_id", type=int, required=True)

    p = sub.add_parser("getAccessHistoryByUser", help= "Retrieve access history for specific user", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id", type=int, required=True)

#--------------------------------------------------------------------
#HW management part (NOT COMPLETE)
#--------------------------------------------------------------------

#HW communication
    p = sub.add_parser("giveStatus", help= "give save status", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("-isLocked", type=str2bool, required=True)
    p.add_argument("-isOpen", type=str2bool)
    p.add_argument("-alarm", type=str2bool)


#Android comunication
    p = sub.add_parser("getStatus", help= "give save status", formatter_class=argparse.RawTextHelpFormatter)

    p = sub.add_parser("lockDoor", help= "lock the safe", formatter_class=argparse.RawTextHelpFormatter)

    p = sub.add_parser("unlockDoor", help= "unlock the safe ", formatter_class=argparse.RawTextHelpFormatter)
    p.add_argument("--user_id" )


    sub.add_parser("help", help="show available commands", formatter_class=argparse.RawTextHelpFormatter)
    return parser