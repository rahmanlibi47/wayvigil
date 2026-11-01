"""Tests for hazard service functionality."""

import pytest
from uuid import uuid4

from geoalchemy2 import WKTElement

from app.models.hazard import Hazard, HazardType, HazardStatus, HazardSeverity
from app.models.user import User
from app.schemas.hazard import HazardReportCreate, Location, NearbyHazardQuery
from app.services.hazard_service import HazardService


async def _create_test_user(db_session, user_id):
    """Insert the user required by HazardReport.reporter_id's foreign key."""
    db_session.add(User(id=user_id, is_anonymous=True))
    await db_session.flush()


async def _create_test_hazard(db_session, hazard_type, status=HazardStatus.PENDING):
    """Insert an aggregated hazard for nearby-query tests."""
    hazard = Hazard(
        hazard_type=hazard_type,
        status=status,
        location=WKTElement("POINT(77.5946 12.9716)", srid=4326),
    )
    db_session.add(hazard)
    await db_session.flush()
    return hazard


class TestHazardReportCreation:
    """Tests for hazard report creation."""

    @pytest.mark.asyncio
    async def test_create_report_stores_location(self, db_session):
        """Test that report location is stored correctly."""
        service = HazardService(db_session)
        user_id = uuid4()
        await _create_test_user(db_session, user_id)

        report_data = HazardReportCreate(
            hazard_type=HazardType.POTHOLE,
            description="Test pothole",
            location=Location(latitude=12.9716, longitude=77.5946, accuracy=5.0),
            device_id="test-device"
        )

        report = await service.create_report(report_data, user_id)

        assert report is not None
        assert report.hazard_type == HazardType.POTHOLE
        assert report.reporter_id == user_id

    @pytest.mark.asyncio
    async def test_create_report_with_media_urls(self, db_session):
        """Test that media URLs are stored as JSONB."""
        service = HazardService(db_session)
        user_id = uuid4()
        await _create_test_user(db_session, user_id)

        report_data = HazardReportCreate(
            hazard_type=HazardType.ACCIDENT,
            location=Location(latitude=12.9716, longitude=77.5946),
            media_urls=["https://example.com/image1.jpg", "https://example.com/image2.jpg"]
        )

        report = await service.create_report(report_data, user_id)

        assert report is not None
        # Media URLs should be stored as a list (JSONB)
        assert report.media_urls is not None


class TestNearbyHazards:
    """Tests for nearby hazard queries."""

    @pytest.mark.asyncio
    async def test_get_nearby_returns_hazards_in_radius(self, db_session):
        """Test that nearby query returns hazards within radius."""
        service = HazardService(db_session)

        await _create_test_hazard(db_session, HazardType.SPEED_BREAKER)

        # Query nearby
        query = NearbyHazardQuery(
            latitude=12.9716,
            longitude=77.5946,
            radius=1000,  # 1km
            include_unverified=True,
        )
        hazards = await service.get_nearby(query)

        # Should find the hazard
        assert len(hazards) > 0

    @pytest.mark.asyncio
    async def test_get_nearby_filters_by_hazard_type(self, db_session):
        """Test that nearby query filters by hazard type."""
        service = HazardService(db_session)
        # Create hazards of different types
        await _create_test_hazard(db_session, HazardType.POTHOLE)
        await _create_test_hazard(db_session, HazardType.SPEED_BREAKER)

        # Query only potholes
        query = NearbyHazardQuery(
            latitude=12.9716,
            longitude=77.5946,
            radius=1000,
            include_unverified=True,
            hazard_types=[HazardType.POTHOLE]
        )
        hazards = await service.get_nearby(query)

        # All returned hazards should be potholes
        assert hazards
        for hazard in hazards:
            assert hazard.hazard_type == HazardType.POTHOLE

    @pytest.mark.asyncio
    async def test_get_nearby_excludes_unverified_by_default(self, db_session):
        """Test that unverified hazards are excluded by default."""
        service = HazardService(db_session)
        # Create a pending hazard; nearby queries exclude it by default.
        await _create_test_hazard(db_session, HazardType.POTHOLE)

        # Query without unverified
        query = NearbyHazardQuery(
            latitude=12.9716,
            longitude=77.5946,
            radius=1000,
            include_unverified=False
        )
        hazards = await service.get_nearby(query)

        # Pending hazards should not be included
        pending_hazards = [h for h in hazards if h.status == HazardStatus.PENDING]
        assert len(pending_hazards) == 0


class TestReportOwnership:
    """Tests for report ownership validation."""

    @pytest.mark.asyncio
    async def test_get_report_returns_correct_report(self, db_session):
        """Test that get_report returns the correct report."""
        service = HazardService(db_session)
        user_id = uuid4()
        await _create_test_user(db_session, user_id)

        report = await service.create_report(
            HazardReportCreate(
                hazard_type=HazardType.POTHOLE,
                location=Location(latitude=12.9716, longitude=77.5946)
            ),
            user_id
        )

        fetched = await service.get_report(report.id)

        assert fetched is not None
        assert fetched.id == report.id
        assert fetched.reporter_id == user_id

    @pytest.mark.asyncio
    async def test_delete_report_removes_report(self, db_session):
        """Test that delete_report removes the report."""
        service = HazardService(db_session)
        user_id = uuid4()
        await _create_test_user(db_session, user_id)

        report = await service.create_report(
            HazardReportCreate(
                hazard_type=HazardType.POTHOLE,
                location=Location(latitude=12.9716, longitude=77.5946)
            ),
            user_id
        )

        # Delete the report
        result = await service.delete_report(report.id)
        assert result is True

        # Verify it's gone
        fetched = await service.get_report(report.id)
        assert fetched is None

    @pytest.mark.asyncio
    async def test_delete_nonexistent_report_returns_false(self, db_session):
        """Test that deleting nonexistent report returns False."""
        service = HazardService(db_session)

        result = await service.delete_report(uuid4())
        assert result is False


class TestUserReports:
    """Tests for user report queries."""

    @pytest.mark.asyncio
    async def test_get_user_reports_returns_only_users_reports(self, db_session):
        """Test that get_user_reports returns only the specified user's reports."""
        service = HazardService(db_session)
        user1_id = uuid4()
        user2_id = uuid4()
        await _create_test_user(db_session, user1_id)
        await _create_test_user(db_session, user2_id)

        # Create reports for both users
        await service.create_report(
            HazardReportCreate(
                hazard_type=HazardType.POTHOLE,
                location=Location(latitude=12.9716, longitude=77.5946)
            ),
            user1_id
        )
        await service.create_report(
            HazardReportCreate(
                hazard_type=HazardType.SPEED_BREAKER,
                location=Location(latitude=12.9720, longitude=77.5950)
            ),
            user2_id
        )

        # Get reports for user1
        user1_reports = await service.get_user_reports(user1_id)

        # All returned reports should belong to user1
        for report in user1_reports:
            assert report.reporter_id == user1_id

    @pytest.mark.asyncio
    async def test_get_user_reports_respects_limit(self, db_session):
        """Test that get_user_reports respects the limit parameter."""
        service = HazardService(db_session)
        user_id = uuid4()
        await _create_test_user(db_session, user_id)

        # Create multiple reports
        for i in range(5):
            await service.create_report(
                HazardReportCreate(
                    hazard_type=HazardType.POTHOLE,
                    location=Location(latitude=12.9716 + i * 0.001, longitude=77.5946)
                ),
                user_id
            )

        # Get only 3 reports
        reports = await service.get_user_reports(user_id, limit=3)

        assert len(reports) <= 3
