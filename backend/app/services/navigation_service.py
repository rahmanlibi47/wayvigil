"""Google Places and Routes API proxy operations."""

from typing import Any
from urllib.parse import quote

import httpx
from fastapi import HTTPException, status
from pydantic import ValidationError

from app.core.config import settings
from app.schemas.navigation import (
    ComputeRouteRequest,
    NavigationRouteResponse,
    NavigationRoutesResponse,
    NavigationRouteStep,
    PlaceAutocompleteRequest,
    PlaceDetailsResponse,
    PlaceSuggestion,
)

PLACES_BASE_URL = "https://places.googleapis.com/v1"
ROUTES_COMPUTE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
PLACES_AUTOCOMPLETE_FIELDS = (
    "suggestions.placePrediction.placeId,"
    "suggestions.placePrediction.text.text,"
    "suggestions.placePrediction.structuredFormat.mainText.text,"
    "suggestions.placePrediction.structuredFormat.secondaryText.text"
)
ROUTES_FIELDS = ",".join(
    [
        "routes.distanceMeters",
        "routes.duration",
        "routes.routeLabels",
        "routes.polyline.encodedPolyline",
        "routes.legs.steps.distanceMeters",
        "routes.legs.steps.staticDuration",
        "routes.legs.steps.navigationInstruction.instructions",
        "routes.legs.steps.navigationInstruction.maneuver",
        "routes.legs.steps.startLocation.latLng",
        "routes.legs.steps.endLocation.latLng",
        "routes.legs.steps.polyline.encodedPolyline",
    ]
)


def _api_key() -> str:
    key = settings.GOOGLE_MAPS_API_KEY
    if not key:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation search is not configured",
        )
    return key


def _provider_failure(response: httpx.Response) -> HTTPException:
    if (
        response.status_code in {401, 403, status.HTTP_429_TOO_MANY_REQUESTS}
        or response.status_code >= 500
    ):
        return HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation provider is temporarily unavailable",
        )
    return HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail="Navigation provider could not process the request",
    )


async def _post_google(
    url: str, body: dict[str, Any], field_mask: str
) -> dict[str, Any]:
    headers = {
        "X-Goog-Api-Key": _api_key(),
        "X-Goog-FieldMask": field_mask,
    }
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(url, json=body, headers=headers)
    except httpx.TimeoutException as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation provider timed out",
        ) from exc
    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation provider is temporarily unavailable",
        ) from exc

    if response.is_error:
        raise _provider_failure(response)
    try:
        payload = response.json()
        if not isinstance(payload, dict):
            raise ValueError("Provider response must be an object")
        return payload
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Navigation provider returned an invalid response",
        ) from exc


async def autocomplete_places(
    request: PlaceAutocompleteRequest,
) -> list[PlaceSuggestion]:
    body: dict[str, Any] = {"input": request.input}
    if request.session_token:
        body["sessionToken"] = request.session_token
    if request.latitude is not None and request.longitude is not None:
        body["locationBias"] = {
            "circle": {
                "center": {
                    "latitude": request.latitude,
                    "longitude": request.longitude,
                },
                "radius": 20000,
            }
        }

    response = await _post_google(
        f"{PLACES_BASE_URL}/places:autocomplete",
        body,
        PLACES_AUTOCOMPLETE_FIELDS,
    )
    suggestions = []
    provider_suggestions = response.get("suggestions", [])
    if not isinstance(provider_suggestions, list):
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Navigation provider returned invalid search results",
        )
    for item in provider_suggestions:
        if not isinstance(item, dict):
            continue
        prediction = item.get("placePrediction")
        if not isinstance(prediction, dict) or not prediction.get("placeId"):
            continue
        structured = prediction.get("structuredFormat", {})
        if not isinstance(structured, dict):
            structured = {}
        main_text_obj = structured.get("mainText", {})
        secondary_text_obj = structured.get("secondaryText", {})
        prediction_text = prediction.get("text", {})
        main_text = (
            main_text_obj.get("text") if isinstance(main_text_obj, dict) else None
        )
        secondary_text = (
            secondary_text_obj.get("text")
            if isinstance(secondary_text_obj, dict)
            else None
        )
        full_text = (
            prediction_text.get("text", "") if isinstance(prediction_text, dict) else ""
        )
        suggestions.append(
            PlaceSuggestion(
                place_id=prediction["placeId"],
                name=main_text or full_text,
                address=secondary_text or full_text,
            )
        )
    return suggestions[:5]


async def get_place_details(
    place_id: str, session_token: str | None = None
) -> PlaceDetailsResponse:
    headers = {
        "X-Goog-Api-Key": _api_key(),
        "X-Goog-FieldMask": "id,displayName,formattedAddress,location",
    }
    url = f"{PLACES_BASE_URL}/places/{quote(place_id, safe='')}"
    params = {"sessionToken": session_token} if session_token else None
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers, params=params)
    except httpx.TimeoutException as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation provider timed out",
        ) from exc
    except httpx.RequestError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Navigation provider is temporarily unavailable",
        ) from exc
    if response.is_error:
        raise _provider_failure(response)
    try:
        place = response.json()
        if not isinstance(place, dict):
            raise ValueError("Provider place must be an object")
        location = place.get("location")
        display_name = place.get("displayName", {})
        address = place.get("formattedAddress", "")
        response_place_id = place.get("id")
        if display_name is None:
            display_name = {}
        if (
            not isinstance(location, dict)
            or not isinstance(display_name, dict)
            or not isinstance(address, str)
            or not isinstance(response_place_id, str)
        ):
            raise ValueError("Provider place has invalid fields")
        name = display_name.get("text", "Selected place")
        if not isinstance(name, str):
            raise ValueError("Provider place name must be text")
        return PlaceDetailsResponse(
            place_id=response_place_id,
            name=name,
            address=address,
            latitude=location["latitude"],
            longitude=location["longitude"],
        )
    except (AttributeError, KeyError, TypeError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Navigation provider returned an invalid place",
        ) from exc


def _duration_seconds(value: str | None) -> int:
    if not value:
        return 0
    try:
        return max(0, int(float(str(value).removesuffix("s"))))
    except (AttributeError, TypeError, ValueError):
        return 0


async def compute_route(request: ComputeRouteRequest) -> NavigationRoutesResponse:
    body = {
        "origin": {
            "location": {
                "latLng": request.origin.model_dump(by_alias=True),
            }
        },
        "destination": {
            "location": {
                "latLng": request.destination.model_dump(by_alias=True),
            }
        },
        "travelMode": request.travel_mode,
        "computeAlternativeRoutes": request.compute_alternatives,
        "languageCode": "en",
        "units": "METRIC",
    }
    response = await _post_google(ROUTES_COMPUTE_URL, body, ROUTES_FIELDS)
    routes = response.get("routes", [])
    if not routes:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No route is available for the selected destination",
        )
    try:
        normalized_routes = []
        for route in routes[:4]:
            normalized_routes.append(_normalize_route(route, request.travel_mode))
        return NavigationRoutesResponse(routes=normalized_routes)
    except (
        AttributeError,
        IndexError,
        KeyError,
        TypeError,
        ValidationError,
        ValueError,
    ) as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Navigation provider returned an invalid route",
        ) from exc


def _normalize_route(
    route: dict[str, Any], travel_mode: str
) -> NavigationRouteResponse:
    polyline = route.get("polyline", {}).get("encodedPolyline", "")
    _validate_route_polyline(polyline)
    legs = route.get("legs", [])
    raw_steps = [step for leg in legs for step in leg.get("steps", [])]
    steps = []
    for step in raw_steps:
        start = step.get("startLocation", {}).get("latLng", {})
        end = step.get("endLocation", {}).get("latLng", {})
        instruction = step.get("navigationInstruction", {})
        steps.append(
            NavigationRouteStep(
                instruction=instruction.get("instructions", "Continue"),
                distance_meters=step.get("distanceMeters", 0),
                duration_seconds=_duration_seconds(step.get("staticDuration")),
                maneuver=instruction.get("maneuver", "STRAIGHT"),
                start_location=start,
                end_location=end,
                polyline=step.get("polyline", {}).get("encodedPolyline", ""),
            )
        )
    warnings = []
    if travel_mode == "TWO_WHEELER":
        warnings.append(
            "Two-wheeler routes are in beta and may not include every road "
            "restriction. Follow local traffic laws and posted signs."
        )
    return NavigationRouteResponse(
        route_label=(route.get("routeLabels") or ["DEFAULT_ROUTE"])[0],
        polyline=polyline,
        distance_meters=route.get("distanceMeters", 0),
        duration_seconds=_duration_seconds(route.get("duration")),
        steps=steps,
        warnings=warnings,
    )


def _validate_route_polyline(encoded: Any) -> None:
    if not isinstance(encoded, str) or not encoded:
        raise ValueError("Route polyline is missing")

    index = 0
    latitude = 0
    longitude = 0
    point_count = 0

    def read_delta() -> int:
        nonlocal index
        result = 0
        shift = 0
        while True:
            if index >= len(encoded) or shift > 30:
                raise ValueError("Route polyline is truncated or too large")
            code = ord(encoded[index])
            index += 1
            if code < 63 or code > 126:
                raise ValueError("Route polyline contains an invalid character")
            value = code - 63
            result |= (value & 0x1F) << shift
            if value < 0x20:
                break
            shift += 5
        return ~(result >> 1) if result & 1 else result >> 1

    while index < len(encoded):
        latitude += read_delta()
        longitude += read_delta()
        if not -9_000_000 <= latitude <= 9_000_000:
            raise ValueError("Route polyline latitude is out of range")
        if not -18_000_000 <= longitude <= 18_000_000:
            raise ValueError("Route polyline longitude is out of range")
        point_count += 1

    if point_count < 2:
        raise ValueError("Route polyline must contain at least two points")
