"""Alembic migration acceptance tests."""

import os
from pathlib import Path
from typing import AsyncGenerator
from uuid import uuid4

import pytest
import pytest_asyncio
from alembic import command
from alembic.config import Config
from sqlalchemy import inspect, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncEngine, create_async_engine
from sqlalchemy.engine import make_url


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


@pytest_asyncio.fixture
async def migration_schema(
    ensure_test_database: None,
) -> AsyncGenerator[tuple[str, AsyncEngine], None]:
    database_url = make_url(os.environ["DATABASE_URL"])
    schema = f"test_migration_{uuid4().hex[:12]}"
    engine = create_async_engine(
        database_url,
        connect_args={"server_settings": {"search_path": f"{schema},public"}},
    )

    async with engine.begin() as connection:
        await connection.execute(text(f'CREATE SCHEMA "{schema}"'))

    try:
        yield schema, engine
    finally:
        await engine.dispose()
        admin_engine = create_async_engine(database_url)
        async with admin_engine.begin() as connection:
            await connection.execute(text(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE'))
        await admin_engine.dispose()


async def _upgrade_with_connection(connection: AsyncConnection) -> None:
    config = Config(str(REPOSITORY_ROOT / "backend" / "alembic.ini"))

    def run_upgrade(sync_connection):
        config.attributes["connection"] = sync_connection
        try:
            command.upgrade(config, "head")
        finally:
            config.attributes.pop("connection", None)

    await connection.run_sync(run_upgrade)


@pytest.mark.asyncio
async def test_fresh_database_migrates_to_current_revision(migration_schema):
    schema, engine = migration_schema

    async with engine.begin() as connection:
        await _upgrade_with_connection(connection)
        await _upgrade_with_connection(connection)

        def inspect_upgrade(sync_connection):
            inspector = inspect(sync_connection)
            tables = set(inspector.get_table_names(schema=schema))
            indexes = {
                index["name"]
                for index in inspector.get_indexes("hazards", schema=schema)
            }
            revision = sync_connection.execute(
                text(f'SELECT version_num FROM "{schema}".alembic_version')
            ).scalar_one()
            return tables, indexes, revision

        tables, indexes, revision = await connection.run_sync(inspect_upgrade)

    assert {"alembic_version", "users", "hazards", "hazard_reports"}.issubset(
        tables
    )
    assert "idx_hazards_location" in indexes
    assert revision == "20261031_0001"


@pytest.mark.asyncio
async def test_migration_upgrades_legacy_media_and_device_schema(migration_schema):
    from app.core.database import Base
    from app.models import hazard, user  # noqa: F401

    schema, engine = migration_schema
    legacy_report_id = uuid4()

    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text(
                f'ALTER TABLE "{schema}".hazard_reports '
                "ALTER COLUMN media_urls TYPE VARCHAR USING media_urls::text"
            )
        )
        await connection.execute(
            text(
                f'INSERT INTO "{schema}".hazard_reports '
                "(id, hazard_type, location, media_urls) "
                "VALUES (:id, 'POTHOLE', ST_GeomFromText("
                "'POINT(77.5946 12.9716)', 4326), :media_urls)"
            ),
            {
                "id": str(legacy_report_id),
                "media_urls": "legacy-photo-1,legacy-photo-2",
            },
        )
        await connection.execute(
            text(f'DROP INDEX IF EXISTS "{schema}".ix_users_device_id')
        )
        await connection.execute(
            text(f'ALTER TABLE "{schema}".users DROP COLUMN device_id')
        )
        await _upgrade_with_connection(connection)

        def inspect_upgrade(sync_connection):
            inspector = inspect(sync_connection)
            report_columns = {
                column["name"]: column
                for column in inspector.get_columns("hazard_reports", schema=schema)
            }
            user_columns = {
                column["name"]
                for column in inspector.get_columns("users", schema=schema)
            }
            user_indexes = {
                index["name"]
                for index in inspector.get_indexes("users", schema=schema)
            }
            media_count, first_media = sync_connection.execute(
                text(
                    f'SELECT jsonb_array_length(media_urls), media_urls ->> 0 '
                    f'FROM "{schema}".hazard_reports WHERE id = :report_id'
                ),
                {"report_id": str(legacy_report_id)},
            ).one()
            return report_columns, user_columns, user_indexes, media_count, first_media

        (
            report_columns,
            user_columns,
            user_indexes,
            media_count,
            first_media,
        ) = await connection.run_sync(inspect_upgrade)

    assert isinstance(report_columns["media_urls"]["type"], JSONB)
    assert "device_id" in user_columns
    assert "ix_users_device_id" in user_indexes
    assert media_count == 2
    assert first_media == "legacy-photo-1"
