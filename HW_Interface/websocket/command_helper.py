from safe_state_machine import Safe

def build_command(event, status):
    """ Turns a notify() event into the command line the backend process receives. Returns None if unknown """

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









    

