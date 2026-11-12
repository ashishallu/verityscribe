import unittest
from unittest.mock import patch

from fastapi import HTTPException

from app.core import security
from app.routers import integration


class _Response:
    def __init__(self, data=None, count=0):
        self.data = data or []
        self.count = count


class _Query:
    def __init__(self, table):
        self.table_name = table
        self.filters = {}
        self.range_value = None

    def select(self, *_args, **_kwargs): return self
    def eq(self, name, value):
        self.filters[name] = value
        return self
    def ilike(self, name, value):
        self.filters[name] = value
        return self
    def is_(self, name, value):
        self.filters[name] = value
        return self
    def order(self, *_args, **_kwargs): return self
    def range(self, start, end):
        self.range_value = (start, end)
        return self
    def maybe_single(self): return self
    def execute(self):
        if self.table_name == "hospitals":
            return _Response([{"id": "hospital-1", "name": "Existing hospital"}], 1)
        if self.table_name == "departments":
            return _Response([{"id": "department-1", "name": "Medicine"}], 1)
        if self.table_name == "profiles":
            return _Response({"hospital_id": "hospital-1"})
        if self.table_name == "doctors":
            return _Response([{
                "id": "doctor-1", "profiles": {"first_name": "A"},
                "hospitals": {"id": "hospital-1"}, "departments": {"id": "department-1"},
            }], 1)
        return _Response()


class _Client:
    def __init__(self):
        self.queries = []

    def table(self, name):
        query = _Query(name)
        self.queries.append(query)
        return query


class DirectoryEndpointTests(unittest.TestCase):
    def setUp(self):
        integration._directory_db.cache_clear()
        security._auth_client.cache_clear()
        security._role_client.cache_clear()

    def tearDown(self):
        integration._directory_db.cache_clear()
        security._auth_client.cache_clear()
        security._role_client.cache_clear()

    def test_authentication_clients_use_bounded_transport_and_postgrest_timeouts(self):
        options = security._client_options()
        self.assertEqual(options.postgrest_client_timeout, 8)
        self.assertEqual(options.httpx_client.timeout.connect, 4)
        self.assertEqual(options.httpx_client.timeout.read, 8)

    def test_directory_client_uses_bounded_postgrest_timeout_and_is_reused(self):
        captured = []
        settings = type("Settings", (), {
            "supabase_url": "https://example.supabase.co",
            "supabase_service_role_key": "not-logged-test-value",
        })()
        with patch.object(integration, "settings", return_value=settings), patch.object(
            integration, "create_client", side_effect=lambda *args, **kwargs: captured.append((args, kwargs)) or object()
        ):
            one = integration.directory_db()
            two = integration.directory_db()

        self.assertIs(one, two)
        self.assertEqual(len(captured), 1)
        self.assertEqual(captured[0][1]["options"].postgrest_client_timeout, 8)

    def test_hospital_department_and_doctor_queries_keep_existing_shapes(self):
        client = _Client()
        with patch.object(integration, "directory_db", return_value=client):
            hospital_result = integration.hospitals(page=1, page_size=25, search=None, _={})
            department_result = integration.departments(
                page=1, page_size=25, search=None, hospital_id="hospital-1", _={}
            )
            doctor_result = integration.doctor_directory(
                page=1, page_size=25, hospital_id="hospital-1", department_id="department-1",
                claims={"sub": "patient-1", "app_metadata": {"role": "patient"}},
            )

        self.assertEqual(hospital_result["data"][0]["name"], "Existing hospital")
        self.assertEqual(department_result["data"][0]["name"], "Medicine")
        self.assertEqual(doctor_result["data"][0]["first_name"], "A")
        self.assertEqual(doctor_result["data"][0]["hospital"]["id"], "hospital-1")
        doctor_query = next(query for query in client.queries if query.table_name == "doctors")
        self.assertEqual(doctor_query.filters["hospital_id"], "hospital-1")
        self.assertEqual(doctor_query.filters["department_id"], "department-1")

    def test_hospital_department_route_keeps_lookup_and_list_response_shapes(self):
        client = _Client()
        with patch.object(integration, "directory_db", return_value=client):
            result = integration.hospital_departments(
                "hospital-1", page=1, page_size=25, search=None, _={}
            )

        self.assertEqual(result["data"][0]["id"], "department-1")
        self.assertEqual(result["meta"], {"page": 1, "page_size": 25, "total": 1})
        self.assertEqual([query.table_name for query in client.queries], ["hospitals", "departments"])

    def test_hospital_admin_doctor_list_remains_scoped_to_profile_hospital(self):
        client = _Client()
        with patch.object(integration, "directory_db", return_value=client):
            result = integration.doctor_directory(
                page=1, page_size=25, hospital_id="untrusted-request-hospital", department_id=None,
                claims={"sub": "admin-1", "app_metadata": {"role": "hospital_admin"}},
            )

        doctor_query = next(query for query in client.queries if query.table_name == "doctors")
        profile_query = next(query for query in client.queries if query.table_name == "profiles")
        self.assertEqual(profile_query.filters["id"], "admin-1")
        self.assertEqual(doctor_query.filters["hospital_id"], "hospital-1")
        self.assertEqual(result["data"][0]["hospital"]["id"], "hospital-1")

    def test_directory_query_failure_is_bounded_and_returns_safe_503(self):
        with self.assertLogs(integration.logger, level="WARNING") as captured:
            with self.assertRaises(HTTPException) as raised:
                integration._timed_directory_query(
                    "hospitals", "postgrest_select", lambda: (_ for _ in ()).throw(TimeoutError("private detail"))
                )

        self.assertEqual(raised.exception.status_code, 503)
        self.assertEqual(raised.exception.detail, "Directory data is temporarily unavailable")
        self.assertIn("error_type=TimeoutError", captured.output[0])
        self.assertNotIn("private detail", captured.output[0])


if __name__ == "__main__":
    unittest.main()
