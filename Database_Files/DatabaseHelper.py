
from sqlalchemy import select
from sqlalchemy.orm import Session

from Database import Base, engine
from Models.Inventory import Inventory
from Models.Inventory_History import InventoryHistory
from Schemas.Inventory import InventoryCreate, InventoryUpdate


def create_tables() -> None:
    Base.metadata.create_all(bind=engine)


def create_inventory(db: Session, item: InventoryCreate) -> Inventory:
    if item.quantity <= 0:
        raise ValueError("Initial inventory quantity must be greater than zero")

    inventory_item = Inventory(item_name=item.item_name, quantity=item.quantity)
    db.add(inventory_item)
    db.flush()
    record_history(db, inventory_item, "add")
    db.commit()
    db.refresh(inventory_item)
    return inventory_item


def add_to_inventory(db: Session, item_id: int, quantity: int) -> Inventory | None:
    if quantity < 0:
        raise ValueError("Quantity to add cannot be negative")
    return change_inventory_quantity(db, item_id, quantity)


def remove_from_inventory(db: Session, item_id: int, quantity: int) -> Inventory | None:
    if quantity < 0:
        raise ValueError("Quantity to remove cannot be negative")
    return change_inventory_quantity(db, item_id, -quantity)


def change_inventory_quantity(db: Session, item_id: int, quantity_change: int) -> Inventory | None:
    if quantity_change == 0:
        return get_inventory(db, item_id)

    inventory_item = db.get(Inventory, item_id)
    if inventory_item is None:
        return None

    new_quantity = inventory_item.quantity + quantity_change
    if new_quantity < 0:
        raise ValueError("Cannot remove more items than are in the safe")

    action = "add" if quantity_change > 0 else "remove"
    inventory_item.quantity = new_quantity
    record_history(db, inventory_item, action)
    db.commit()
    db.refresh(inventory_item)
    return inventory_item


def get_inventory(db: Session, item_id: int) -> Inventory | None:
    return db.get(Inventory, item_id)


def get_all_inventory(db: Session) -> list[Inventory]:
    statement = select(Inventory).order_by(Inventory.item_id)
    return list(db.scalars(statement))


def update_inventory(db: Session, item_id: int, item: InventoryUpdate) -> Inventory | None:
    if item.quantity < 0:
        raise ValueError("Inventory quantity cannot be negative")

    inventory_item = db.get(Inventory, item_id)
    if inventory_item is None:
        return None

    quantity_change = item.quantity - inventory_item.quantity
    if (
        quantity_change == 0
        and item.item_name == inventory_item.item_name
    ):
        return inventory_item

    inventory_item.item_name = item.item_name
    inventory_item.quantity = item.quantity
    if quantity_change != 0:
        action = "add" if quantity_change > 0 else "remove"
        record_history(db, inventory_item, action)
    db.commit()
    db.refresh(inventory_item)
    return inventory_item


def delete_inventory(db: Session, item_id: int) -> bool:
    inventory_item = db.get(Inventory, item_id)
    if inventory_item is None:
        return False

    record_history(db, inventory_item, "delete")
    db.delete(inventory_item)
    db.commit()
    return True


def get_inventory_history(db: Session, item_id: int | None = None) -> list[InventoryHistory]:
    statement = select(InventoryHistory)
    if item_id is not None:
        statement = statement.where(InventoryHistory.item_id == item_id)
    statement = statement.order_by(
        InventoryHistory.timestamp, InventoryHistory.history_id
    )
    return list(db.scalars(statement))


def record_history(db: Session, item: Inventory, action: str) -> None:
    db.add(
        InventoryHistory(
            item_id=item.item_id,
            item_name=item.item_name,
            quantity=item.quantity,
            action=action,
        )
    )
