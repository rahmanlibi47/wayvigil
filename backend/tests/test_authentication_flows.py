"""Authentication flow and security acceptance tests."""

from datetime import timedelta
from uuid import uuid4

import pytest
from fastapi import status
from httpx import AsyncClient
from jose import jwt

from app.api.v1.endpoints import auth as auth_endpoint
from app.core.config import settings
from app.core.firebase import FirebaseAuthUnavailable, InvalidFirebaseToken
from app.core.security import create_access_token, decode_token
from app.models.user import User
from app.schemas.user import UserCreate
from app.services.user_service import UserService


def _firebase_claims(**overrides):
    claims = {
        "uid": "firebase-user-1",
        "email": "driver@example.com",
        "email_verified": True,
        "name": "Test Driver",
        "picture": "https://example.test/avatar.png",
    }
    claims.update(overrides)
    return claims


@pytest.mark.asyncio
async def test_anonymous_login_returns_bearer_token_and_active_user(
    client: AsyncClient,
):
    response = await client.post(
        "/api/v1/auth/anonymous", json={"device_id": "auth-device-1"}
    )

    assert response.status_code == status.HTTP_200_OK
    body = response.json()
    assert body["token_type"] == "bearer"
    assert body["expires_in"] > 0
    assert body["user"]["is_anonymous"] is True
    assert body["user"]["is_active"] is True

    claims = decode_token(body["access_token"])
    assert claims is not None
    assert claims["sub"] == body["user"]["id"]
    assert claims["type"] == "anonymous"
    assert "exp" in claims

    profile_response = await client.get(
        "/api/v1/users/me",
        headers={"Authorization": f"Bearer {body['access_token']}"},
    )
    assert profile_response.status_code == status.HTTP_200_OK
    assert profile_response.json()["id"] == body["user"]["id"]


@pytest.mark.asyncio
async def test_anonymous_login_requires_device_id(client: AsyncClient):
    response = await client.post("/api/v1/auth/anonymous", json={})

    assert response.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


@pytest.mark.asyncio
async def test_firebase_login_requires_token(client: AsyncClient):
    response = await client.post("/api/v1/auth/firebase", json={})

    assert response.status_code == status.HTTP_422_UNPROCESSABLE_ENTITY


@pytest.mark.asyncio
@pytest.mark.parametrize("authorization", [None, "Bearer invalid-token"])
async def test_protected_profile_rejects_missing_or_malformed_token(
    client: AsyncClient, authorization: str | None
):
    headers = {"Authorization": authorization} if authorization else {}
    response = await client.get("/api/v1/users/me", headers=headers)

    assert response.status_code == status.HTTP_401_UNAUTHORIZED
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.asyncio
async def test_protected_profile_rejects_expired_token(client: AsyncClient):
    token = create_access_token(
        {"sub": str(uuid4())}, expires_delta=timedelta(seconds=-1)
    )

    response = await client.get(
        "/api/v1/users/me", headers={"Authorization": f"Bearer {token}"}
    )

    assert response.status_code == status.HTTP_401_UNAUTHORIZED


@pytest.mark.asyncio
async def test_protected_profile_rejects_token_signed_with_another_key(
    client: AsyncClient,
):
    token = jwt.encode(
        {"sub": str(uuid4())}, "a-different-secret-key", algorithm=settings.ALGORITHM
    )

    response = await client.get(
        "/api/v1/users/me", headers={"Authorization": f"Bearer {token}"}
    )

    assert response.status_code == status.HTTP_401_UNAUTHORIZED


@pytest.mark.asyncio
async def test_protected_profile_rejects_token_for_unknown_user(
    client: AsyncClient,
):
    token = create_access_token({"sub": str(uuid4())})

    response = await client.get(
        "/api/v1/users/me", headers={"Authorization": f"Bearer {token}"}
    )

    assert response.status_code == status.HTTP_401_UNAUTHORIZED


@pytest.mark.asyncio
async def test_protected_profile_rejects_non_uuid_subject(client: AsyncClient):
    token = create_access_token({"sub": "not-a-user-uuid"})

    response = await client.get(
        "/api/v1/users/me", headers={"Authorization": f"Bearer {token}"}
    )

    assert response.status_code == status.HTTP_401_UNAUTHORIZED


@pytest.mark.asyncio
async def test_firebase_login_creates_user_and_returns_firebase_token(
    client: AsyncClient, monkeypatch
):
    claims = _firebase_claims()
    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", lambda _: claims)

    response = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "valid-firebase-token"}
    )

    assert response.status_code == status.HTTP_200_OK
    body = response.json()
    assert body["user"]["firebase_uid"] == claims["uid"]
    assert body["user"]["email"] == claims["email"]
    assert body["user"]["display_name"] == claims["name"]
    assert body["user"]["photo_url"] == claims["picture"]
    assert body["user"]["is_anonymous"] is False
    token_claims = decode_token(body["access_token"])
    assert token_claims is not None
    assert token_claims["sub"] == body["user"]["id"]
    assert token_claims["type"] == "firebase"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("verification_error", "expected_status"),
    [
        (InvalidFirebaseToken, status.HTTP_401_UNAUTHORIZED),
        (FirebaseAuthUnavailable, status.HTTP_503_SERVICE_UNAVAILABLE),
    ],
)
async def test_firebase_login_maps_verification_errors_to_api_status(
    client: AsyncClient, monkeypatch, verification_error, expected_status: int
):
    def reject_token(_token):
        raise verification_error

    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", reject_token)

    response = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "unusable-token"}
    )

    assert response.status_code == expected_status


@pytest.mark.asyncio
async def test_firebase_login_reuses_user_and_refreshes_profile(
    client: AsyncClient, monkeypatch
):
    claims = _firebase_claims()
    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", lambda _: claims)
    first = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "first-token"}
    )
    assert first.status_code == status.HTTP_200_OK

    claims = _firebase_claims(name="Updated Driver", picture=None)
    second = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "second-token"}
    )

    assert second.status_code == status.HTTP_200_OK
    assert second.json()["user"]["id"] == first.json()["user"]["id"]
    assert second.json()["user"]["display_name"] == "Updated Driver"


@pytest.mark.asyncio
async def test_firebase_login_links_verified_email_to_existing_user(
    client: AsyncClient, db_session, monkeypatch
):
    existing_user = await UserService(db_session).create(
        UserCreate(email="driver@example.com", display_name="Existing Driver")
    )
    claims = _firebase_claims(uid="linked-firebase-uid")
    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", lambda _: claims)

    response = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "verified-token"}
    )

    assert response.status_code == status.HTTP_200_OK
    assert response.json()["user"]["id"] == str(existing_user.id)
    assert response.json()["user"]["firebase_uid"] == claims["uid"]


@pytest.mark.asyncio
async def test_firebase_login_rejects_unverified_email_collision(
    client: AsyncClient, db_session, monkeypatch
):
    await UserService(db_session).create(
        UserCreate(email="driver@example.com", display_name="Existing Driver")
    )
    claims = _firebase_claims(uid="unlinked-firebase-uid", email_verified=False)
    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", lambda _: claims)

    response = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "unverified-token"}
    )

    assert response.status_code == status.HTTP_409_CONFLICT
    assert response.json()["detail"] == "Email is already linked to another account"


@pytest.mark.asyncio
async def test_firebase_login_rejects_deactivated_user(
    client: AsyncClient, db_session, monkeypatch
):
    db_session.add(User(firebase_uid="disabled-firebase-uid", is_active=False))
    await db_session.commit()
    claims = _firebase_claims(uid="disabled-firebase-uid")
    monkeypatch.setattr(auth_endpoint, "verify_firebase_token", lambda _: claims)

    response = await client.post(
        "/api/v1/auth/firebase", json={"firebase_token": "disabled-user-token"}
    )

    assert response.status_code == status.HTTP_403_FORBIDDEN
