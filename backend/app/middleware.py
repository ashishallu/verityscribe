import time
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        request.state.started_at = time.perf_counter()
        response = await call_next(request)
        response.headers['X-Request-Duration-Ms'] = str(round((time.perf_counter() - request.state.started_at) * 1000, 2))
        return response
