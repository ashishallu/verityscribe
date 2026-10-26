import time
from uuid import uuid4
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

class RequestContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        request.state.started_at = time.perf_counter()
        request.state.request_id = str(uuid4())
        response = await call_next(request)
        response.headers['X-Request-Duration-Ms'] = str(round((time.perf_counter() - request.state.started_at) * 1000, 2))
        response.headers['X-Request-ID'] = request.state.request_id
        return response
