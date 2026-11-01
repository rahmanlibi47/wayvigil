"""Hazard models."""

from datetime import datetime
from enum import Enum as PyEnum
from typing import TYPE_CHECKING, List, Optional
from uuid import uuid4

from geoalchemy2 import Geometry
from sqlalchemy import (
    Column,
    DateTime,
    Enum,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, relationship

from app.core.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class HazardType(str, PyEnum):
    """Types of road hazards."""

    SPEED_BREAKER = "speed_breaker"
    POTHOLE = "pothole"
    SHARP_CURVE = "sharp_curve"
    DANGEROUS_INTERSECTION = "dangerous_intersection"
    ROAD_CONSTRUCTION = "road_construction"
    ACCIDENT = "accident"
    WATERLOGGING = "waterlogging"
    ROAD_OBSTRUCTION = "road_obstruction"
    DAMAGED_ROAD = "damaged_road"
    RAILWAY_CROSSING = "railway_crossing"
    OTHER = "other"


class HazardStatus(str, PyEnum):
    """Hazard verification status."""

    PENDING = "pending"
    UNDER_REVIEW = "under_review"
    VERIFIED = "verified"
    REJECTED = "rejected"
    EXPIRED = "expired"


class HazardSeverity(str, PyEnum):
    """Hazard severity levels."""

    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    CRITICAL = "critical"


class Hazard(Base):
    """Aggregated hazard information."""

    __tablename__ = "hazards"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    hazard_type = Column(Enum(HazardType), nullable=False)
    status = Column(Enum(HazardStatus), default=HazardStatus.PENDING)
    severity = Column(Enum(HazardSeverity), default=HazardSeverity.MODERATE)

    # Location (PostGIS Point)
    # The GiST index is declared explicitly in __table_args__ below.
    location = Column(Geometry("POINT", srid=4326, spatial_index=False), nullable=False)

    # Metadata
    description = Column(Text, nullable=True)
    first_reported_at = Column(DateTime, default=datetime.utcnow)
    last_reported_at = Column(DateTime, default=datetime.utcnow)
    report_count = Column(Integer, default=1)
    confirmation_count = Column(Integer, default=0)
    rejection_count = Column(Integer, default=0)

    # Verification
    ai_confidence = Column(Float, nullable=True)
    verified_at = Column(DateTime, nullable=True)
    expires_at = Column(DateTime, nullable=True)

    # Relationships
    reports: Mapped[List["HazardReport"]] = relationship(
        "HazardReport", back_populates="hazard"
    )

    # Table arguments with spatial index for efficient geo queries
    __table_args__ = (
        Index("idx_hazards_location", "location", postgresql_using="gist"),
        Index("idx_hazards_status", "status"),
        Index("idx_hazards_type", "hazard_type"),
        Index("idx_hazards_expires", "expires_at"),
    )

    def __repr__(self):
        return f"<Hazard(id={self.id}, type={self.hazard_type}, status={self.status})>"


class HazardReport(Base):
    """Individual hazard report from users."""

    __tablename__ = "hazard_reports"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    hazard_id = Column(UUID(as_uuid=True), ForeignKey("hazards.id"), nullable=True)
    reporter_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)

    # Report data
    hazard_type = Column(Enum(HazardType), nullable=False)
    description = Column(Text, nullable=True)

    # Location
    # The GiST index is declared explicitly in __table_args__ below.
    location = Column(Geometry("POINT", srid=4326, spatial_index=False), nullable=False)
    location_accuracy = Column(Float, nullable=True)  # in meters

    # Media - stored as JSONB for proper querying
    media_urls = Column(JSONB, nullable=True, default=list)

    # Status
    status = Column(Enum(HazardStatus), default=HazardStatus.PENDING)
    ai_confidence = Column(Float, nullable=True)
    ai_classification = Column(String(50), nullable=True)

    # Metadata
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Device info
    device_id = Column(String(128), nullable=True)
    app_version = Column(String(20), nullable=True)

    # Relationships
    hazard: Mapped[Optional["Hazard"]] = relationship(
        "Hazard", back_populates="reports"
    )
    reporter: Mapped[Optional["User"]] = relationship("User")

    # Table arguments with indexes for common queries
    __table_args__ = (
        Index("idx_hazard_reports_location", "location", postgresql_using="gist"),
        Index("idx_hazard_reports_reporter", "reporter_id"),
        Index("idx_hazard_reports_hazard", "hazard_id"),
        Index("idx_hazard_reports_created", "created_at"),
        Index("idx_hazard_reports_device", "device_id"),
    )

    def __repr__(self):
        return f"<HazardReport(id={self.id}, type={self.hazard_type})>"
