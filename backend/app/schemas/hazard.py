"""Hazard schemas."""

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from geoalchemy2.shape import to_shape
from pydantic import BaseModel, Field, field_validator

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

    @field_validator("location", mode="before")
    @classmethod
    def convert_geometry_to_location(cls, value):
        """Convert a database geometry point to API latitude/longitude fields."""
        if isinstance(value, Location):
            return value
        point = to_shape(value)
        return {"latitude": point.y, "longitude": point.x}


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

    @field_validator("location", mode="before")
    @classmethod
    def convert_geometry_to_location(cls, value):
        """Convert a database geometry point to API latitude/longitude fields."""
        if isinstance(value, Location):
            return value
        point = to_shape(value)
        return {"latitude": point.y, "longitude": point.x}


class NearbyHazardQuery(BaseModel):
    """Query parameters for nearby hazards."""

    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    radius: float = Field(default=500, gt=0, le=5000)  # meters
    include_unverified: bool = False
    hazard_types: Optional[List[HazardType]] = None
