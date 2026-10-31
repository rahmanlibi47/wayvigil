"""Hazard endpoints."""

from typing import List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.v1.endpoints.auth import get_current_user
from app.core.database import get_db
from app.models.hazard import HazardType
from app.schemas.hazard import (HazardReportCreate, HazardReportResponse,
                                HazardResponse, NearbyHazardQuery)
from app.schemas.user import UserResponse
from app.services.hazard_service import HazardService

router = APIRouter()


@router.get("/nearby", response_model=List[HazardResponse])
async def get_nearby_hazards(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    radius: float = Query(default=500, gt=0, le=5000),
    include_unverified: bool = Query(default=False),
    hazard_types: List[HazardType] = Query(default=None),
    db: AsyncSession = Depends(get_db),
):
    """Get hazards near a location."""
    hazard_service = HazardService(db)
    query = NearbyHazardQuery(
        latitude=latitude,
        longitude=longitude,
        radius=radius,
        include_unverified=include_unverified,
        hazard_types=hazard_types,
    )
    hazards = await hazard_service.get_nearby(query)
    return [HazardResponse.model_validate(h) for h in hazards]


@router.post(
    "/reports", response_model=HazardReportResponse, status_code=status.HTTP_201_CREATED
)
async def create_hazard_report(
    report: HazardReportCreate,
    db: AsyncSession = Depends(get_db),
    current_user: UserResponse = Depends(get_current_user),
):
    """Submit a new hazard report."""
    hazard_service = HazardService(db)
    created_report = await hazard_service.create_report(report, current_user.id)
    return HazardReportResponse.model_validate(created_report)


@router.get("/reports/me", response_model=List[HazardReportResponse])
async def get_my_reports(
    db: AsyncSession = Depends(get_db),
    current_user: UserResponse = Depends(get_current_user),
    limit: int = Query(default=20, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
):
    """Get current user's hazard reports."""
    hazard_service = HazardService(db)
    reports = await hazard_service.get_user_reports(current_user.id, limit, offset)
    return [HazardReportResponse.model_validate(r) for r in reports]


@router.get("/reports/{report_id}", response_model=HazardReportResponse)
async def get_report(
    report_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: UserResponse = Depends(get_current_user),
):
    """Get a specific hazard report."""
    hazard_service = HazardService(db)
    report = await hazard_service.get_report(report_id)
    if report is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Report not found",
        )
    return HazardReportResponse.model_validate(report)


@router.delete("/reports/{report_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_report(
    report_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user: UserResponse = Depends(get_current_user),
):
    """Delete user's own hazard report."""
    hazard_service = HazardService(db)
    report = await hazard_service.get_report(report_id)
    if report is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Report not found",
        )
    # TODO: Check if report belongs to current user
    await hazard_service.delete_report(report_id)
