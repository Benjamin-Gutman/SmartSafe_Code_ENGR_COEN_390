from pydantic import BaseModel, ConfigDict

class User(BaseModel):
    username: str
    is_admin : bool
    access_history_permissions : bool
    access_safe_permissions: bool
    is_deleted : bool
    password_hashed: str





class UserCreate(User):
    pass

class StatusUpdate(User):
    is_admin : bool
    access_history_permissions : bool
    access_safe_permissions: bool
    is_deleted : bool


class UserResponse(User):
    model_config = ConfigDict(from_attributes=True)
    user_id: int