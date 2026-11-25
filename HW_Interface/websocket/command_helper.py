import logging
import json

from ..safe_state_machine import Safe

log = logging.getLogger("hw")

def build_command(event, status=None, details=None):
    """ Turns a notify() event into the command line the backend process receives. Returns None if unknown """
    
    details = details or {}

    if event == "scan":
       inventory = details.get("inventory") or []
       items = [{"item_name": item.item_name, "quantity": item.quantity} for item in inventory]
       return f"scanResults {json.dumps({'list': items})}"


    if event in ("door_opened", "door_closed", "locked", "unlocked", "alarm"):

            state = status["state"]

            is_open = status["door"] == "open"
            is_locked = state in ("locked", "alarm")
            is_alarm = state == "alarm"

            msg = (f"giveStatus -isLocked {str(is_locked).lower()} -isOpen {str(is_open).lower()} -alarm {str(is_alarm).lower()}")

            if event == "unlocked":
                   msg = (f"{msg} -user_id {details['source']}")
            
            return msg
    
    
    
    return None

def build_reply(status):
      
        state = status["state"]
      
        is_open = status["door"] == "open"
        is_locked = state in ("locked", "alarm")
        is_alarm = state == "alarm"

        return {"locked": str(is_locked).lower(), "open": str(is_open).lower(), "alarm": str(is_alarm).lower()}









    

