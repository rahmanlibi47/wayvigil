"""Acceptance tests for authenticated navigation provider endpoints."""

import pytest
from fastapi import HTTPException
from httpx import AsyncClient

from app.core import config
from app.schemas.navigation import (
    ComputeRouteRequest,
    Coordinate,
    NavigationRouteResponse,
    NavigationRoutesResponse,
    NavigationRouteStep,
    PlaceAutocompleteRequest,
    PlaceDetailsResponse,
    PlaceSuggestion,
)
from app.services import navigation_service


async def _auth_headers(client: AsyncClient) -> dict[str, str]:
    response = await client.post(
        "/api/v1/auth/anonymous", json={"device_id": "navigation-test-device"}
    )
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


@pytest.mark.asyncio
async def test_place_autocomplete_requires_authentication(client: AsyncClient):
    response = await client.post(
        "/api/v1/navigation/places/autocomplete", json={"input": "Central Park"}
    )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_place_autocomplete_validates_query_length(client: AsyncClient):
    headers = await _auth_headers(client)
    response = await client.post(
        "/api/v1/navigation/places/autocomplete",
        headers=headers,
        json={"input": "x"},
    )

    assert response.status_code == 422


@pytest.mark.asyncio
async def test_place_autocomplete_returns_safe_error_when_key_is_missing(
    client: AsyncClient, monkeypatch
):
    headers = await _auth_headers(client)
    monkeypatch.setattr(config.settings, "GOOGLE_MAPS_API_KEY", None)
    response = await client.post(
        "/api/v1/navigation/places/autocomplete",
        headers=headers,
        json={"input": "Cubbon Park"},
    )

    assert response.status_code == 503
    assert "key" not in response.text.lower()


@pytest.mark.asyncio
async def test_place_autocomplete_returns_normalized_predictions(
    client: AsyncClient, monkeypatch
):
    headers = await _auth_headers(client)

    async def fake_autocomplete(_payload):
        return [
            PlaceSuggestion(
                place_id="place-123", name="Cubbon Park", address="Bengaluru"
            )
        ]

    monkeypatch.setattr(navigation_service, "autocomplete_places", fake_autocomplete)
    response = await client.post(
        "/api/v1/navigation/places/autocomplete",
        headers=headers,
        json={"input": "Cubbon Park", "session_token": "session-1"},
    )

    assert response.status_code == 200
    assert response.json() == [
        {"place_id": "place-123", "name": "Cubbon Park", "address": "Bengaluru"}
    ]


@pytest.mark.asyncio
async def test_place_details_returns_coordinates(client: AsyncClient, monkeypatch):
    headers = await _auth_headers(client)

    async def fake_details(_place_id, session_token=None):
        assert session_token == "session-123"
        return PlaceDetailsResponse(
            place_id="place-123",
            name="Cubbon Park",
            address="Bengaluru",
            latitude=12.9763,
            longitude=77.5929,
        )

    monkeypatch.setattr(navigation_service, "get_place_details", fake_details)
    response = await client.post(
        "/api/v1/navigation/places/details",
        headers=headers,
        json={"place_id": "place-123", "session_token": "session-123"},
    )

    assert response.status_code == 200
    assert response.json()["latitude"] == pytest.approx(12.9763)
    assert response.json()["longitude"] == pytest.approx(77.5929)


@pytest.mark.asyncio
async def test_route_endpoint_rejects_out_of_range_coordinates(client: AsyncClient):
    headers = await _auth_headers(client)
    response = await client.post(
        "/api/v1/navigation/routes",
        headers=headers,
        json={
            "origin": {"latitude": 91, "longitude": 77},
            "destination": {"latitude": 12, "longitude": 77},
            "travel_mode": "DRIVE",
        },
    )

    assert response.status_code == 422


@pytest.mark.asyncio
async def test_route_endpoint_returns_normalized_route(
    client: AsyncClient,
    monkeypatch,
):
    headers = await _auth_headers(client)

    async def fake_compute_route(_payload):
        return NavigationRoutesResponse(
            routes=[
                NavigationRouteResponse(
                    polyline="encoded-route",
                    distance_meters=1200,
                    duration_seconds=240,
                    steps=[
                        NavigationRouteStep(
                            instruction="Turn right",
                            distance_meters=200,
                            duration_seconds=40,
                            maneuver="TURN_RIGHT",
                            start_location={"latitude": 12.97, "longitude": 77.59},
                            end_location={"latitude": 12.971, "longitude": 77.591},
                            polyline="encoded-step",
                        )
                    ],
                )
            ]
        )

    monkeypatch.setattr(navigation_service, "compute_route", fake_compute_route)
    response = await client.post(
        "/api/v1/navigation/routes",
        headers=headers,
        json={
            "origin": {"latitude": 12.97, "longitude": 77.59},
            "destination": {"latitude": 12.98, "longitude": 77.60},
        },
    )

    assert response.status_code == 200
    assert response.json()["routes"][0]["distance_meters"] == 1200
    assert response.json()["routes"][0]["steps"][0]["maneuver"] == "TURN_RIGHT"


@pytest.mark.asyncio
async def test_route_service_normalizes_google_route_payload(monkeypatch):
    async def fake_post_google(_url, _body, _field_mask):
        return {
            "routes": [
                {
                    "distanceMeters": 950,
                    "duration": "125s",
                    "polyline": {"encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
                    "legs": [
                        {
                            "steps": [
                                {
                                    "distanceMeters": 120,
                                    "staticDuration": "15s",
                                    "navigationInstruction": {
                                        "instructions": "Turn left",
                                        "maneuver": "TURN_LEFT",
                                    },
                                    "startLocation": {
                                        "latLng": {
                                            "latitude": 12.97,
                                            "longitude": 77.59,
                                        }
                                    },
                                    "endLocation": {
                                        "latLng": {
                                            "latitude": 12.971,
                                            "longitude": 77.591,
                                        }
                                    },
                                    "polyline": {
                                        "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
                                    },
                                }
                            ]
                        }
                    ],
                }
            ]
        }

    monkeypatch.setattr(navigation_service, "_post_google", fake_post_google)
    result = await navigation_service.compute_route(
        ComputeRouteRequest(
            origin=Coordinate(latitude=12.97, longitude=77.59),
            destination=Coordinate(latitude=12.98, longitude=77.60),
        )
    )

    route = result.routes[0]
    assert route.distance_meters == 950
    assert route.duration_seconds == 125
    assert route.polyline == "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    assert route.steps[0].maneuver == "TURN_LEFT"
    assert route.steps[0].duration_seconds == 15


@pytest.mark.asyncio
async def test_route_service_requests_and_normalizes_alternate_routes(monkeypatch):
    captured = {}

    async def fake_post_google(_url, body, _field_mask):
        captured.update(body=body)
        return {
            "routes": [
                {
                    "routeLabels": [label],
                    "distanceMeters": distance,
                    "duration": "120s",
                    "polyline": {"encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
                    "legs": [],
                }
                for label, distance in [
                    ("DEFAULT_ROUTE", 900),
                    ("DEFAULT_ROUTE_ALTERNATE", 1100),
                ]
            ]
        }

    monkeypatch.setattr(navigation_service, "_post_google", fake_post_google)
    result = await navigation_service.compute_route(
        ComputeRouteRequest(
            origin=Coordinate(latitude=12.97, longitude=77.59),
            destination=Coordinate(latitude=12.98, longitude=77.60),
            travel_mode="DRIVE",
            compute_alternatives=True,
        )
    )

    assert captured["body"]["computeAlternativeRoutes"] is True
    assert [route.distance_meters for route in result.routes] == [900, 1100]
    assert result.routes[1].route_label == "DEFAULT_ROUTE_ALTERNATE"


@pytest.mark.asyncio
async def test_two_wheeler_route_uses_requested_mode_and_includes_safety_warning(
    monkeypatch,
):
    captured = {}

    async def fake_post_google(_url, body, _field_mask):
        captured.update(body=body)
        return {
            "routes": [
                {
                    "distanceMeters": 950,
                    "duration": "125s",
                    "polyline": {"encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
                    "legs": [],
                }
            ]
        }

    monkeypatch.setattr(navigation_service, "_post_google", fake_post_google)
    result = await navigation_service.compute_route(
        ComputeRouteRequest(
            origin=Coordinate(latitude=12.97, longitude=77.59),
            destination=Coordinate(latitude=12.98, longitude=77.60),
            travel_mode="TWO_WHEELER",
            compute_alternatives=False,
        )
    )

    assert captured["body"]["travelMode"] == "TWO_WHEELER"
    assert captured["body"]["computeAlternativeRoutes"] is False
    assert result.routes[0].warnings


@pytest.mark.asyncio
async def test_autocomplete_service_sends_key_in_header_and_normalizes_predictions(
    monkeypatch,
):
    captured = {}
    monkeypatch.setattr(config.settings, "GOOGLE_MAPS_API_KEY", "test-server-key")

    class FakeResponse:
        is_error = False

        def json(self):
            return {
                "suggestions": [
                    {
                        "placePrediction": {
                            "placeId": "place-123",
                            "text": {"text": "Cubbon Park, Bengaluru"},
                            "structuredFormat": {
                                "mainText": {"text": "Cubbon Park"},
                                "secondaryText": {"text": "Bengaluru"},
                            },
                        }
                    }
                ]
            }

    class FakeClient:
        def __init__(self, **_kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args):
            return None

        async def post(self, url, json, headers):
            captured.update(url=url, body=json, headers=headers)
            return FakeResponse()

    monkeypatch.setattr(navigation_service.httpx, "AsyncClient", FakeClient)
    results = await navigation_service.autocomplete_places(
        PlaceAutocompleteRequest(input="Cubbon Park", session_token="session-123")
    )

    assert results[0].name == "Cubbon Park"
    assert results[0].address == "Bengaluru"
    assert captured["url"].startswith("https://places.googleapis.com/")
    assert captured["headers"]["X-Goog-Api-Key"] == "test-server-key"
    assert "test-server-key" not in captured["url"]
    assert captured["body"]["sessionToken"] == "session-123"


@pytest.mark.asyncio
async def test_places_provider_quota_error_is_sanitized(monkeypatch):
    monkeypatch.setattr(config.settings, "GOOGLE_MAPS_API_KEY", "test-server-key")

    class FakeResponse:
        is_error = True
        status_code = 403

    class FakeClient:
        def __init__(self, **_kwargs):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args):
            return None

        async def post(self, *_args, **_kwargs):
            return FakeResponse()

    monkeypatch.setattr(navigation_service.httpx, "AsyncClient", FakeClient)
    with pytest.raises(HTTPException) as error:
        await navigation_service.autocomplete_places(
            PlaceAutocompleteRequest(input="Cubbon Park")
        )

    assert error.value.status_code == 503
    assert "test-server-key" not in error.value.detail
