from io import BytesIO
import unittest
from unittest.mock import AsyncMock, patch

from starlette.datastructures import UploadFile

from app.routers.integration import ChatRequest, secure_chat, upload_patient_chat_report
from app.services.ai_service import MedicalDocumentSummary


SESSION_ID = "11111111-1111-4111-8111-111111111111"


class _Response:
    def __init__(self, data):
        self.data = data


class _StorageBucket:
    def upload(self, *_args, **_kwargs): pass
    def remove(self, *_args, **_kwargs): pass


class _Storage:
    def from_(self, _bucket): return _StorageBucket()


class _Query:
    def __init__(self, client, table):
        self.client, self.table = client, table
        self.filters = {}
        self.operation = "select"
        self.row = None
        self.ids = None

    def select(self, *_args, **_kwargs): return self
    def eq(self, key, value):
        self.filters[key] = value
        return self
    def in_(self, key, values):
        self.filters[key] = None
        self.ids = set(values)
        return self
    def order(self, *_args, **_kwargs): return self
    def limit(self, *_args): return self
    def upsert(self, row):
        self.operation, self.row = "upsert", row
        return self
    def insert(self, row):
        self.operation, self.row = "insert", row
        return self
    def update(self, row):
        self.operation, self.row = "update", row
        return self
    def execute(self):
        if self.table == "ai_chat_messages":
            if self.operation == "insert":
                self.client.messages.append(dict(self.row))
                return _Response([self.client.messages[-1]])
            return _Response([row for row in self.client.messages
                              if row.get("session_id") == self.filters.get("session_id")])
        if self.table == "ai_chat_sessions":
            return _Response([row for row in self.client.sessions
                              if all(row.get(key) == value for key, value in self.filters.items())])
        if self.table == "medical_documents":
            if self.operation == "upsert":
                self.client.document = dict(self.row)
                return _Response([self.client.document])
            row = self.client.document
            if row and all(row.get(key) == value for key, value in self.filters.items()):
                return _Response([row])
            return _Response([])
        if self.table == "patient_embeddings":
            if self.operation == "insert":
                self.client.embedding = {"id": "embedding-id", **self.row}
                return _Response([self.client.embedding])
            if self.operation == "update":
                if self.client.embedding:
                    self.client.embedding.update(self.row)
                return _Response([self.client.embedding] if self.client.embedding else [])
            row = self.client.embedding
            if row and all(row.get(key) == value for key, value in self.filters.items()
                           if key != "source_document_id") and (
                               self.ids is None or row.get("source_document_id") in self.ids
                           ) and (self.filters.get("source_document_id") is None or
                                  row.get("source_document_id") == self.filters["source_document_id"]):
                return _Response([row])
            return _Response([])
        raise AssertionError(f"unexpected table {self.table}")


class _Client:
    def __init__(self):
        self.document = None
        self.embedding = None
        self.storage = _Storage()
        self.sessions = [{"id": SESSION_ID, "patient_id": "patient-test", "ended_at": None}]
        self.messages = []

    def table(self, name): return _Query(self, name)


class ReportSummaryResponseTests(unittest.TestCase):
    def test_upload_returns_structured_summary_and_separate_full_source_text(self):
        client = _Client()
        extracted = "Extracted from uploaded document: Drug A 5 mg daily\nReview in one week"
        summary = MedicalDocumentSummary(
            document_type="prescription", title="Prescription",
            provider="Not specified", document_date="Not specified", reason="Not specified",
            summary="One medicine and a one-week follow-up are documented.",
            documented_diagnoses=[],
            clinical_findings=["Cough documented"],
            medications=[{"name": "Drug A", "dosage": "5 mg", "frequency": "daily",
                          "duration": "Not specified", "instructions": "Not specified"}],
            test_results=[], recommended_tests=[], follow_up=["Review in one week"],
            warnings=[], missing_information=["Treatment duration not specified"],
            source_references=["Drug A 5 mg daily"],
        )
        document = UploadFile(filename="prescription.png", file=BytesIO(b"\x89PNG\r\n\x1a\nsynthetic"))
        async def inline_threadpool(function, *args):
            return function(*args)

        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration._owned_chat_session", return_value={"id": SESSION_ID}), \
             patch("app.routers.integration._chat_document_bucket", return_value="private-test-bucket"), \
             patch("app.routers.integration._read_report_upload", new=AsyncMock(return_value=b"\x89PNG\r\n\x1a\nsynthetic")), \
             patch("app.routers.integration.run_in_threadpool", side_effect=inline_threadpool), \
             patch("app.routers.integration.validate_image_dimensions"), \
             patch("app.routers.integration.extract_image_document", return_value=(
                 "Drug A 5 mg daily\nReview in one week", [{
                     "test_name": "Glucose", "result_value": "98", "result_text": "98",
                     "unit": "mg/dL", "reference_range": "70-140", "result_status": None,
                     "specimen": None, "collection_date": None, "report_date": None,
                     "page_number": 1, "table_index": 1, "row_index": 1,
                     "source_text": "Glucose | 98 | mg/dL | 70-140",
                     "extraction_confidence": 0.98, "ambiguous": False,
                 }],
             )), \
             patch("app.routers.integration.ai_provider.summarize_medical_document", return_value=summary):
            operation = upload_patient_chat_report(
                report_type="prescription", session_id=SESSION_ID, document=document,
                upload_id="synthetic-upload-12345", patient={"id": "patient-test"},
            )
            try:
                operation.send(None)
            except StopIteration as completed:
                result = completed.value
            else:
                self.fail("mocked upload unexpectedly suspended on external async work")

        data = result["data"]
        self.assertEqual(data["summary_status"], "ready")
        self.assertEqual(data["summary"]["medications"][0]["name"], "Drug A")
        self.assertEqual(data["summary"]["follow_up"], ["Review in one week"])
        self.assertEqual(data["extracted_text"], "Drug A 5 mg daily\nReview in one week")
        self.assertNotIn("Drug A 5 mg daily", data["message"])
        self.assertEqual(client.embedding["content"], extracted)
        self.assertEqual(client.embedding["structured_results"][0]["result_value"], "98")
        self.assertEqual(data["structured_results"][0]["reference_range"], "70-140")
        self.assertEqual(client.document["patient_id"], "patient-test")
        self.assertEqual(client.document["session_id"], SESSION_ID)

    def test_upload_then_exact_chat_query_reads_persisted_structured_row(self):
        client = _Client()
        extracted = "[Page 1]\nInvestigation Observed Value Units Reference Range\nPlatelet Count 72,000 /uL 150,000-450,000"
        structured = [{
            "test_name": "Platelet Count", "result_value": "72,000", "result_text": "72,000",
            "unit": "/uL", "reference_range": "150,000-450,000", "result_status": None,
            "specimen": None, "collection_date": None, "report_date": None,
            "page_number": 1, "table_index": 1, "row_index": 1,
            "source_text": "Platelet Count | 72,000 | /uL | 150,000-450,000",
            "extraction_confidence": .98, "ambiguous": False,
        }]
        document = UploadFile(filename="synthetic-lab.png", file=BytesIO(b"\x89PNG\r\n\x1a\nsynthetic"))
        async def inline_threadpool(function, *args):
            return function(*args)

        def complete_upload():
            with patch("app.routers.integration.db", return_value=client), \
                 patch("app.routers.integration._owned_chat_session", return_value={"id": SESSION_ID}), \
                 patch("app.routers.integration._chat_document_bucket", return_value="private-test-bucket"), \
                 patch("app.routers.integration._read_report_upload", new=AsyncMock(return_value=b"\x89PNG\r\n\x1a\nsynthetic")), \
                 patch("app.routers.integration.run_in_threadpool", side_effect=inline_threadpool), \
                 patch("app.routers.integration.validate_image_dimensions"), \
                 patch("app.routers.integration.extract_image_document", return_value=(extracted, structured)), \
                 patch("app.routers.integration.ai_provider.summarize_medical_document", side_effect=RuntimeError("offline")):
                operation = upload_patient_chat_report(
                    report_type="lab_report", session_id=SESSION_ID, document=document,
                    upload_id="synthetic-e2e-upload-123", patient={"id": "patient-test"},
                )
                try:
                    operation.send(None)
                except StopIteration as completed:
                    return completed.value
                self.fail("mock upload unexpectedly suspended")

        upload_result = complete_upload()
        self.assertEqual(client.embedding["structured_results"][0]["test_name"], "Platelet Count")
        self.assertEqual(upload_result["data"]["structured_results"][0]["result_value"], "72,000")

        with patch("app.routers.integration.db", return_value=client), \
             patch("app.routers.integration.get_current_patient", return_value={"id": "patient-test"}), \
             patch("app.routers.integration.ai_provider.answer", side_effect=AssertionError("exact lookup must bypass model")):
            chat_result = secure_chat(
                ChatRequest(message="What is the patient's platelet count?", session_id=SESSION_ID),
                {"sub": "patient-test", "app_metadata": {"role": "patient"}},
            )
        self.assertIn("72,000 /uL", chat_result["data"]["answer"])
        self.assertIn("150,000-450,000", chat_result["data"]["answer"])
        self.assertIn("page 1", chat_result["data"]["answer"])


if __name__ == "__main__":
    unittest.main()
