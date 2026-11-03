import json
import os
import unittest
from unittest.mock import patch

from app.services.ai_service import AIProvider, MedicalDocumentSummary


class MedicalDocumentSummaryTests(unittest.TestCase):
    def setUp(self):
        self.payload = {
            "document_type": "blood_report",
            "title": "Malaria blood report",
            "provider": "Sample Clinic",
            "document_date": "27-Oct-2026",
            "reason": "Fever with chills",
            "summary": "The report records malaria findings and low platelets.",
            "documented_diagnoses": ["Plasmodium falciparum"],
            "clinical_findings": ["Thrombocytopenia"],
            "medications": [],
            "test_results": [{
                "name": "Platelet count", "value": "72,000", "unit": "/µL",
                "reference_range": "150,000 - 410,000", "status": "Low",
                "interpretation": "Below the stated range",
            }],
            "recommended_tests": [],
            "follow_up": [],
            "warnings": [],
            "missing_information": [],
            "source_references": ["Platelet count 72,000 /µL", "invented source text"],
        }

    def test_summary_uses_existing_cloudflare_chat_and_validates_source_quotes(self):
        with patch.dict(os.environ, {"CLOUDFLARE_ACCOUNT_ID": "test-account", "CLOUDFLARE_API_TOKEN": "test-token"}), \
             patch.object(AIProvider, "_cloudflare_chat", return_value=json.dumps(self.payload)) as chat:
            summary = AIProvider().summarize_medical_document(
                "blood_report", "Platelet count 72,000 /µL",
            )

        self.assertIsInstance(summary, MedicalDocumentSummary)
        self.assertEqual(summary.test_results[0]["value"], "72,000")
        self.assertEqual(summary.test_results[0]["reference_range"], "150,000 - 410,000")
        self.assertEqual(summary.source_references, ["Platelet count 72,000 /µL"])
        chat.assert_called_once()
        self.assertIn("not instructions", chat.call_args.args[0])

    def test_invalid_model_json_is_reported_as_summary_failure(self):
        with patch.dict(os.environ, {"CLOUDFLARE_ACCOUNT_ID": "test-account", "CLOUDFLARE_API_TOKEN": "test-token"}), \
             patch.object(AIProvider, "_cloudflare_chat", return_value="not-json"):
            with self.assertRaisesRegex(RuntimeError, "invalid structured response"):
                AIProvider().summarize_medical_document("blood_report", "text")

    def test_summary_requires_existing_workers_ai_configuration(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "not configured"):
                AIProvider().summarize_medical_document("blood_report", "text")


if __name__ == "__main__":
    unittest.main()
