"""OpenAI-shaped errors: {"error": {"message", "type", "code", "param"}}."""

from __future__ import annotations

from typing import Any

_TYPE_BY_STATUS = {
    400: "invalid_request_error",
    401: "authentication_error",
    403: "permission_error",
    404: "invalid_request_error",
    405: "invalid_request_error",
    413: "invalid_request_error",
    415: "invalid_request_error",
    429: "rate_limit_error",
    500: "server_error",
    503: "server_error",
}


class APIError(Exception):
    def __init__(
        self,
        status: int,
        code: str,
        message: str,
        *,
        param: str | None = None,
        retry_after: int | None = None,
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.param = param
        self.retry_after = retry_after

    @property
    def type(self) -> str:
        return _TYPE_BY_STATUS.get(self.status, "server_error")

    def body(self) -> dict[str, Any]:
        return {
            "error": {
                "message": self.message,
                "type": self.type,
                "code": self.code,
                "param": self.param,
            }
        }

    def headers(self) -> dict[str, str]:
        if self.retry_after is not None:
            return {"Retry-After": str(int(self.retry_after))}
        return {}


def internal_error() -> APIError:
    return APIError(500, "internal_error", "The server hit an unexpected error. Try again; if it repeats, report it.")
