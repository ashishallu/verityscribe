# VerityScribe FastAPI

Run locally with `uvicorn app.main:app --reload` after copying `.env.example` to `.env`.

All endpoints are under `/api/v1`; OpenAPI is at `/api/v1/openapi.json`. Requests require a Supabase access token and execute through RLS using the publishable key. The server-only seed utility (`app.seed`) accepts real Auth user IDs and creates only matching `profiles` and patient/doctor records.
