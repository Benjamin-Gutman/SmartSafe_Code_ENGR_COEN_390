from sqlalchemy import Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from Database import Base


class Inventory(Base):
    __tablename__ = "inventory"

    item_id: Mapped[int] = mapped_column(
        Integer, primary_key=True, autoincrement=True
    )
    item_name: Mapped[str] = mapped_column(String(100), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
