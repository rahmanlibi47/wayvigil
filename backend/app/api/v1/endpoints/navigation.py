"""Authenticated Places and Routes provider endpoints."""

from fastapi import APIRouter, Depends, Request

from app.api.v1.endpoints.auth import get_current_user
from app.core.rate_limit import rate_limit
from app.schemas.navigation import (
    ComputeRouteRequest,
    NavigationRoutesResponse,
    PlaceAutocompleteRequest,
    PlaceDetailsRequest,
    PlaceDetailsResponse,
    PlaceSuggestion,
)
from app.schemas.user import UserResponse
from app.services import navigation_service

router = APIRouter()


@router.post("/places/autocomplete", response_model=list[PlaceSuggestion])
@rate_limit(requests_per_minute=20)
async def autocomplete_places(
    payload: PlaceAutocompleteRequest,
    request: Request,
    _current_user: UserResponse = Depends(get_current_user),
):
    """Return a small set of Google Places predictions for an authenticated user."""
    return await navigation_service.autocomplete_places(payload)


@router.post("/places/details", response_model=PlaceDetailsResponse)
@rate_limit(requests_per_minute=30)
async def place_details(
    payload: PlaceDetailsRequest,
    request: Request,
    _current_user: UserResponse = Depends(get_current_user),
):
    """Resolve a Places prediction and keep provider credentials private."""
    return await navigation_service.get_place_details(
        payload.place_id, payload.session_token
    )


@router.post("/routes", response_model=NavigationRoutesResponse)
@rate_limit(requests_per_minute=20)
async def calculate_route(
    payload: ComputeRouteRequest,
    request: Request,
    _current_user: UserResponse = Depends(get_current_user),
):
    """Calculate a route using the server-side provider."""
    return await navigation_service.compute_route(payload)
