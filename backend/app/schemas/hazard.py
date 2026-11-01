"""Hazard schemas."""

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.hazard import HazardSeverity, HazardStatus, HazardType


class Location(BaseModel):
    """Location schema."""

    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    accuracy: Optional[float] = None


class HazardBase(BaseModel):
    """Base hazard schema."""

    hazard_type: HazardType
    description: Optional[str] = None
    location: Location


class HazardCreate(HazardBase):
    """Hazard creation schema."""

    media_urls: Optional[List[str]] = None
    device_id: Optional[str] = None
    app_version: Optional[str] = None


class HazardResponse(BaseModel):
    """Hazard response schema."""

    id: UUID
    hazard_type: HazardType
    status: HazardStatus
    severity: HazardSeverity
    location: Location
    description: Optional[str]
    first_reported_at: datetime
    last_reported_at: datetime
    report_count: int
    confirmation_count: int
    expires_at: Optional[datetime]

    class Config:
        from_attributes = True


class HazardReportCreate(BaseModel):
    """Hazard report creation schema."""

    hazard_type: HazardType
    description: Optional[str] = None
    location: Location
    media_urls: Optional[List[str]] = None
    device_id: Optional[str] = None
    app_version: Optional[str] = None


class HazardReportResponse(BaseModel):
    """Hazard report response schema."""

    id: UUID
    hazard_id: Optional[UUID]
    hazard_type: HazardType
    status: HazardStatus
    location: Location
    description: Optional[str]
    media_urls: Optional[List[str]]
    created_at: datetime

    class Config:
        from_attributes = True


class NearbyHazardQuery(BaseModel):
    """Query parameters for nearby hazards."""

    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    radius: float = Field(default=500, gt=0, le=5000)  # meters
    include_unverified: bool = False
    hazard_types: Optional[List[HazardType]] = None
