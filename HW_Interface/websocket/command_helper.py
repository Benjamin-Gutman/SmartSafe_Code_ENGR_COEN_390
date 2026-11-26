import logging

from ..safe_state_machine import Safe

log = logging.getLogger("hw")

def build_command(event, status=None, details=None):
    """ Turns a notify() event into the command line the backend process receives. Returns None if unknown """
    
    details = details or {}

    if event == "scan":
           log.info("Websocket feature for scan not implemented yet")

    if event == "keypad":
           password = details.get("password") or ""
           log.info(f"validatePin -pin {password}")
           return f"validatePin -pin {password}"

    if event in ("door_opened", "door_closed", "locked", "unlocked", "alarm"):

            state = status["state"]

            is_open = status["door"] == "open"
            is_locked = state in ("locked", "alarm")
            is_alarm = state == "alarm"
    
            return (f"giveStatus -isLocked {str(is_locked).lower()} -isOpen {str(is_open).lower()} -alarm {str(is_alarm).lower()}")
    
    
    
    return None

def build_reply(status):
      
        state = status["state"]
      
        is_open = status["door"] == "open"
        is_locked = state in ("locked", "alarm")
        is_alarm = state == "alarm"

        return {"locked": str(is_locked).lower(), "open": str(is_open).lower(), "alarm": str(is_alarm).lower()}









    

