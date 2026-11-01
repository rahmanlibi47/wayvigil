"""Bring existing databases up to the current model schema.

Revision ID: 20261031_0001
"""

from alembic import op
from sqlalchemy import inspect
from sqlalchemy.dialects.postgresql import JSONB

from app.core.database import Base
from app.models import hazard, user  # noqa: F401 - register model metadata

revision = "20261031_0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis")

    # create_all supplies the baseline for a fresh database; existing tables are
    # left intact and receive the explicit additive/type updates below.
    Base.metadata.create_all(bind=bind)

    op.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS device_id VARCHAR(128)")
    op.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ix_users_device_id ON users (device_id)"
    )

    columns = {
        column["name"]: column
        for column in inspect(bind).get_columns("hazard_reports")
    }
    media_column = columns.get("media_urls")
    if media_column is not None and not isinstance(media_column["type"], JSONB):
        op.execute(
            "ALTER TABLE hazard_reports ALTER COLUMN media_urls TYPE JSONB "
            "USING CASE WHEN media_urls IS NULL THEN NULL "
            "ELSE to_jsonb(string_to_array(media_urls, ',')) END"
        )

    # create_all does not add indexes to tables that already existed.
    index_statements = (
        "CREATE INDEX IF NOT EXISTS idx_hazards_status ON hazards (status)",
        "CREATE INDEX IF NOT EXISTS idx_hazards_type ON hazards (hazard_type)",
        "CREATE INDEX IF NOT EXISTS idx_hazards_expires ON hazards (expires_at)",
        "CREATE INDEX IF NOT EXISTS idx_hazard_reports_reporter ON hazard_reports (reporter_id)",
        "CREATE INDEX IF NOT EXISTS idx_hazard_reports_hazard ON hazard_reports (hazard_id)",
        "CREATE INDEX IF NOT EXISTS idx_hazard_reports_created ON hazard_reports (created_at)",
        "CREATE INDEX IF NOT EXISTS idx_hazard_reports_device ON hazard_reports (device_id)",
        "CREATE INDEX IF NOT EXISTS idx_hazards_location ON hazards USING GIST (location)",
        "CREATE INDEX IF NOT EXISTS idx_hazard_reports_location ON hazard_reports USING GIST (location)",
    )
    for statement in index_statements:
        op.execute(statement)


def downgrade() -> None:
    # The JSONB conversion and device-to-user association are intentionally
    # irreversible without potentially losing data.
    pass
