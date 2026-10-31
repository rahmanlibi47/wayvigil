"""User schemas."""

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr


class UserBase(BaseModel):
    """Base user schema."""

    email: Optional[EmailStr] = None
    phone_number: Optional[str] = None
    display_name: Optional[str] = None
    vehicle_type: str = "car"
    preferred_language: str = "en"


class UserCreate(UserBase):
    """User creation schema."""

    firebase_uid: Optional[str] = None
    is_anonymous: bool = False


class UserUpdate(BaseModel):
    """User update schema."""

    display_name: Optional[str] = None
    vehicle_type: Optional[str] = None
    preferred_language: Optional[str] = None
    photo_url: Optional[str] = None


class UserResponse(UserBase):
    """User response schema."""

    id: UUID
    firebase_uid: Optional[str]
    photo_url: Optional[str]
    is_active: bool
    is_anonymous: bool
    created_at: datetime
    updated_at: datetime
    last_login_at: Optional[datetime]

    class Config:
        from_attributes = True
