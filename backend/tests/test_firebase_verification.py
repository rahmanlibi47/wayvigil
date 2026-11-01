"""Unit tests for Firebase token verification boundaries."""

import pytest

from app.core import firebase


def test_firebase_verifier_returns_verified_claims_and_checks_revocation(
    monkeypatch,
):
    app = object()
    expected_claims = {"uid": "firebase-user-1", "email_verified": True}
    observed = {}
    monkeypatch.setattr(firebase.firebase_admin, "get_app", lambda: app)

    def verify(token, *, app, check_revoked):
        observed.update(token=token, app=app, check_revoked=check_revoked)
        return expected_claims

    monkeypatch.setattr(firebase.auth, "verify_id_token", verify)

    claims = firebase.verify_firebase_token("signed-token")

    assert claims == expected_claims
    assert observed == {
        "token": "signed-token",
        "app": app,
        "check_revoked": True,
    }


@pytest.mark.parametrize(
    "firebase_error",
    [
        firebase.auth.InvalidIdTokenError,
        firebase.auth.ExpiredIdTokenError,
        firebase.auth.RevokedIdTokenError,
        firebase.auth.UserDisabledError,
    ],
)
def test_firebase_verifier_maps_untrusted_identity_errors(
    monkeypatch, firebase_error
):
    monkeypatch.setattr(firebase.firebase_admin, "get_app", lambda: object())

    def reject_token(*_args, **_kwargs):
        if firebase_error is firebase.auth.ExpiredIdTokenError:
            raise firebase_error("rejected", ValueError("expired"))
        raise firebase_error("rejected")

    monkeypatch.setattr(firebase.auth, "verify_id_token", reject_token)

    with pytest.raises(firebase.InvalidFirebaseToken):
        firebase.verify_firebase_token("rejected-token")


def test_firebase_verifier_maps_provider_errors_to_unavailable(monkeypatch):
    monkeypatch.setattr(firebase.firebase_admin, "get_app", lambda: object())

    def fail_to_verify(*_args, **_kwargs):
        raise RuntimeError("provider unavailable")

    monkeypatch.setattr(firebase.auth, "verify_id_token", fail_to_verify)

    with pytest.raises(firebase.FirebaseAuthUnavailable):
        firebase.verify_firebase_token("token")
