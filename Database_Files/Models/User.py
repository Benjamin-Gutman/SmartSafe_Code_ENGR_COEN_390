from sqlalchemy import Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from Database import Base


class User(Base):
    __tablename__ = "users"

    user_id: Mapped[int] = mapped_column(
        Integer, primary_key=True, autoincrement=True
    )
    username: Mapped[str] = mapped_column(String(100), nullable=False)

    # For now i have in mind : 1) admin ( for first person created in database) 2) can access access history 3) can access safe 4) deleted
    # each of them will have a column

    #might change this to a string for roles/permissions, lmk.
    is_admin: Mapped[bool] = mapped_column(Boolean, default = False, nullable=False) 

    access_history_permissions : Mapped[bool] = mapped_column(Boolean, default = False, nullable=False) 

    access_safe_permissions: Mapped[bool] = mapped_column(Boolean, default = False, nullable=False) 

    is_deleted : Mapped[bool] = mapped_column(Boolean, default = False, nullable=False) 

    #not sure if password is necessary to be stashed here but will put it for now. 
    password_hashed: mapped[str] = mapped_column(String(100), nullable=False)

    