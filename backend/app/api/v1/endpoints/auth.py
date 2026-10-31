"""Authentication endpoints."""

from datetime import timedelta
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.security import create_access_token, decode_token
from app.schemas.user import UserCreate, UserResponse
from app.services.user_service import UserService

router = APIRouter()
oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.API_V1_STR}/auth/login")


class TokenResponse(BaseModel):
    """Token response."""

    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserResponse


class AnonymousLoginRequest(BaseModel):
    """Anonymous login request."""

    device_id: str
    app_version: Optional[str] = None


class FirebaseLoginRequest(BaseModel):
    """Firebase login request."""

    firebase_token: str
    device_id: Optional[str] = None
    app_version: Optional[str] = None


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> UserResponse:
    """Get current user from token."""
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )

    payload = decode_token(token)
    if payload is None:
        raise credentials_exception

    user_id = payload.get("sub")
    if user_id is None:
        raise credentials_exception

    user_service = UserService(db)
    user = await user_service.get_by_id(user_id)
    if user is None:
        raise credentials_exception

    return UserResponse.model_validate(user)


@router.post("/anonymous", response_model=TokenResponse)
async def anonymous_login(
    request: AnonymousLoginRequest,
    db: AsyncSession = Depends(get_db),
):
    """Create anonymous user and return token."""
    user_service = UserService(db)

    # Create anonymous user
    user_data = UserCreate(
        is_anonymous=True,
        display_name="Anonymous User",
    )
    user = await user_service.create_anonymous(user_data, request.device_id)

    # Create access token
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token = create_access_token(
        data={"sub": str(user.id), "type": "anonymous"},
        expires_delta=access_token_expires,
    )

    return TokenResponse(
        access_token=access_token,
        expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
        user=UserResponse.model_validate(user),
    )


@router.post("/firebase", response_model=TokenResponse)
async def firebase_login(
    request: FirebaseLoginRequest,
    db: AsyncSession = Depends(get_db),
):
    """Login with Firebase token."""
    # TODO: Verify Firebase token and extract user info
    # For now, return error
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail="Firebase authentication not yet implemented",
    )


@router.get("/me", response_model=UserResponse)
async def get_me(
    current_user: UserResponse = Depends(get_current_user),
):
    """Get current user info."""
    return current_user
