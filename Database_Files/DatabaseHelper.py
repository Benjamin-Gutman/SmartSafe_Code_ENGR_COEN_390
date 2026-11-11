
from sqlalchemy import select
from sqlalchemy.orm import Session

from Database import Base, engine
from Models.Inventory import Inventory
from Models.Inventory_History import InventoryHistory
from Models.User import User
from Schemas.Inventory import InventoryCreate, InventoryUpdate
from Schemas.Users import User, UserCreate, UserUpdate, UserResponse
from Schemas.Access_History import AccessHistory, AccessHistoryCreate, AccessHistoryUpdate, AccessHistoryDelete, AccessHistoryResponse



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

def create_user(db: Session, user: UserCreate) -> User:
    db_user = User(
        username=user.username,
        email=user.email,
        password = user.password,
        isAdmin=user.isAdmin,
        hasOpenDoorPrivilege=user.hasOpenDoorPrivilege,
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user

def get_user(db: Session, user_id: int) -> User | None:
    return db.get(User, user_id)

def get_user_by_username(db: Session, username: str) -> User | None:
    statement = select(User).where(User.username == username)
    return db.scalars(statement).first()

def get_all_users(db: Session) -> list[User]:
    statement = select(User).order_by(User.user_id)
    return list(db.scalars(statement))

def update_user(db: Session, user_id: int, user: UserUpdate) -> User | None:
    db_user = db.get(User, user_id)
    if db_user is None:
        return None

    db_user.username = user.username
    db_user.email = user.email
    db_user.isAdmin = user.isAdmin
    db_user.hasOpenDoorPrivilege = user.hasOpenDoorPrivilege

    if user.password is not None:
        db_user.password = user.password

    db.commit()
    db.refresh(db_user)
    return db_user

def update_user_password(db: Session, user_id: int, new_password: str) -> User | None:
    db_user = db.get(User, user_id)
    if db_user is None:
        return None

    db_user.password = new_password
    db.commit()
    db.refresh(db_user)
    return db_user

def update_user_privileges(db: Session, user_id: int, isAdmin: bool, hasOpenDoorPrivilege: bool) -> User | None:
    db_user = db.get(User, user_id)
    if db_user is None:
        return None

    db_user.isAdmin = isAdmin
    db_user.hasOpenDoorPrivilege = hasOpenDoorPrivilege
    db.commit()
    db.refresh(db_user)
    return db_user

def delete_user(db: Session, user_id: int) -> bool:
    db_user = db.get(User, user_id)
    if db_user is None:
        return False

    db.delete(db_user)
    db.commit()
    return True

def verify_login(db: Session, user: UserLogin) -> bool:
    return 0
    

def create_access_history(db: Session, access_history: AccessHistoryCreate) -> AccessHistory:
    db_access_history = AccessHistory(
        user_id=access_history.user_id,
        changes_made=access_history.changes_made,
        timestamp=access_history.timestamp,
    )
    db.add(db_access_history)
    db.commit()
    db.refresh(db_access_history)
    return db_access_history

def get_access_history(db: Session, access_id: int) -> AccessHistory | None:
    return db.get(AccessHistory, access_id)

def get_all_access_history(db: Session) -> list[AccessHistory]:
    statement = select(AccessHistory).order_by(AccessHistory.timestamp)
    return list(db.scalars(statement))

def update_access_history(db: Session, access_id: int, access_history: AccessHistoryUpdate) -> AccessHistory | None:
    db_access_history = db.get(AccessHistory, access_id)
    if db_access_history is None:
        return None

    db_access_history.user_id = access_history.user_id
    db_access_history.changes_made = access_history.changes_made
    db_access_history.timestamp = access_history.timestamp

    db.commit()
    db.refresh(db_access_history)
    return db_access_history

def get_access_history_by_user(db: Session, user_id: int) -> list[AccessHistory]:
    statement = select(AccessHistory).where(AccessHistory.user_id == user_id).order_by(AccessHistory.timestamp)
    return list(db.scalars(statement))



