from pydantic import BaseModel, ConfigDict

class User(BaseModel):
    username: str
    email: str
    isAdmin: bool = False
    hasOpenDoorPrivilege: bool = False

class UserCreate(User):
    password: str

class UserUpdate(User):
    user_id: int
    password: str | None = None

class UserResponse(User):
    model_config = ConfigDict(from_attributes=True)
    user_id: int

