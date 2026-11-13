
from sqlalchemy import select
from sqlalchemy.orm import Session

from Database import Base, engine
from Models.Inventory import InventoryModel
from Models.Inventory_History import InventoryHistoryModel
from Models.User import UserModel
from Models.Access_History import AccessHistoryModel

from Schemas.Inventory import InventoryCreate, InventoryUpdate
from Schemas.Users import User, UserCreate, UserUpdate, UserResponse, UserLogin
from Schemas.Access_History import AccessHistory, AccessHistoryCreate, AccessHistoryUpdate, AccessHistoryDelete, AccessHistoryResponse



def create_tables() -> None:
    Base.metadata.create_all(bind=engine)


def create_inventory(db: Session, item: InventoryCreate) -> InventoryModel:
    if item.quantity <= 0:
        raise ValueError("Initial inventory quantity must be greater than zero")

    inventory_item = InventoryModel(item_name=item.item_name, quantity=item.quantity)
    db.add(inventory_item)
    db.flush()
    record_history(db, inventory_item, "add")
    db.commit()
    db.refresh(inventory_item)
    return inventory_item


def add_to_inventory(db: Session, item_id: int, quantity: int) -> InventoryModel | None:
    if quantity < 0:
        raise ValueError("Quantity to add cannot be negative")
    return change_inventory_quantity(db, item_id, quantity)


def remove_from_inventory(db: Session, item_id: int, quantity: int) -> InventoryModel | None:
    if quantity < 0:
        raise ValueError("Quantity to remove cannot be negative")
    return change_inventory_quantity(db, item_id, -quantity)


def change_inventory_quantity(db: Session, item_id: int, quantity_change: int) -> InventoryModel | None:
    if quantity_change == 0:
        return get_inventory(db, item_id)

    inventory_item = db.get(InventoryModel, item_id)
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


def get_inventory(db: Session, item_id: int) -> InventoryModel | None:
    return db.get(InventoryModel, item_id)


def get_all_inventory(db: Session) -> list[InventoryModel]:
    statement = select(InventoryModel).order_by(InventoryModel.item_id)
    return list(db.scalars(statement))


def update_inventory(db: Session, item_id: int, item: InventoryUpdate) -> InventoryModel | None:
    if item.quantity < 0:
        raise ValueError("Inventory quantity cannot be negative")

    inventory_item = db.get(InventoryModel, item_id)
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
    inventory_item = db.get(InventoryModel, item_id)
    if inventory_item is None:
        return False

    record_history(db, inventory_item, "delete")
    db.delete(inventory_item)
    db.commit()
    return True


def get_inventory_history(db: Session, item_id: int | None = None) -> list[InventoryHistoryModel]:
    statement = select(InventoryHistoryModel)
    if item_id is not None:
        statement = statement.where(InventoryHistoryModel.item_id == item_id)
    statement = statement.order_by(
        InventoryHistoryModel.timestamp, InventoryHistoryModel.history_id
    )
    return list(db.scalars(statement))


def record_history(db: Session, item: InventoryModel, action: str) -> None:
    db.add(
        InventoryHistoryModel (
            item_id=item.item_id,
            item_name=item.item_name,
            quantity=item.quantity,
            action=action,
        )
    )

def create_user(db: Session, user: UserCreate) -> UserModel:
    db_user = UserModel(
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

def get_user(db: Session, user_id: int) -> UserModel | None:
    return db.get(UserModel, user_id)

def get_user_by_username(db: Session, username: str) -> UserModel | None:
    statement = select(UserModel).where(UserModel.username == username)
    return db.scalars(statement).first()

def get_all_users(db: Session) -> list[UserModel]:
    statement = select(UserModel).order_by(UserModel.user_id)
    return list(db.scalars(statement))

def update_user(db: Session, user_id: int, user: UserUpdate) -> UserModel | None:
    db_user = db.get(UserModel, user_id)
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

def update_user_password(db: Session, user_id: int, new_password: str) -> UserModel | None:
    db_user = db.get(UserModel, user_id)
    if db_user is None:
        return None

    db_user.password = new_password
    db.commit()
    db.refresh(db_user)
    return db_user

def update_user_privileges(db: Session, user_id: int, isAdmin: bool, hasOpenDoorPrivilege: bool) -> UserModel | None:
    db_user = db.get(UserModel, user_id)
    if db_user is None:
        return None

    db_user.isAdmin = isAdmin
    db_user.hasOpenDoorPrivilege = hasOpenDoorPrivilege
    db.commit()
    db.refresh(db_user)
    return db_user

def delete_user(db: Session, user_id: int) -> bool:
    db_user = db.get(UserModel, user_id)
    if db_user is None:
        return False

    db.delete(db_user)
    db.commit()
    return True

def verify_login(db: Session, user: UserLogin) -> bool:
    db_user = get_user_by_username(db, user.username)
    if db_user is None:
        return None #set like this to differenciate an inexistent user from a wrong password
    return db_user.password == user.password

def create_access_history(db: Session, access_history: AccessHistoryCreate) -> AccessHistoryModel:
    
    changes = [
        change.model_dump(mode="json")
        for change in access_history.changes_made
    ]
    
    db_access_history = AccessHistoryModel(
        user_id=access_history.user_id,
        changes_made= changes,
        timestamp=access_history.timestamp,
    )
    db.add(db_access_history)
    db.commit()
    db.refresh(db_access_history)
    return db_access_history


def get_access_history(db: Session, access_id: int) -> AccessHistoryModel | None:
    return db.get(AccessHistoryModel, access_id)

def get_all_access_history(db: Session) -> list[AccessHistoryModel]:
    statement = select(AccessHistoryModel).order_by(AccessHistoryModel.timestamp)
    return list(db.scalars(statement))

def update_access_history(db: Session, access_id: int, access_history: AccessHistoryUpdate) -> AccessHistoryModel | None:

    changes = [
        change.model_dump(mode="json")
        for change in access_history.changes_made
    ]
    
    db_access_history = db.get(AccessHistoryModel, access_id)
    if db_access_history is None:
        return None

    db_access_history.user_id = access_history.user_id
    db_access_history.changes_made = changes
    db_access_history.timestamp = access_history.timestamp

    db.commit()
    db.refresh(db_access_history)
    return db_access_history

def get_access_history_by_user(db: Session, user_id: int) -> list[AccessHistoryModel]:
    statement = select(AccessHistoryModel).where(AccessHistoryModel.user_id == user_id).order_by(AccessHistoryModel.timestamp)
    return list(db.scalars(statement))
