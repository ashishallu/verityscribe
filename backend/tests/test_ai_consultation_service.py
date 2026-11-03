import unittest
import uuid
from unittest.mock import patch
from datetime import datetime, timezone

from fastapi import HTTPException
from pydantic import ValidationError

from app.services import ai_consultation
from app.routers import ai_consultations


class _Result:
    data = []


class _Query:
    def __init__(self, data=None): self.data = data
    def select(self, *_args, **_kwargs): return self
    def eq(self, *_args, **_kwargs): return self
    def limit(self, *_args, **_kwargs): return self
    def order(self, *_args, **_kwargs): return self
    def maybe_single(self): return self
    def execute(self): return _MemoryResult(self.data) if self.data is not None else _Result()


class _Client:
    def __init__(self, case_matches=None): self.case_matches = case_matches or []; self.rpc_calls = []; self.tables = {"profiles": [{"id": "patient-current", "first_name": "Current", "last_name": "Patient"}]}
    def table(self, name): return _Query(self.tables[name][0] if name == "profiles" else None)
    def rpc(self, name, params):
        self.rpc_calls.append((name, params))
        return _RPCQuery(self.case_matches)


class _MemoryResult:
    def __init__(self, data): self.data = data


class _RPCQuery:
    def __init__(self, data): self.data = data
    def execute(self): return _MemoryResult(self.data)


class _MemoryQuery:
    def __init__(self, client, table):
        self.client, self.table_name = client, table
        self.filters = {}
        self.new_rows = None
        self.sort_column = None
        self.single = False

    def select(self, *_args, **_kwargs): return self
    def eq(self, column, value): self.filters[column] = value; return self
    def order(self, column, **_kwargs): self.sort_column = column; return self
    def limit(self, *_args): return self
    def maybe_single(self): self.single = True; return self
    def insert(self, row): self.new_rows = row; return self
    def upsert(self, row, **_kwargs): self.new_rows = row; return self
    def execute(self):
        rows = self.client.tables.setdefault(self.table_name, [])
        if self.new_rows is not None:
            candidates = self.new_rows if isinstance(self.new_rows, list) else [self.new_rows]
            created = []
            for candidate in candidates:
                row = {"id": str(uuid.uuid4()), "created_at": datetime.now(timezone.utc).isoformat(), **candidate}
                if row.get("request_key") and any(
                    existing.get("session_id") == row.get("session_id")
                    and existing.get("request_key") == row.get("request_key")
                    and existing.get("role") == row.get("role")
                    for existing in rows
                ):
                    raise RuntimeError("duplicate request key")
                rows.append(row)
                created.append(row)
            return _MemoryResult(created)
        selected = [row for row in rows if all(row.get(key) == value for key, value in self.filters.items())]
        if self.sort_column:
            selected.sort(key=lambda row: row.get(self.sort_column, ""))
        return _MemoryResult((selected[0] if selected else None) if self.single else list(selected))


class _MemoryClient:
    def __init__(self): self.tables = {"ai_consultation_messages": [], "profiles": []}
    def table(self, name): return _MemoryQuery(self, name)


class AIConsultationServiceTests(unittest.TestCase):
  def test_emergency_terms_are_detected_without_model_call(self):
    self.assertTrue(ai_consultation.emergency_warning("I have sudden chest pain"))
    self.assertTrue(ai_consultation.emergency_warning("I might kill myself"))
    self.assertFalse(ai_consultation.emergency_warning("My ankle aches after running"))

  def test_summary_never_returns_a_prescription_or_unverified_cases(self):
    with patch.object(ai_consultation.ai_provider, "answer", return_value='{"clinical_summary":"Patient reports headache","draft_prescription":{"medicine":"made up"},"similar_cases":{"matches":["made up"]}}'):
      result = ai_consultation.generate_summary(
          _Client(), patient_id="patient-test", hospital_id="hospital-test", mode="ai_chat",
          appointment={"id": "appointment-test", "consultation_type": "ai_chat"}, messages=[],
          similar_case_consent=False,
      )
    self.assertEqual(result["clinical_summary"], "Patient reports headache")
    self.assertIsNone(result["draft_prescription"])
    self.assertEqual(result["similar_cases"], {"status": "not_consented", "matches": []})
    self.assertTrue(result["review_required"])

  def test_similar_case_retrieval_uses_symptom_terms_and_authenticated_scope(self):
    client = _Client(case_matches=[{"symptom_terms": ["headache", "nausea"], "similarity": 0.5}])
    response = '{"clinical_summary":"Headache and nausea","symptoms":[{"name":"Headache"},{"name":"Nausea"}]}'
    with patch.object(ai_consultation.ai_provider, "answer", return_value=response):
      result = ai_consultation.generate_summary(
          client, patient_id="patient-current", hospital_id="hospital-current", mode="ai_chat",
          appointment={"id": "appointment-current", "consultation_type": "ai_chat"},
          messages=[], similar_case_consent=True,
      )
    self.assertEqual(result["similar_cases"]["status"], "matches")
    self.assertEqual(result["similar_cases"]["matches"], client.case_matches)
    self.assertEqual(client.rpc_calls[0][0], "find_similar_ai_consultation_cases")
    self.assertEqual(client.rpc_calls[0][1]["p_search_terms"], "headache nausea")
    self.assertEqual(client.rpc_calls[0][1]["p_hospital_id"], "hospital-current")
    self.assertEqual(client.rpc_calls[0][1]["p_excluded_patient_id"], "patient-current")

  def test_approved_case_storage_is_opt_in_and_contains_symptom_terms_only(self):
    client = _MemoryClient()
    session = {"id": "session-1", "hospital_id": "hospital-1", "patient_id": "patient-1"}
    summary = {"clinical_summary": "private narrative with identifiers", "symptoms": [
      {"name": "Ashish Reddy headache", "duration": "three days"}, {"name": "Nausea"}
    ]}
    ai_consultation.store_approved_case(client, session=session, summary=summary)
    self.assertNotIn("ai_consultation_case_library", client.tables)
    session["similar_case_consent_at"] = "2026-11-04T00:00:00Z"
    client.tables["profiles"].append({"id": "patient-1", "first_name": "Ashish", "last_name": "Reddy"})
    ai_consultation.store_approved_case(client, session=session, summary=summary)
    stored = client.tables["ai_consultation_case_library"][0]
    self.assertEqual(stored["symptom_terms"], ["headache", "nausea"])
    self.assertNotIn("clinical_summary", stored)

  def test_message_retry_reuses_persisted_pair(self):
    client = _MemoryClient()
    session = {"id": "session-1", "patient_id": "patient-1", "hospital_id": "hospital-1", "mode": "ai_chat", "status": "in_progress"}
    with patch.object(ai_consultations, "generate_reply", return_value="follow-up") as generate:
      first = ai_consultations._send_message(client, session, "My head hurts", "turn-1")
      retry = ai_consultations._send_message(client, session, "My head hurts", "turn-1")
    self.assertEqual([row["id"] for row in first["messages"]], [row["id"] for row in retry["messages"]])
    self.assertEqual(len(client.tables["ai_consultation_messages"]), 2)
    generate.assert_called_once()

  def test_retry_after_provider_error_keeps_single_patient_turn(self):
    client = _MemoryClient()
    session = {"id": "session-1", "patient_id": "patient-1", "hospital_id": "hospital-1", "mode": "ai_chat", "status": "in_progress"}
    with patch.object(ai_consultations, "generate_reply", side_effect=[RuntimeError("unavailable"), "follow-up"]):
      with self.assertRaises(HTTPException):
        ai_consultations._send_message(client, session, "My head hurts", "turn-2")
      result = ai_consultations._send_message(client, session, "My head hurts", "turn-2")
    self.assertEqual([row["role"] for row in result["messages"]], ["patient", "assistant"])
    self.assertEqual(len(client.tables["ai_consultation_messages"]), 2)

  def test_review_payload_size_is_bounded(self):
    with self.assertRaises(ValidationError):
      ai_consultations.ReviewRequest(decision="approved", summary={"clinical_summary": "x" * 50000})


if __name__ == "__main__":
    unittest.main()
