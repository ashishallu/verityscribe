import unittest
from unittest.mock import patch

from fastapi import HTTPException

from app.routers.integration import (
    ChatRequest,
    _document_evidence,
    _owned_chat_session,
    _session_documents,
    remove_chat_session_document,
    secure_chat,
)


class _Response:
    def __init__(self, data):
        self.data = data


class _Query:
    def __init__(self, owner, table):
        self.owner, self.table = owner, table
        self.filters = {}
        self.ids = None
        self.descending = False
        self.update_values = None

    def select(self, *_args, **_kwargs): return self
    def insert(self, row):
        self.owner.messages.append(row)
        return self
    def update(self, values):
        self.update_values = values
        return self
    def eq(self, key, value):
        self.filters[key] = value
        return self
    def in_(self, key, values):
        self.ids = set(values)
        self.filters[key] = list(values)
        return self
    def order(self, _key, desc=False):
        self.descending = desc
        return self
    def limit(self, _count): return self
    def execute(self):
        self.owner.queries.append(self)
        if self.table == "ai_chat_sessions":
            rows = self.owner.sessions
        elif self.table == "medical_documents":
            rows = [row for row in self.owner.documents
                    if all(row.get(key) == value for key, value in self.filters.items())]
            if self.update_values is not None:
                for row in rows:
                    row.update(self.update_values)
        elif self.table == "patient_embeddings":
            rows = [row for row in self.owner.texts
                    if row.get("patient_id") == self.filters.get("patient_id")
                    and row.get("source_document_id") in (self.ids or set())]
        else:
            rows = []
        rows = [row for row in rows if all(row.get(key) == value for key, value in self.filters.items()
                                           if key not in {"source_document_id"})]
        return _Response(rows)


class _Client:
    def __init__(self):
        self.queries = []
        self.sessions = [{"id": "session-a", "patient_id": "patient-a", "ended_at": None}]
        self.documents = [
            {"id": "doc-a", "patient_id": "patient-a", "session_id": "session-a", "document_type": "prescription"},
            {"id": "doc-b", "patient_id": "patient-a", "session_id": "session-b", "document_type": "prescription"},
            {"id": "doc-c", "patient_id": "patient-b", "session_id": "session-a", "document_type": "prescription"},
        ]
        self.texts = [
            {"source_document_id": "doc-a", "patient_id": "patient-a", "content": "[Page 1]\nRx: Drug A 10 mg, take once daily.\nDuration: unclear."},
            {"source_document_id": "doc-b", "patient_id": "patient-a", "content": "secret from another session"},
            {"source_document_id": "doc-c", "patient_id": "patient-b", "content": "another patient's private text"},
        ]
        self.messages = []

    def table(self, name): return _Query(self, name)


class SessionScopedDocumentTests(unittest.TestCase):
    def test_session_lookup_requires_patient_ownership_and_active_state(self):
        client = _Client()
        with self.assertRaises(HTTPException) as denied:
            _owned_chat_session(client, "session-a", "patient-b")
        self.assertEqual(denied.exception.status_code, 404)
        client.sessions[0]["ended_at"] = "2026-10-26T10:00:00Z"
        with self.assertRaises(HTTPException) as ended:
            _owned_chat_session(client, "session-a", "patient-a")
        self.assertEqual(ended.exception.status_code, 410)

    def test_document_retrieval_is_patient_and_session_scoped(self):
        client = _Client()
        documents = _session_documents(client, "patient-a", "session-a")
        self.assertEqual([item["id"] for item in documents], ["doc-a"])
        self.assertIn("Drug A 10 mg", documents[0]["extracted_text"])
        document_query = next(query for query in client.queries if query.table == "medical_documents")
        self.assertEqual(document_query.filters["patient_id"], "patient-a")
        self.assertEqual(document_query.filters["session_id"], "session-a")
        text_query = next(query for query in client.queries if query.table == "patient_embeddings")
        self.assertEqual(text_query.filters["patient_id"], "patient-a")
        self.assertEqual(text_query.ids, {"doc-a"})
        self.assertNotIn("secret", str(documents))
        self.assertNotIn("another patient's", str(documents))

    def test_evidence_selection_keeps_literal_ocr_and_bounds_source_text(self):
        evidence = _document_evidence("What dose is listed?", [{
            "id": "doc-a", "document_type": "prescription",
            "extracted_text": "[Page 1]\nClinic header\nDrug A 10 mg once daily\n" + ("unrelated line\n" * 500),
        }])
        self.assertEqual(evidence[0]["document_id"], "doc-a")
        self.assertIn("Drug A 10 mg", evidence[0]["source_text"])
        self.assertLessEqual(len(evidence[0]["source_text"]), 1800)

    def test_document_questions_send_only_attached_evidence_to_model(self):
        client = _Client()
        captured = {}

        def answer(prompt):
            captured["prompt"] = prompt
            return "The extracted text lists Drug A 10 mg once daily; verify this with your pharmacist."

        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration.get_current_patient", return_value={"id": "patient-a"}), \
             patch("app.routers.integration.ai_provider.answer", side_effect=answer):
            result = secure_chat(
                ChatRequest(message="What medicine does my uploaded prescription list?", session_id="session-a"),
                {"sub": "patient-a", "app_metadata": {"role": "patient"}},
            )
        self.assertIn("Drug A 10 mg", captured["prompt"])
        self.assertIn("not a clinician-approved", captured["prompt"])
        self.assertNotIn("doc-b", captured["prompt"])
        self.assertNotIn("another patient's private text", captured["prompt"])
        self.assertEqual(result["data"]["answer"].startswith("The extracted text"), True)
        self.assertEqual([row["sender_type"] for row in client.messages], ["user", "assistant"])

    def test_general_health_question_does_not_receive_attached_document_text(self):
        client = _Client()
        captured = {}
        def answer(prompt):
            captured["prompt"] = prompt
            return "A balanced diet includes varied whole foods."
        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration.get_current_patient", return_value={"id": "patient-a"}), \
             patch("app.routers.integration.ai_provider.answer", side_effect=answer):
            secure_chat(
                ChatRequest(message="What is a balanced diet?", session_id="session-a"),
                {"sub": "patient-a", "app_metadata": {"role": "patient"}},
            )
        self.assertIn("MODE: GENERAL", captured["prompt"])
        self.assertNotIn("Drug A 10 mg", captured["prompt"])
        self.assertNotIn("DOCUMENT EVIDENCE", captured["prompt"])

    def test_remove_detaches_only_this_session_without_deleting_source_text(self):
        client = _Client()
        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration.get_current_patient", return_value={"id": "patient-a"}):
            result = remove_chat_session_document("session-a", "doc-a", {
                "sub": "patient-a", "app_metadata": {"role": "patient"},
            })
        self.assertFalse(result["data"]["attached"])
        document = next(row for row in client.documents if row["id"] == "doc-a")
        self.assertIsNone(document["session_id"])
        self.assertTrue(any(row["source_document_id"] == "doc-a" for row in client.texts))

    def test_remove_rejects_a_document_from_another_session(self):
        client = _Client()
        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration.get_current_patient", return_value={"id": "patient-a"}):
            with self.assertRaises(HTTPException) as denied:
                remove_chat_session_document("session-a", "doc-b", {
                    "sub": "patient-a", "app_metadata": {"role": "patient"},
                })
        self.assertEqual(denied.exception.status_code, 404)
        self.assertEqual(next(row for row in client.documents if row["id"] == "doc-b")["session_id"], "session-b")


if __name__ == "__main__":
    unittest.main()
