"""Hazard service."""

from typing import List, Optional
from uuid import UUID

from geoalchemy2.functions import ST_DWithin, ST_GeogFromText
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.hazard import Hazard, HazardReport, HazardStatus
from app.schemas.hazard import HazardReportCreate, NearbyHazardQuery


class HazardService:
    """Hazard service for database operations."""

    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_nearby(self, query: NearbyHazardQuery) -> List[Hazard]:
        """Get hazards near a location."""
        # Build base query
        stmt = select(Hazard)

        # Add spatial filter
        point = f"SRID=4326;POINT({query.longitude} {query.latitude})"
        stmt = stmt.where(
            ST_DWithin(Hazard.location, ST_GeogFromText(point), query.radius)
        )

        # Filter by verification status
        if not query.include_unverified:
            stmt = stmt.where(Hazard.status == HazardStatus.VERIFIED)

        # Filter by hazard types
        if query.hazard_types:
            stmt = stmt.where(Hazard.hazard_type.in_(query.hazard_types))

        # Order by distance (approximate)
        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def create_report(
        self, report_data: HazardReportCreate, user_id: UUID
    ) -> HazardReport:
        """Create a new hazard report."""
        from geoalchemy2 import WKTElement

        report = HazardReport(
            reporter_id=user_id,
            hazard_type=report_data.hazard_type,
            description=report_data.description,
            location=WKTElement(
                f"POINT({report_data.location.longitude}"
                f" {report_data.location.latitude})",
                srid=4326,
            ),
            location_accuracy=report_data.location.accuracy,
            media_urls=(
                ",".join(report_data.media_urls) if report_data.media_urls else None
            ),
            device_id=report_data.device_id,
            app_version=report_data.app_version,
        )
        self.db.add(report)
        await self.db.commit()
        await self.db.refresh(report)

        # TODO: Trigger AI verification workflow

        return report

    async def get_report(self, report_id: UUID) -> Optional[HazardReport]:
        """Get a hazard report by ID."""
        result = await self.db.execute(
            select(HazardReport).where(HazardReport.id == report_id)
        )
        return result.scalar_one_or_none()

    async def get_user_reports(
        self, user_id: UUID, limit: int = 20, offset: int = 0
    ) -> List[HazardReport]:
        """Get reports by user."""
        stmt = (
            select(HazardReport)
            .where(HazardReport.reporter_id == user_id)
            .order_by(HazardReport.created_at.desc())
            .offset(offset)
            .limit(limit)
        )
        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def delete_report(self, report_id: UUID) -> bool:
        """Delete a hazard report."""
        result = await self.db.execute(
            select(HazardReport).where(HazardReport.id == report_id)
        )
        report = result.scalar_one_or_none()
        if report is None:
            return False

        await self.db.delete(report)
        await self.db.commit()
        return True

    async def create_or_update_hazard(self, report: HazardReport) -> Hazard:
        """Create or update aggregated hazard from report."""
        # Check for existing hazard within 20 meters
        point = (
            f"SRID=4326;POINT({report.location.longitude} {report.location.latitude})"
        )
        stmt = (
            select(Hazard)
            .where(Hazard.hazard_type == report.hazard_type)
            .where(ST_DWithin(Hazard.location, ST_GeogFromText(point), 20))
        )
        result = await self.db.execute(stmt)
        existing = result.scalar_one_or_none()

        if existing:
            # Update existing hazard
            existing.report_count += 1
            existing.last_reported_at = report.created_at
            await self.db.commit()
            await self.db.refresh(existing)
            return existing
        else:
            # Create new hazard
            hazard = Hazard(
                hazard_type=report.hazard_type,
                location=report.location,
                description=report.description,
                first_reported_at=report.created_at,
                last_reported_at=report.created_at,
                report_count=1,
            )
            self.db.add(hazard)
            await self.db.commit()
            await self.db.refresh(hazard)
            return hazard
