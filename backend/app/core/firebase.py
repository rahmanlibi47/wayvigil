"""Firebase ID token verification."""

from threading import Lock
from typing import Any, Dict

import firebase_admin
from firebase_admin import auth, credentials

from app.core.config import settings

_app_lock = Lock()


class InvalidFirebaseToken(Exception):
    """The supplied Firebase ID token is invalid or expired."""


class FirebaseAuthUnavailable(Exception):
    """Firebase verification could not be performed due to server configuration."""


def verify_firebase_token(token: str) -> Dict[str, Any]:
    """Verify a Firebase ID token and return its trusted claims."""
    try:
        with _app_lock:
            try:
                app = firebase_admin.get_app()
            except ValueError:
                credential = (
                    credentials.Certificate(settings.FIREBASE_CREDENTIALS_PATH)
                    if settings.FIREBASE_CREDENTIALS_PATH
                    else credentials.ApplicationDefault()
                )
                options = (
                    {"projectId": settings.FIREBASE_PROJECT_ID}
                    if settings.FIREBASE_PROJECT_ID
                    else None
                )
                app = firebase_admin.initialize_app(credential, options=options)

        return auth.verify_id_token(token, app=app, check_revoked=True)
    except (auth.InvalidIdTokenError, auth.ExpiredIdTokenError, auth.RevokedIdTokenError, auth.UserDisabledError) as exc:
        raise InvalidFirebaseToken from exc
    except Exception as exc:
        raise FirebaseAuthUnavailable from exc
