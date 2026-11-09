from pydantic import BaseModel, ConfigDict
from datetime import datetime
from typing import List
from .Inventory_History import InventoryHistory

class AccessHistory(BaseModel):
    user_id: int
    changes_made: List[InventoryHistory]
    timestamp: datetime

class AccessHistoryCreate(AccessHistory):
    pass

class AccessHistoryUpdate(AccessHistory):
    access_id: int

class AccessHistoryDelete(AccessHistory):
    access_id: int
    

class AccessHistoryResponse(AccessHistory):
    model_config = ConfigDict(from_attributes=True)