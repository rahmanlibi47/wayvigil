"""Smoke tests for public API behavior."""

import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_health_endpoint_reports_healthy_service(client: AsyncClient):
    response = await client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
    assert response.json()["version"]


@pytest.mark.asyncio
async def test_root_endpoint_returns_project_metadata(client: AsyncClient):
    response = await client.get("/")

    assert response.status_code == 200
    assert response.json()["name"] == "WayVigil AI"
    assert response.json()["version"]
