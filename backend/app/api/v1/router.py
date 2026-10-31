"""API v1 router."""

from fastapi import APIRouter

from app.api.v1.endpoints import auth, hazards, users

api_router = APIRouter()

api_router.include_router(auth.router, prefix="/auth", tags=["authentication"])
api_router.include_router(users.router, prefix="/users", tags=["users"])
api_router.include_router(hazards.router, prefix="/hazards", tags=["hazards"])
