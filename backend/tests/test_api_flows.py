"""Integration tests for authenticated API workflows."""

import pytest
from geoalchemy2 import WKTElement
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.hazard import Hazard, HazardType


async def _login_anonymously(client: AsyncClient, device_id: str) -> tuple[dict, str]:
    response = await client.post(
        "/api/v1/auth/anonymous", json={"device_id": device_id}
    )
    assert response.status_code == 200
    payload = response.json()
    headers = {"Authorization": f"Bearer {payload['access_token']}"}
    return headers, payload["user"]["id"]


@pytest.mark.asyncio
async def test_anonymous_login_reuses_account_for_device(client: AsyncClient):
    first_headers, first_user_id = await _login_anonymously(client, "device-reuse")
    second_headers, second_user_id = await _login_anonymously(client, "device-reuse")

    assert first_user_id == second_user_id
    first_profile = await client.get("/api/v1/users/me", headers=first_headers)
    second_profile = await client.get("/api/v1/users/me", headers=second_headers)
    assert first_profile.status_code == second_profile.status_code == 200
    assert first_profile.json()["id"] == second_profile.json()["id"]


@pytest.mark.asyncio
async def test_hazard_report_requires_authentication(client: AsyncClient):
    response = await client.post(
        "/api/v1/hazards/reports",
        json={
            "hazard_type": "pothole",
            "location": {"latitude": 12.9716, "longitude": 77.5946},
        },
    )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_user_can_create_list_and_delete_own_hazard_report(
    client: AsyncClient,
):
    headers, _ = await _login_anonymously(client, "report-owner")
    create_response = await client.post(
        "/api/v1/hazards/reports",
        headers=headers,
        json={
            "hazard_type": "pothole",
            "description": "Deep pothole near the junction",
            "location": {
                "latitude": 12.9716,
                "longitude": 77.5946,
                "accuracy": 5.0,
            },
            "media_urls": ["https://example.test/pothole.jpg"],
        },
    )

    assert create_response.status_code == 201
    report = create_response.json()
    assert report["hazard_type"] == "pothole"
    assert report["location"]["latitude"] == pytest.approx(12.9716)
    assert report["location"]["longitude"] == pytest.approx(77.5946)

    list_response = await client.get("/api/v1/hazards/reports/me", headers=headers)
    assert list_response.status_code == 200
    assert [item["id"] for item in list_response.json()] == [report["id"]]

    delete_response = await client.delete(
        f"/api/v1/hazards/reports/{report['id']}", headers=headers
    )
    assert delete_response.status_code == 204

    get_response = await client.get(
        f"/api/v1/hazards/reports/{report['id']}", headers=headers
    )
    assert get_response.status_code == 404


@pytest.mark.asyncio
async def test_user_cannot_delete_another_users_hazard_report(
    client: AsyncClient,
):
    owner_headers, _ = await _login_anonymously(client, "report-owner-2")
    other_headers, _ = await _login_anonymously(client, "report-other-2")
    create_response = await client.post(
        "/api/v1/hazards/reports",
        headers=owner_headers,
        json={
            "hazard_type": "pothole",
            "location": {"latitude": 12.9716, "longitude": 77.5946},
        },
    )
    assert create_response.status_code == 201
    report_id = create_response.json()["id"]

    delete_response = await client.delete(
        f"/api/v1/hazards/reports/{report_id}", headers=other_headers
    )
    assert delete_response.status_code == 403

    report_response = await client.get(
        f"/api/v1/hazards/reports/{report_id}", headers=owner_headers
    )
    assert report_response.status_code == 200
    assert report_response.json()["id"] == report_id


@pytest.mark.asyncio
async def test_user_can_update_own_profile(client: AsyncClient):
    headers, _ = await _login_anonymously(client, "profile-update")

    response = await client.put(
        "/api/v1/users/me",
        headers=headers,
        json={"display_name": "WayVigil Driver", "preferred_language": "en"},
    )

    assert response.status_code == 200
    assert response.json()["display_name"] == "WayVigil Driver"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "params",
    [
        {"latitude": 91, "longitude": 0},
        {"latitude": 0, "longitude": 181},
        {"latitude": 0, "longitude": 0, "radius": 5001},
        {"latitude": 0, "longitude": 0, "radius": 0},
    ],
)
async def test_nearby_hazards_rejects_invalid_query_values(
    client: AsyncClient, params: dict
):
    response = await client.get("/api/v1/hazards/nearby", params=params)

    assert response.status_code == 422


@pytest.mark.asyncio
async def test_nearby_hazards_returns_coordinates(
    client: AsyncClient, db_session: AsyncSession
):
    db_session.add(
        Hazard(
            hazard_type=HazardType.POTHOLE,
            location=WKTElement("POINT(77.5946 12.9716)", srid=4326),
        )
    )
    await db_session.flush()

    response = await client.get(
        "/api/v1/hazards/nearby",
        params={
            "latitude": 12.9716,
            "longitude": 77.5946,
            "radius": 1000,
            "include_unverified": True,
        },
    )

    assert response.status_code == 200
    hazards = response.json()
    assert len(hazards) == 1
    assert hazards[0]["location"]["latitude"] == pytest.approx(12.9716)
    assert hazards[0]["location"]["longitude"] == pytest.approx(77.5946)
