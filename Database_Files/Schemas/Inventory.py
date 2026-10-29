from pydantic import BaseModel, ConfigDict

class Inventory(BaseModel):
    item_name: str
    quantity: int


class InventoryCreate(Inventory):
    pass

class InventoryUpdate(Inventory):
    item_id: int

class InventoryDelete(Inventory):
    item_id: int

class InventoryResponse(Inventory):
    model_config = ConfigDict(from_attributes=True)
    item_id: int