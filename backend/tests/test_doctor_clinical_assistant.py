import unittest
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

from fastapi import HTTPException

from app.routers import integration
from app.services.ai_service import AIProvider


class _Result:
    def __init__(self, data):
        self.data = data


class _Query:
    def __init__(self, client, table):
        self.client = client
        self.table_name = table
        self.filters = {}
        self.single = False

    def select(self, *_args, **_kwargs):
        return self

    def eq(self, key, value):
        self.filters[key] = value
        return self

    def limit(self, _count):
        return self

    def maybe_single(self):
        self.single = True
        return self

    def execute(self):
        if self.table_name == "appointments":
            rows = [row for row in self.client.appointments
                    if all(row.get(key) == value for key, value in self.filters.items())]
        elif self.table_name == "profiles":
            rows = [self.client.profiles[self.filters["id"]]] if self.filters.get("id") in self.client.profiles else []
        else:
            rows = []
        return _Result((rows[0] if rows else None) if self.single else rows)


class _Client:
    def __init__(self):
        self.appointments = [{
            "id": "appointment-owned",
            "doctor_id": "doctor-a",
            "patient_id": "patient-a",
            "appointment_date": "2026-11-22",
            "appointment_time": "10:00",
            "consultation_type": "in_person",
            "status": "scheduled",
            "reason_for_visit": "Headache follow-up",
        }]
        self.profiles = {"patient-a": {"first_name": "Test", "last_name": "Patient"}}

    def table(self, name):
        return _Query(self, name)


class DoctorClinicalAssistantTests(unittest.TestCase):
    def setUp(self):
        self.client = _Client()
        self.chart = {
            "patient": {"height_cm": 170},
            "reports": [],
            "records": {
                "allergies": [{"allergy_name": "Penicillin", "severity": "reported"}],
                "chronic_conditions": [],
                "vitals": [],
                "consultations": [],
                "prescriptions": [],
            },
            "uploaded_report_text": [],
        }

    def _patches(self, provider):
        return (
            patch.object(integration, "db", return_value=self.client),
            patch.object(integration, "_chat_context", return_value=self.chart),
            patch.object(integration, "_patient_table_rows", return_value=[]),
            patch.object(integration.ai_provider, "answer_doctor_assistant", side_effect=provider),
        )

    def test_returns_provider_answer_with_assigned_patient_context(self):
        captured = {}

        def provider(prompt):
            captured["prompt"] = prompt
            return "The assigned chart records a reported penicillin allergy; verify reaction details."

        with self._patches(provider)[0], self._patches(provider)[1], \
             self._patches(provider)[2], self._patches(provider)[3]:
            result = integration.doctor_clinical_assistant(
                "appointment-owned",
                integration.DoctorAssistantRequest(
                    message="Any allergies?",
                    history=[integration.DoctorAssistantTurn(role="user", content="Summarise the chart")],
                ),
                {"id": "doctor-a"},
            )
        self.assertIn("Penicillin", captured["prompt"])
        self.assertIn("Summarise the chart", captured["prompt"])
        self.assertIn("PHYSICIAN QUESTION: Any allergies?", captured["prompt"])
        self.assertEqual(result["data"]["answer"], "The assigned chart records a reported penicillin allergy; verify reaction details.")
        self.assertTrue(result["data"]["requires_clinician_review"])

    def test_other_doctor_cannot_access_appointment(self):
        with patch.object(integration, "db", return_value=self.client), \
             patch.object(integration.ai_provider, "answer", side_effect=AssertionError("must not call provider")):
            with self.assertRaises(HTTPException) as caught:
                integration.doctor_clinical_assistant(
                    "appointment-owned",
                    integration.DoctorAssistantRequest(message="Any allergies?"),
                    {"id": "doctor-b"},
                )
        self.assertEqual(caught.exception.status_code, 404)

    def test_provider_failure_is_reported_as_temporary_unavailable(self):
        patches = self._patches(RuntimeError("provider unavailable"))
        with patches[0], patches[1], patches[2], patches[3]:
            with self.assertRaises(HTTPException) as caught:
                integration.doctor_clinical_assistant(
                    "appointment-owned",
                    integration.DoctorAssistantRequest(message="Summarise history"),
                    {"id": "doctor-a"},
                )
        self.assertEqual(caught.exception.status_code, 503)
        self.assertEqual(caught.exception.detail, "Clinical assistant is temporarily unavailable")

    def test_chart_context_is_bounded_before_provider_call(self):
        keys = (
            "id", "created_at", "updated_at", "date", "report_date", "consultation_date",
            "name", "condition_name", "allergy_name", "medication_name", "medicine_name",
            "drug_name", "dosage", "frequency", "duration", "symptoms", "diagnosis",
            "treatment_plan", "notes", "status", "severity", "reaction", "value", "unit",
            "systolic", "diastolic", "heart_rate", "temperature", "blood_glucose",
            "weight", "height", "report_type", "findings", "recommendations",
            "blood_pressure", "title", "description",
        )
        self.chart["records"]["vitals"] = [
            {key: "clinical context " * 15 for key in keys} for _ in range(8)
        ]
        captured = {}

        def provider(prompt):
            captured["prompt"] = prompt
            return "No recent vitals are documented."

        patches = self._patches(provider)
        with patches[0], patches[1], patches[2], patches[3]:
            integration.doctor_clinical_assistant(
                "appointment-owned",
                integration.DoctorAssistantRequest(message="Summarise vitals"),
                {"id": "doctor-a"},
            )
        self.assertIn("chart context truncated", captured["prompt"])
        self.assertLess(len(captured["prompt"]), 18000)

    def test_doctor_assistant_uses_configured_gemini_model(self):
        class FakeModels:
            calls = []

            def generate_content(self, **kwargs):
                self.calls.append(kwargs)
                return SimpleNamespace(text="Gemini grounded response")

        fake_client = SimpleNamespace(models=FakeModels())
        fake_genai = ModuleType("google.genai")
        fake_genai.Client = lambda **_kwargs: fake_client
        fake_types = ModuleType("google.genai.types")
        fake_types.HttpOptions = lambda **kwargs: kwargs
        fake_types.HttpRetryOptions = lambda **kwargs: kwargs
        fake_genai.types = fake_types
        fake_google = ModuleType("google")
        fake_google.genai = fake_genai
        modules = {
            "google": fake_google,
            "google.genai": fake_genai,
            "google.genai.types": fake_types,
        }
        provider = AIProvider()
        with patch(
            "app.services.ai_service.settings",
            return_value=SimpleNamespace(
                google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"
            ),
        ), patch.dict("sys.modules", modules):
            answer = provider.answer_doctor_assistant("synthetic prompt")
        self.assertEqual(answer, "Gemini grounded response")
        self.assertEqual(fake_client.models.calls[0]["model"], "gemini-3.8-flash")
        self.assertEqual(fake_client.models.calls[0]["contents"], "synthetic prompt")

    def test_doctor_assistant_retries_gemini_503_without_cloudflare_fallback(self):
        class TransientGeminiError(Exception):
            code = 503
            response = SimpleNamespace(headers={"Retry-After": "0"})

        class FakeModels:
            calls = 0

            def generate_content(self, **_kwargs):
                self.calls += 1
                if self.calls == 1:
                    raise TransientGeminiError("temporary overload")
                return SimpleNamespace(text="Gemini response after retry")

        fake_client = SimpleNamespace(models=FakeModels())
        fake_genai = ModuleType("google.genai")
        fake_genai.Client = lambda **_kwargs: fake_client
        fake_types = ModuleType("google.genai.types")
        fake_types.HttpOptions = lambda **kwargs: kwargs
        fake_types.HttpRetryOptions = lambda **kwargs: kwargs
        fake_genai.types = fake_types
        fake_google = ModuleType("google")
        fake_google.genai = fake_genai
        modules = {
            "google": fake_google,
            "google.genai": fake_genai,
            "google.genai.types": fake_types,
        }
        provider = AIProvider()
        with patch(
            "app.services.ai_service.settings",
            return_value=SimpleNamespace(
                google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"
            ),
        ), patch.dict("sys.modules", modules), patch("app.services.ai_service.time.sleep"):
            answer = provider.answer_doctor_assistant("synthetic prompt")
        self.assertEqual(answer, "Gemini response after retry")
        self.assertEqual(fake_client.models.calls, 2)

    def test_doctor_assistant_uses_gemini_lite_only_after_primary_overload(self):
        class TransientGeminiError(Exception):
            code = 503
            response = SimpleNamespace(headers={})

        class FakeModels:
            calls = []

            def generate_content(self, **kwargs):
                self.calls.append(kwargs)
                if kwargs["model"] == "gemini-3.8-flash":
                    raise TransientGeminiError("primary temporarily overloaded")
                return SimpleNamespace(text="Grounded Gemini fallback answer")

        fake_client = SimpleNamespace(models=FakeModels())
        fake_genai = ModuleType("google.genai")
        fake_genai.Client = lambda **_kwargs: fake_client
        fake_types = ModuleType("google.genai.types")
        fake_types.HttpOptions = lambda **kwargs: kwargs
        fake_types.HttpRetryOptions = lambda **kwargs: kwargs
        fake_genai.types = fake_types
        fake_google = ModuleType("google")
        fake_google.genai = fake_genai
        modules = {
            "google": fake_google,
            "google.genai": fake_genai,
            "google.genai.types": fake_types,
        }
        provider = AIProvider()
        with patch(
            "app.services.ai_service.settings",
            return_value=SimpleNamespace(
                google_api_key="test-key", gemini_chat_model="gemini-3.8-flash"
            ),
        ), patch.dict("sys.modules", modules), patch("app.services.ai_service.time.sleep"):
            answer = provider.answer_doctor_assistant("synthetic prompt")

        self.assertEqual(answer, "Grounded Gemini fallback answer")
        self.assertEqual([call["model"] for call in fake_client.models.calls], [
            "gemini-3.8-flash",
            "gemini-3.8-flash",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
        ])


if __name__ == "__main__":
    unittest.main()
