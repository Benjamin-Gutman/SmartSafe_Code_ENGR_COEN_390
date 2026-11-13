from pydantic import BaseModel, ConfigDict

class User(BaseModel):
    username: str
    email: str
    isAdmin: bool = False
    hasOpenDoorPrivilege: bool = False
    password: str | None = None

class UserCreate(User):
    pass

class UserUpdate(User):
    user_id: int
    password: str | None = None

class UserResponse(User):
    model_config = ConfigDict(from_attributes=True)
    user_id: int

class UserLogin(BaseModel):
    username:str
    password:str
