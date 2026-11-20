#for safe acces history
# For safe access history
from pydantic import BaseModel, ConfigDict
from datetime import datetime
from typing import Any


class AccessHistory(BaseModel):
    user_id: int
    changes_made: list[dict[str, Any]]
    timestamp: datetime = Field(default_factory=datetime.now)


class AccessHistoryCreate(AccessHistory):
    pass


class AccessHistoryUpdate(AccessHistory):
    access_id: int


class AccessHistoryDelete(BaseModel):
    access_id: int


class AccessHistoryResponse(AccessHistory):
    access_id: int
    username: str | None = None

    model_config = ConfigDict(from_attributes=True)
