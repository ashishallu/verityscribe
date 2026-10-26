import unittest
from unittest.mock import patch

from app.routers.integration import _chat_context, _safe_personal_record_answer, my_reports


class _Response:
    def __init__(self, data):
        self.data = data


class _Query:
    def __init__(self, table, owner):
        self.table_name = table
        self.owner = owner
        self.filters = {}
        self.in_values = None

    def select(self, *_args, **_kwargs): return self
    def eq(self, key, value):
        self.filters[key] = value
        return self
    def order(self, *_args, **_kwargs): return self
    def limit(self, *_args): return self
    def in_(self, _key, values):
        self.in_values = values
        return self
    def maybe_single(self): return self
    def execute(self):
        self.owner.queries.append(self)
        if self.table_name == "reports":
            data = [{"report_type": "blood test", "findings": "Reviewed result"}]
        elif self.table_name == "medical_documents":
            data = [{
                "id": "doc-patient-one", "patient_id": "patient-one",
                "document_type": "prescription", "created_at": "2026-10-01T10:00:00Z",
            }]
        elif self.table_name == "patient_embeddings":
            data = [{
                "source_document_id": "doc-patient-one",
                "content": "Extracted from uploaded document: medicine 5 mg",
            }]
        elif self.table_name == "patients":
            data = {}
        else:
            data = []
        return _Response(data)


class _Client:
    def __init__(self):
        self.queries = []
    def table(self, name):
        return _Query(name, self)


class PatientDocumentIntegrationTests(unittest.TestCase):
    def test_reports_list_merges_private_upload_as_unverified_without_clinical_insert(self):
        client = _Client()
        with patch("app.routers.integration.db", return_value=client):
            result = my_reports({"sub": "patient-one", "app_metadata": {"role": "patient"}})

        rows = result["data"]
        uploaded = next(row for row in rows if row.get("source_type") == "private_chat_upload")
        self.assertEqual(uploaded["review_status"], "pending_clinician_review")
        self.assertIn("not clinician-reviewed", uploaded["recommendations"])
        self.assertIn("medicine 5 mg", uploaded["findings"])
        self.assertTrue(all(
            query.filters.get("patient_id") == "patient-one"
            for query in client.queries
            if query.table_name in {"medical_documents", "patient_embeddings"}
        ))
        self.assertEqual([row["report_type"] for row in rows[:1]], ["Uploaded prescription"])

    def test_chat_context_retrieves_only_authenticated_patient_document_text(self):
        client = _Client()
        context = _chat_context(client, "patient-one")

        embedding_query = next(query for query in client.queries if query.table_name == "patient_embeddings")
        self.assertEqual(embedding_query.filters.get("patient_id"), "patient-one")
        answer = _safe_personal_record_answer("What medicine is in my prescription?", context)
        self.assertIn("medicine 5 mg", answer)
        self.assertIn("not a verified prescription", answer)
        self.assertIn("confirm every medicine and dose", answer)


if __name__ == "__main__":
    unittest.main()
