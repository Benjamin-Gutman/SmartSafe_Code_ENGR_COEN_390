#for safe acces history
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime
from typying import any


class AccessHistory(BaseModel):
    user_id: int
    changes_made: list[dict[str, Any]]
    timestamp: datetime = FIeld (default_factory = lambda: datetime.now)

class AccessHistoryCreate(AccessHistory):
    pass

class AccessHistoryUpdate(AccessHistory):
    access_id: int

class AccessHistoryDelete(BaseModel):
    access_id: int


class AccessHistoryResponse(AccessHistory):
    acces_id : int
    username: str | None = None
    model_config = ConfigDict(from_attributes=True)
