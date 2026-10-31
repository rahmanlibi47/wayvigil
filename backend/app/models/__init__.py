"""Database models."""

from app.models.hazard import Hazard, HazardReport
from app.models.user import User

__all__ = ["User", "Hazard", "HazardReport"]
