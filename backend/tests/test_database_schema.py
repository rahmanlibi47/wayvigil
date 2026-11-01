"""Database schema acceptance tests."""

from uuid import uuid4

import pytest
from geoalchemy2 import Geometry, WKTElement
from sqlalchemy import text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.hazard import (
    Hazard,
    HazardReport,
    HazardSeverity,
    HazardStatus,
    HazardType,
)
from app.models.user import User


def test_model_metadata_registers_all_core_tables():
    assert {"users", "hazards", "hazard_reports"}.issubset(
        User.metadata.tables.keys()
    )


def test_spatial_columns_use_wgs84_point_geometry():
    for model in (Hazard, HazardReport):
        geometry = model.__table__.c.location.type
        assert isinstance(geometry, Geometry)
        assert geometry.geometry_type == "POINT"
        assert geometry.srid == 4326
        assert geometry.spatial_index is False


def test_hazard_report_media_uses_postgresql_jsonb():
    assert isinstance(HazardReport.__table__.c.media_urls.type, JSONB)
    assert Hazard.__table__.c.location.nullable is False
    assert HazardReport.__table__.c.location.nullable is False
    assert HazardReport.__table__.c.hazard_type.nullable is False


def test_core_schema_declares_expected_indexes_and_foreign_keys():
    hazard_indexes = {index.name for index in Hazard.__table__.indexes}
    report_indexes = {index.name for index in HazardReport.__table__.indexes}

    assert {
        "idx_hazards_location",
        "idx_hazards_status",
        "idx_hazards_type",
        "idx_hazards_expires",
    }.issubset(hazard_indexes)
    assert {
        "idx_hazard_reports_location",
        "idx_hazard_reports_reporter",
        "idx_hazard_reports_hazard",
        "idx_hazard_reports_created",
        "idx_hazard_reports_device",
    }.issubset(report_indexes)
    assert {
        fk.target_fullname for fk in HazardReport.__table__.c.reporter_id.foreign_keys
    } == {"users.id"}
    assert {
        fk.target_fullname for fk in HazardReport.__table__.c.hazard_id.foreign_keys
    } == {"hazards.id"}


@pytest.mark.asyncio
async def test_test_database_has_postgis_and_expected_tables(
    db_session: AsyncSession,
):
    postgis_version = await db_session.scalar(text("SELECT PostGIS_Version()"))

    table_names = set(
        (
            await db_session.scalars(
                text(
                    "SELECT table_name FROM information_schema.tables "
                    "WHERE table_schema = current_schema()"
                )
            )
        ).all()
    )

    assert postgis_version
    assert {"users", "hazards", "hazard_reports"}.issubset(table_names)


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("unique_field", "value"),
    [
        ("firebase_uid", "firebase-unique-id"),
        ("device_id", "device-unique-id"),
        ("email", "unique@example.test"),
        ("phone_number", "+15551234567"),
    ],
)
async def test_user_identity_fields_are_unique(
    db_session: AsyncSession, unique_field: str, value: str
):
    db_session.add(User(**{unique_field: value}))
    await db_session.flush()
    db_session.add(User(**{unique_field: value}))

    with pytest.raises(IntegrityError):
        await db_session.flush()
    await db_session.rollback()


@pytest.mark.asyncio
@pytest.mark.parametrize("foreign_key", ["reporter_id", "hazard_id"])
async def test_hazard_report_rejects_missing_foreign_key_target(
    db_session: AsyncSession, foreign_key: str
):
    db_session.add(
        HazardReport(
            **{foreign_key: uuid4()},
            hazard_type=HazardType.POTHOLE,
            location=WKTElement("POINT(77.5946 12.9716)", srid=4326),
        )
    )

    with pytest.raises(IntegrityError):
        await db_session.flush()
    await db_session.rollback()


def test_hazard_model_declares_expected_defaults():
    assert Hazard.__table__.c.status.default.arg == HazardStatus.PENDING
    assert Hazard.__table__.c.severity.default.arg == HazardSeverity.MODERATE
