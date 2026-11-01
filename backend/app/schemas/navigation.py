"""Request and response schemas for navigation provider operations."""

from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class PlaceAutocompleteRequest(BaseModel):
    input: str = Field(min_length=3, max_length=200)
    session_token: Optional[str] = Field(default=None, max_length=128)
    latitude: Optional[float] = Field(default=None, ge=-90, le=90, allow_inf_nan=False)
    longitude: Optional[float] = Field(
        default=None, ge=-180, le=180, allow_inf_nan=False
    )

    @field_validator("input")
    @classmethod
    def normalize_input(cls, value: str) -> str:
        value = value.strip()
        if len(value) < 3:
            raise ValueError(
                "Search input must contain at least three non-space characters"
            )
        return value

    @model_validator(mode="after")
    def validate_location_bias(self):
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("Latitude and longitude must be provided together")
        return self


class PlaceSuggestion(BaseModel):
    place_id: str
    name: str
    address: str


class PlaceDetailsRequest(BaseModel):
    place_id: str = Field(min_length=1, max_length=200)
    session_token: Optional[str] = Field(default=None, max_length=128)


class PlaceDetailsResponse(PlaceSuggestion):
    latitude: float = Field(ge=-90, le=90, allow_inf_nan=False)
    longitude: float = Field(ge=-180, le=180, allow_inf_nan=False)


class Coordinate(BaseModel):
    latitude: float = Field(ge=-90, le=90, allow_inf_nan=False)
    longitude: float = Field(ge=-180, le=180, allow_inf_nan=False)


class ComputeRouteRequest(BaseModel):
    origin: Coordinate
    destination: Coordinate
    travel_mode: Literal["DRIVE", "TWO_WHEELER"] = "DRIVE"
    compute_alternatives: bool = True


class NavigationRouteStep(BaseModel):
    instruction: str
    distance_meters: int = Field(ge=0)
    duration_seconds: int = Field(ge=0)
    maneuver: str
    start_location: Coordinate
    end_location: Coordinate
    polyline: str = ""


class NavigationRouteResponse(BaseModel):
    route_label: str = "DEFAULT_ROUTE"
    polyline: str = Field(min_length=1)
    distance_meters: int = Field(ge=0)
    duration_seconds: int = Field(ge=0)
    steps: list[NavigationRouteStep]
    warnings: list[str] = Field(default_factory=list)


class NavigationRoutesResponse(BaseModel):
    routes: list[NavigationRouteResponse] = Field(min_length=1, max_length=4)
