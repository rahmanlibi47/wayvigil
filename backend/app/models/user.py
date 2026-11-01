"""User model."""

from datetime import datetime
from uuid import uuid4

from sqlalchemy import Boolean, Column, DateTime, String
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class User(Base):
    """User model."""

    __tablename__ = "users"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    firebase_uid = Column(String(128), unique=True, index=True, nullable=True)
    device_id = Column(String(128), unique=True, index=True, nullable=True)
    email = Column(String(255), unique=True, index=True, nullable=True)
    phone_number = Column(String(20), unique=True, index=True, nullable=True)
    display_name = Column(String(255), nullable=True)
    photo_url = Column(String(512), nullable=True)

    # Profile
    vehicle_type = Column(String(20), default="car")  # car, motorcycle
    preferred_language = Column(String(10), default="en")

    # Status
    is_active = Column(Boolean, default=True)
    is_anonymous = Column(Boolean, default=False)
    is_admin = Column(Boolean, default=False)

    # Metadata
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    last_login_at = Column(DateTime, nullable=True)

    def __repr__(self):
        return f"<User(id={self.id}, email={self.email})>"
