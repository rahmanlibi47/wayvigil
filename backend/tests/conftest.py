"""Pytest configuration."""

import asyncio
import os
import re
from typing import AsyncGenerator

# Configure test settings before importing application modules. Settings are
# instantiated at import time and require a secret outside test mode.
os.environ["ENVIRONMENT"] = "test"
os.environ["SECRET_KEY"] = "test-secret-key-that-is-32-characters-long"
os.environ["DATABASE_URL"] = os.environ.get(
    "DATABASE_URL",
    "postgresql+asyncpg://postgres:postgres@localhost:5433/saferoute_test",
)
os.environ["CORS_ORIGINS"] = '["*"]'

import pytest
import pytest_asyncio
from httpx import AsyncClient
from sqlalchemy import text
from sqlalchemy.engine import make_url
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker

from app.core.database import get_db  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture(scope="session")
def event_loop():
    """Create an instance of the default event loop for the test session."""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture(scope="session")
async def ensure_test_database() -> AsyncGenerator[None, None]:
    """Create the configured test database and PostGIS extension if missing."""
    test_url = make_url(os.environ["DATABASE_URL"])
    database_name = test_url.database
    if not database_name:
        raise ValueError("DATABASE_URL must include a database name")

    admin_url = test_url.set(database="postgres")
    admin_engine = create_async_engine(admin_url)
    async with admin_engine.connect() as connection:
        connection = await connection.execution_options(isolation_level="AUTOCOMMIT")
        await connection.execute(
            text("SELECT pg_advisory_lock(hashtext('saferoute_test_database_setup'))")
        )
        try:
            exists = await connection.scalar(
                text("SELECT 1 FROM pg_database WHERE datname = :name"),
                {"name": database_name},
            )
            if not exists:
                quoted_name = connection.dialect.identifier_preparer.quote(database_name)
                await connection.execute(text(f"CREATE DATABASE {quoted_name}"))
        finally:
            await connection.execute(
                text("SELECT pg_advisory_unlock(hashtext('saferoute_test_database_setup'))")
            )
    await admin_engine.dispose()

    test_engine = create_async_engine(os.environ["DATABASE_URL"])
    async with test_engine.connect() as connection:
        connection = await connection.execution_options(isolation_level="AUTOCOMMIT")
        await connection.execute(
            text("SELECT pg_advisory_lock(hashtext('saferoute_test_postgis_setup'))")
        )
        try:
            await connection.execute(text("CREATE EXTENSION IF NOT EXISTS postgis"))
        finally:
            await connection.execute(
                text("SELECT pg_advisory_unlock(hashtext('saferoute_test_postgis_setup'))")
            )
    await test_engine.dispose()


@pytest_asyncio.fixture(scope="session")
async def worker_schema(ensure_test_database: None) -> AsyncGenerator[str, None]:
    """Give each pytest-xdist worker its own isolated PostgreSQL schema."""
    worker_id = os.environ.get("PYTEST_XDIST_WORKER", "gw0")
    run_id = os.environ.get("PYTEST_XDIST_TESTRUNUID", str(os.getpid()))
    safe_suffix = re.sub(r"[^a-zA-Z0-9_]", "_", f"{run_id}_{worker_id}")[:48]
    schema = f"pytest_{safe_suffix}"
    admin_engine = create_async_engine(os.environ["DATABASE_URL"])

    async with admin_engine.begin() as connection:
        await connection.execute(text(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE'))
        await connection.execute(text(f'CREATE SCHEMA "{schema}"'))

    try:
        yield schema
    finally:
        async with admin_engine.begin() as connection:
            await connection.execute(text(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE'))
        await admin_engine.dispose()


@pytest_asyncio.fixture
async def db_session(worker_schema: str) -> AsyncGenerator[AsyncSession, None]:
    """Create a test session scoped to this worker's database schema."""
    from app.core.database import Base

    # Create engine for tests
    test_engine = create_async_engine(
        os.environ["DATABASE_URL"],
        echo=False,
        future=True,
        execution_options={"schema_translate_map": {None: worker_schema}},
        connect_args={
            "server_settings": {
                "search_path": f"{worker_schema},public",
            }
        },
    )

    async_session = sessionmaker(
        test_engine, class_=AsyncSession, expire_on_commit=False
    )

    # Create tables
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with async_session() as session:
        yield session
        await session.rollback()

    # Cleanup
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

    await test_engine.dispose()


@pytest_asyncio.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    """Create a client whose API requests use this worker's isolated session."""
    async def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    try:
        async with AsyncClient(app=app, base_url="http://test") as ac:
            yield ac
    finally:
        app.dependency_overrides.pop(get_db, None)
