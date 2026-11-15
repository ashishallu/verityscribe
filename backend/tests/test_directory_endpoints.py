import unittest
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

import httpx
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
            return _Response({"hospital_id": "hospital-1", "role": "patient"})
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
        security._clear_profile_role_cache()

    def tearDown(self):
        integration._directory_db.cache_clear()
        security._auth_client.cache_clear()
        security._role_client.cache_clear()
        security._clear_profile_role_cache()

    def test_canonical_profile_role_is_reused_during_concurrent_page_requests(self):
        client = _Client()
        with patch.object(security.time, "monotonic", side_effect=[100.0, 100.0, 101.0]):
            self.assertEqual(security._profile_role(client, "patient-1"), "patient")
            self.assertEqual(security._profile_role(client, "patient-1"), "patient")

        role_queries = [query for query in client.queries if query.table_name == "profiles"]
        self.assertEqual(len(role_queries), 1)
        self.assertEqual(role_queries[0].filters["id"], "patient-1")

    def test_concurrent_requests_share_access_token_validation(self):
        started = threading.Event()
        release = threading.Event()
        user = type("User", (), {"id": "patient-1"})()

        class Auth:
            calls = 0

            def get_user(self, _token):
                self.calls += 1
                started.set()
                release.wait(timeout=2)
                return type("Response", (), {"user": user})()

        client = type("Client", (), {"auth": Auth()})()
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(security._validated_user, client, "test-token")
            self.assertTrue(started.wait(timeout=1))
            second = pool.submit(security._validated_user, client, "test-token")
            time.sleep(0.05)
            release.set()
            self.assertIs(first.result(timeout=1), user)
            self.assertIs(second.result(timeout=1), user)

        self.assertEqual(client.auth.calls, 1)

    def test_auth_stage_retries_a_transient_transport_error_once(self):
        attempts = 0

        def operation():
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise httpx.ConnectTimeout("temporary test failure")
            return "ok"

        self.assertEqual(security._retry_transport_once(operation), "ok")
        self.assertEqual(attempts, 2)

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
