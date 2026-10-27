from pydantic import BaseModel, ConfigDict
from datetime import datetime

class InventoryHistory(BaseModel):
    item_id: int
    item_name: str
    quantity: int
    action: str
    timestamp: datetime

class InventoryHistoryResponse(InventoryHistory):
    model_config = ConfigDict(from_attributes=True)

class InventoryHistoryCreate(InventoryHistory):
    pass

class InventoryHistoryUpdate(InventoryHistory):
    pass

