"""Application configuration."""

import json
from typing import List, Optional

from pydantic import ValidationInfo, field_validator, model_validator
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings with security validation."""

    # Project
    PROJECT_NAME: str = "WayVigil AI"
    VERSION: str = "0.1.0"
    DEBUG: bool = False
    ENVIRONMENT: str = "development"

    # API
    API_V1_STR: str = "/api/v1"

    # CORS - Empty default requires explicit configuration
    CORS_ORIGINS: List[str] = []

    # Database
    DATABASE_URL: str = "postgresql://postgres:postgres@localhost:5432/saferoute"
    DATABASE_POOL_SIZE: int = 5
    DATABASE_MAX_OVERFLOW: int = 10

    # Security - No default to prevent accidental weak secrets
    SECRET_KEY: str = ""
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    ALGORITHM: str = "HS256"

    # Firebase
    FIREBASE_CREDENTIALS_PATH: Optional[str] = None
    FIREBASE_PROJECT_ID: Optional[str] = None

    # Storage
    S3_BUCKET: Optional[str] = None
    S3_ENDPOINT: Optional[str] = None
    S3_ACCESS_KEY: Optional[str] = None
    S3_SECRET_KEY: Optional[str] = None
    S3_REGION: str = "us-east-1"

    # Google Maps
    GOOGLE_MAPS_API_KEY: Optional[str] = None

    # Rate Limiting
    RATE_LIMIT_PER_MINUTE: int = 60

    @field_validator("SECRET_KEY")
    @classmethod
    def validate_secret_key(cls, v: str, info: ValidationInfo) -> str:
        """Ensure SECRET_KEY is set and sufficiently strong."""
        if not v:
            # Allow empty in test environment, otherwise require it
            if info.data.get("ENVIRONMENT") == "test":
                return "test-secret-key-minimum-32-characters-long"
            raise ValueError(
                "SECRET_KEY must be set via environment variable. "
                "Generate a secure key with: openssl rand -hex 32"
            )
        if len(v) < 32:
            raise ValueError(
                f"SECRET_KEY must be at least 32 characters long (got {len(v)}). "
                "Generate a secure key with: openssl rand -hex 32"
            )
        # Prevent use of the example/placeholder secret
        lowered = v.lower()
        if any(
            marker in lowered
            for marker in (
                "your-secret-key",
                "change-in-production",
                "local-dev-compose-secret-",
            )
        ):
            raise ValueError(
                "SECRET_KEY appears to be a placeholder. "
                "Set a unique secure key via environment variable."
            )
        return v

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def parse_cors_origins(cls, v):
        """Accept either a JSON list or comma-separated origins."""
        if isinstance(v, str):
            stripped = v.strip()
            if not stripped:
                return []
            if stripped.startswith("["):
                return json.loads(stripped)
            return [origin.strip() for origin in stripped.split(",") if origin.strip()]
        return v

    @field_validator("CORS_ORIGINS")
    @classmethod
    def validate_cors_origins(cls, v: List[str], info: ValidationInfo) -> List[str]:
        """Validate CORS origins."""
        if "*" in v and info.data.get("ENVIRONMENT") not in {"development", "test"}:
            raise ValueError(
                "CORS_ORIGINS cannot be '*' in non-development environments. "
                "Specify explicit allowed origins."
            )
        return v

    @model_validator(mode="after")
    def validate_production_settings(self) -> "Settings":
        """Additional validations for production environment."""
        if self.ENVIRONMENT == "production":
            if self.DEBUG:
                raise ValueError("DEBUG cannot be True in production environment")
            if not self.CORS_ORIGINS or "*" in self.CORS_ORIGINS:
                raise ValueError(
                    "CORS_ORIGINS must be explicitly set with specific origins "
                    "in production"
                )
        return self

    model_config = {"env_file": ".env", "case_sensitive": True}


settings = Settings()
