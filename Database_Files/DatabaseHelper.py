
from sqlalchemy import select
from sqlalchemy.orm import Session

from Database import Base, engine
from Models.Inventory import InventoryModel
from Models.Inventory_History import InventoryHistoryModel
from Models.User import UserModel
from Models.Access_History import AccessHistoryModel

from Schemas.Inventory import InventoryCreate, InventoryUpdate, InventoryResponse
from Schemas.Inventory_History import InventoryHistory, InventoryHistoryCreate, InventoryHistoryResponse, InventoryHistoryUpdate
from Schemas.Users import User, UserCreate, UserUpdate, UserResponse, UserLogin
from Schemas.Access_History import AccessHistory, AccessHistoryCreate, AccessHistoryUpdate, AccessHistoryDelete, AccessHistoryResponse



def create_tables() -> None:
    Base.metadata.create_all(bind=engine)


def create_inventory(db: Session, item: InventoryCreate) -> InventoryResponse:
    if item.quantity <= 0:
        raise ValueError("Initial inventory quantity must be greater than zero")

    inventory_item = InventoryModel(item_name=item.item_name, quantity=item.quantity, item_barcode = item.item_barcode)
    db.add(inventory_item)
    db.flush()
    record_history(db, inventory_item, "add")
    db.commit()
    db.refresh(inventory_item)
    return InventoryResponse.model_validate(inventory_item)


def add_to_inventory(db: Session, item_id: int, quantity: int) -> InventoryResponse | None:
    if quantity < 0:
        raise ValueError("Quantity to add cannot be negative")
    return change_inventory_quantity(db, item_id, quantity)


def remove_from_inventory(db: Session, item_id: int, quantity: int) -> InventoryResponse | None:
    if quantity < 0:
        raise ValueError("Quantity to remove cannot be negative")
    return change_inventory_quantity(db, item_id, -quantity)


def change_inventory_quantity(db: Session, item_id: int, quantity_change: int) -> InventoryResponse | None:
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
    return InventoryResponse.model_validate(inventory_item)


def get_inventory(db: Session, item_id: int) -> InventoryResponse | None:
    item = db.get(InventoryModel, item_id)
    if item is None:
        return item
    return InventoryResponse.model_validate(item)


def get_all_inventory(db: Session) -> list[InventoryResponse]:
    statement = select(InventoryModel).order_by(InventoryModel.item_id)
    return [InventoryResponse.model_validate(item) for item in db.scalars(statement)]


def update_inventory(db: Session, item_id: int, item: InventoryUpdate) -> InventoryResponse | None:
    if item.quantity < 0:
        raise ValueError("Inventory quantity cannot be negative")

    inventory_item = db.get(InventoryModel, item_id)
    if inventory_item is None:
        return None

    quantity_change = item.quantity - inventory_item.quantity
    if (
        quantity_change == 0
        and item.item_name == inventory_item.item_name
        and item.item_barcode == inventory_item.item_barcode
    ):
        return InventoryResponse.model_validate(inventory_item)

    inventory_item.item_name = item.item_name
    inventory_item.quantity = item.quantity
    inventory_item.item_barcode = item.item_barcode
    if quantity_change != 0:
        action = "add" if quantity_change > 0 else "remove"
        record_history(db, inventory_item, action)
    db.commit()
    db.refresh(inventory_item)
    return InventoryResponse.model_validate(inventory_item)


def delete_inventory(db: Session, item_id: int) -> bool:
    inventory_item = db.get(InventoryModel, item_id)
    if inventory_item is None:
        return None

    record_history(db, inventory_item, "delete")
    db.delete(inventory_item)
    db.commit()
    return inventory_item


def get_inventory_history(db: Session, item_id: int | None = None) -> list[InventoryHistoryResponse]:
    statement = select(InventoryHistoryModel)
    if item_id is not None:
        statement = statement.where(InventoryHistoryModel.item_id == item_id)
    statement = statement.order_by(
        InventoryHistoryModel.timestamp, InventoryHistoryModel.history_id
    )
    return [InventoryHistoryResponse.model_validate(item) for item in db.scalars(statement)]


def record_history(db: Session, item: InventoryModel, action: str) -> None:
    db.add(
        InventoryHistoryModel (
            item_id = item.item_id,
            item_barcode = item.item_barcode,
            item_name = item.item_name,
            quantity = item.quantity,
            action = action,
        )
    )

def create_user(db: Session, user: UserCreate) -> UserResponse:
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
    return UserResponse.model_validate(db_user)

def get_user(db: Session, user_id: int) -> UserResponse | None:
    user = db.get(UserModel, user_id)
    if user is None:
        return user
    user = UserResponse.model_validate(user)
    user.password = None
    return user

def get_user_by_username(db: Session, username: str) -> UserResponse | None:
    statement = select(UserModel).where(UserModel.username == username)
    user = db.scalars(statement).first()
    if user == None:
        return None
    user = UserResponse.model_validate(user)
    user.password = None
    return user

def get_all_users(db: Session) -> list[UserResponse]:
    statement = select(UserModel).order_by(UserModel.user_id)
    return [UserResponse.model_validate(item) for item in db.scalars(statement)]

def update_user(db: Session, user_id: int, user: UserUpdate) -> UserResponse | None:
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
    return UserResponse.model_validate(db_user)

def update_user_password(db: Session, user_id: int, new_password: str) -> UserResponse | None:
    db_user = db.get(UserModel, user_id)
    if db_user is None:
        return None

    db_user.password = new_password
    db.commit()
    db.refresh(db_user)
    return UserResponse.model_validate(db_user)

def update_user_privileges(db: Session, user_id: int, isAdmin: bool, hasOpenDoorPrivilege: bool) -> UserResponse | None:
    db_user = db.get(UserModel, user_id)
    if db_user is None:
        return None

    db_user.isAdmin = isAdmin
    db_user.hasOpenDoorPrivilege = hasOpenDoorPrivilege
    db.commit()
    db.refresh(db_user)
    return UserResponse.model_validate(db_user)

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

def create_access_history(db: Session, access_history: AccessHistoryCreate) -> AccessHistoryResponse:
    
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
    return AccessHistoryResponse.model_validate(db_access_history)


def get_access_history(db: Session, access_id: int) -> AccessHistoryResponse | None:
    item = db.get(AccessHistoryModel, access_id)
    if item is None:
        return item
    return AccessHistoryResponse.model_validate(item)

def get_all_access_history(db: Session) -> list[AccessHistoryResponse]:
    statement = select(AccessHistoryModel).order_by(AccessHistoryModel.timestamp)
    return [AccessHistoryResponse.model_validate(item) for item in list(db.scalars(statement))]

def update_access_history(db: Session, access_id: int, access_history: AccessHistoryUpdate) -> AccessHistoryResponse | None:

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
    return AccessHistoryResponse.model_validate(db_access_history)

def get_access_history_by_user(db: Session, user_id: int) -> list[AccessHistoryResponse]:
    statement = select(AccessHistoryModel).where(AccessHistoryModel.user_id == user_id).order_by(AccessHistoryModel.timestamp)
    return [AccessHistoryResponse.model_validate(item) for item in list(db.scalars(statement))]
