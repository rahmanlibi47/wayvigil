"""Rate limiting implementation."""

import time
from functools import wraps
from typing import Callable, Dict, Optional, Tuple

from fastapi import HTTPException, Request, status

from app.core.config import settings


class SimpleRateLimiter:
    """Simple in-memory rate limiter using sliding window.

    For production, consider using Redis-based rate limiting.
    """

    def __init__(self):
        # Store: {key: [(timestamp, count), ...]}
        self._requests: Dict[str, list] = {}
        self._window_size = 60  # 1 minute window

    def is_allowed(self, key: str, limit: int) -> Tuple[bool, int]:
        """Check if request is allowed and return remaining count.

        Args:
            key: Unique identifier (e.g., IP + endpoint)
            limit: Maximum requests per window

        Returns:
            Tuple of (is_allowed, remaining_requests)
        """
        now = time.time()
        window_start = now - self._window_size

        # Get or create request list for this key
        requests = self._requests.get(key, [])

        # Remove old requests outside the window
        requests = [ts for ts in requests if ts > window_start]

        # Check if under limit
        if len(requests) < limit:
            requests.append(now)
            self._requests[key] = requests
            return True, limit - len(requests)

        self._requests[key] = requests
        return False, 0

    def cleanup_old_entries(self):
        """Remove old entries to prevent memory growth."""
        now = time.time()
        window_start = now - self._window_size
        keys_to_remove = []

        for key, requests in self._requests.items():
            requests = [ts for ts in requests if ts > window_start]
            if requests:
                self._requests[key] = requests
            else:
                keys_to_remove.append(key)

        for key in keys_to_remove:
            del self._requests[key]


# Global rate limiter instance
_rate_limiter = SimpleRateLimiter()


def get_rate_limiter() -> SimpleRateLimiter:
    """Get the global rate limiter instance."""
    return _rate_limiter


def rate_limit(requests_per_minute: Optional[int] = None):
    """Decorator to apply rate limiting to an endpoint.

    Args:
        requests_per_minute: Maximum requests allowed per minute.
                           Defaults to settings.RATE_LIMIT_PER_MINUTE.
    """
    limit = requests_per_minute or settings.RATE_LIMIT_PER_MINUTE

    def decorator(func: Callable) -> Callable:
        @wraps(func)
        async def wrapper(*args, **kwargs):
            # Try to find the Request object in args or kwargs
            request: Optional[Request] = None
            for arg in args:
                if isinstance(arg, Request):
                    request = arg
                    break
            if not request:
                request = kwargs.get("request")

            if request:
                # Create a key based on client IP and endpoint
                client_ip = request.client.host if request.client else "unknown"
                endpoint = request.url.path
                key = f"{client_ip}:{endpoint}"

                is_allowed, remaining = _rate_limiter.is_allowed(key, limit)

                if not is_allowed:
                    raise HTTPException(
                        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                        detail="Rate limit exceeded. Please try again later.",
                        headers={"Retry-After": "60"},
                    )

            return await func(*args, **kwargs)

        return wrapper

    return decorator


class RateLimitMiddleware:
    """ASGI middleware for global rate limiting."""

    def __init__(self, app, requests_per_minute: int = 60):
        self.app = app
        self.limit = requests_per_minute
        self._limiter = SimpleRateLimiter()

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        # Get client IP
        client_ip = "unknown"
        headers = dict(scope.get("headers", []))
        if b"x-forwarded-for" in headers:
            client_ip = headers[b"x-forwarded-for"].decode().split(",")[0].strip()
        elif scope.get("client"):
            client_ip = scope["client"][0]

        path = scope.get("path", "/")
        key = f"{client_ip}:{path}"

        is_allowed, _ = self._limiter.is_allowed(key, self.limit)

        if not is_allowed:
            await send({
                "type": "http.response.start",
                "status": 429,
                "headers": [(b"content-type", b"application/json")],
            })
            await send({
                "type": "http.response.body",
                "body": b'{"detail": "Rate limit exceeded"}',
            })
            return

        await self.app(scope, receive, send)
