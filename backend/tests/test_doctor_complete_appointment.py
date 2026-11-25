from types import SimpleNamespace

from app.routers import integration
from app.services.clinical_drafts import decode_free_text_prescription_notes


class Query:
    def __init__(self, client, table):
        self.client = client
        self.table_name = table
        self.filters = {}
        self.payload = None
        self.single = False

    def select(self, *_args, **_kwargs):
        return self

    def eq(self, key, value):
        self.filters[key] = value
        return self

    def limit(self, *_args):
        return self

    def maybe_single(self):
        self.single = True
        return self

    def update(self, payload):
        self.payload = dict(payload)
        return self

    def insert(self, payload):
        self.payload = payload if isinstance(payload, list) else [payload]
        self.inserting = True
        return self

    def execute(self):
        table = self.client.tables.setdefault(self.table_name, [])
        inserting = getattr(self, "inserting", False)
        if inserting:
            inserted = [dict(row) for row in self.payload]
            for index, row in enumerate(inserted):
                row.setdefault("id", f"{self.table_name}-{len(table) + index + 1}")
                table.append(row)
            return SimpleNamespace(data=inserted)
        matches = [row for row in table if all(row.get(k) == v for k, v in self.filters.items())]
        if self.payload is not None:
            for row in matches:
                row.update(self.payload)
        if self.single:
            return SimpleNamespace(data=matches[0] if matches else None)
        return SimpleNamespace(data=matches)


class Client:
    def __init__(self):
        self.tables = {
            "appointments": [{
                "id": "appointment-1", "patient_id": "patient-1", "doctor_id": "doctor-1",
                "consultation_type": "in_person", "status": "scheduled",
                "reason_for_visit": "Follow-up",
            }],
            "consultations": [],
            "prescriptions": [],
        }

    def table(self, name):
        return Query(self, name)


def test_complete_doctor_appointment_persists_free_text_prescription_and_marks_completed(monkeypatch):
    client = Client()
    monkeypatch.setattr(integration, "db", lambda: client)
    payload = integration.ConsultationCompleteRequest(
        clinical_note="Reviewed symptoms and plan.",
        diagnoses=["Migraine"],
        prescriptions=[{
            "name": "Example medication", "dosage": "10 mg", "frequency": "once daily",
            "duration": "5 days", "instructions": "Take with food",
        }],
        clinical_advice=["Maintain hydration."],
    )

    result = integration.complete_doctor_appointment(
        "appointment-1", payload, doctor={"id": "doctor-1"}
    )["data"]

    assert result["appointment"]["status"] == "completed"
    assert result["consultation"]["diagnosis"] == "Migraine"
    assert "Clinician-reviewed advice" in result["consultation"]["treatment_plan"]
    assert result["prescription"] is not None
    stored = decode_free_text_prescription_notes(result["prescription"]["notes"])
    assert stored["free_text_items"][0]["name"] == "Example medication"
    assert stored["free_text_items"][0]["dosage"] == "10 mg"


def test_free_text_prescription_notes_round_trip_preserves_existing_note():
    from app.services.clinical_drafts import encode_free_text_prescription_notes

    encoded = encode_free_text_prescription_notes(
        items=[{"name": "Medicine", "dosage": "5 mg"}],
        clinical_advice=["Rest."],
        source="test",
        source_id="appointment-1",
        existing_notes="Reviewed by clinician",
    )

    decoded = decode_free_text_prescription_notes(encoded)
    assert decoded["notes"] == "Reviewed by clinician"
    assert decoded["clinical_advice"] == ["Rest."]
