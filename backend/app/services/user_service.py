"""User service."""

from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User
from app.schemas.user import UserCreate, UserUpdate


class UserService:
    """User service for database operations."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_by_id(self, user_id: str) -> Optional[User]:
        """Get user by ID."""
        try:
            uuid_obj = UUID(user_id)
        except (ValueError, AttributeError):
            # Return None for invalid UUID format instead of raising exception
            return None
        result = await self.db.execute(select(User).where(User.id == uuid_obj))
        return result.scalar_one_or_none()

    async def get_by_firebase_uid(self, firebase_uid: str) -> Optional[User]:
        """Get user by Firebase UID."""
        result = await self.db.execute(
            select(User).where(User.firebase_uid == firebase_uid)
        )
        return result.scalar_one_or_none()

    async def get_by_email(self, email: str) -> Optional[User]:
        """Get user by email."""
        result = await self.db.execute(select(User).where(User.email == email))
        return result.scalar_one_or_none()

    async def create(self, user_data: UserCreate) -> User:
        """Create a new user."""
        user = User(
            firebase_uid=user_data.firebase_uid,
            email=user_data.email,
            phone_number=user_data.phone_number,
            display_name=user_data.display_name,
            vehicle_type=user_data.vehicle_type,
            preferred_language=user_data.preferred_language,
            is_anonymous=user_data.is_anonymous,
            last_login_at=datetime.utcnow(),
        )
        self.db.add(user)
        await self.db.commit()
        await self.db.refresh(user)
        return user

    async def create_anonymous(self, user_data: UserCreate, device_id: str) -> User:
        """Return the device's anonymous account, creating it when needed."""
        result = await self.db.execute(select(User).where(User.device_id == device_id))
        user = result.scalar_one_or_none()
        if user is not None:
            user.last_login_at = datetime.utcnow()
            await self.db.commit()
            await self.db.refresh(user)
            return user

        user = User(
            device_id=device_id,
            display_name=user_data.display_name or "Anonymous User",
            vehicle_type=user_data.vehicle_type,
            preferred_language=user_data.preferred_language,
            is_anonymous=True,
            is_active=True,
            last_login_at=datetime.utcnow(),
        )
        self.db.add(user)
        await self.db.commit()
        await self.db.refresh(user)
        return user

    async def get_or_create_firebase_user(self, claims: dict) -> User:
        """Resolve a verified Firebase identity to an application user."""
        firebase_uid = claims["uid"]
        user = await self.get_by_firebase_uid(firebase_uid)
        email = claims.get("email")
        email_user = await self.get_by_email(email) if email else None
        if email_user is not None and email_user is not user:
            if (
                user is not None
                or not claims.get("email_verified")
                or email_user.firebase_uid
            ):
                raise ValueError("Email is already linked to another account")
            user = email_user

        if user is None:
            user = User(firebase_uid=firebase_uid)
            self.db.add(user)

        user.firebase_uid = firebase_uid
        user.email = email or user.email
        user.phone_number = claims.get("phone_number") or user.phone_number
        user.display_name = claims.get("name") or user.display_name
        user.photo_url = claims.get("picture") or user.photo_url
        user.is_anonymous = False
        user.last_login_at = datetime.utcnow()
        await self.db.commit()
        await self.db.refresh(user)
        return user

    async def update(self, user_id: UUID, user_update: UserUpdate) -> Optional[User]:
        """Update user."""
        result = await self.db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        if user is None:
            return None

        update_data = user_update.model_dump(exclude_unset=True)
        for field, value in update_data.items():
            setattr(user, field, value)

        user.updated_at = datetime.utcnow()
        await self.db.commit()
        await self.db.refresh(user)
        return user

    async def delete(self, user_id: UUID) -> bool:
        """Delete user."""
        result = await self.db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        if user is None:
            return False

        await self.db.delete(user)
        await self.db.commit()
        return True
