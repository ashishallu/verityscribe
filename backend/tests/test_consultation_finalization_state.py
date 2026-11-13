import unittest
import threading
import time
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

from fastapi import HTTPException

from app.routers import ai_consultations


class _Result:
    def __init__(self, data):
        self.data = data


def test_fallback_summary_keeps_severity_and_other_answers_in_context():
    summary = ai_consultations._fallback_clinical_summary([
        {"role": "assistant", "content": "What brings you in today?"},
        {"role": "patient", "content": "I have a headache."},
        {"role": "assistant", "content": "When did it start?"},
        {"role": "patient", "content": "Five days ago."},
        {"role": "assistant", "content": "How often does it happen?"},
        {"role": "patient", "content": "It has been constant."},
        {"role": "assistant", "content": "Rate the pain from 1 to 10."},
        {"role": "patient", "content": "7"},
        {"role": "assistant", "content": "Any other symptoms? When did they begin?"},
        {"role": "patient", "content": "Vomiting, just two days back."},
    ], interview_complete=False)

    assert summary["chief_complaint"] == "I have a headache."
    assert "7/10" in summary["clinical_summary"]
    assert "Onset/duration" in summary["clinical_summary"]
    assert "Pattern/frequency" in summary["clinical_summary"]
    assert "Additional symptoms" in summary["clinical_summary"]
    assert summary["severity_frequency"] == ["It has been constant.", "7"]
    assert summary["interview_state"] == "ended_early"


def test_fallback_summary_contextualizes_joint_pain_side_duration_and_prior_surgery():
    summary = ai_consultations._fallback_clinical_summary([
        {"role": "assistant", "content": "What brings you in today?"},
        {"role": "patient", "content": "I have been having leg pain."},
        {"role": "assistant", "content": "Which side is painful, right or left?"},
        {"role": "patient", "content": "Just the right one."},
        {"role": "assistant", "content": "When did it start?"},
        {"role": "patient", "content": "Three or four days ago."},
        {"role": "assistant", "content": "How would you describe it?"},
        {"role": "patient", "content": "Sharp and constant."},
        {"role": "patient", "content": "I had knee surgery three years ago."},
        {"role": "patient", "content": "ಕಂಟೆಂಟ್"},
    ], interview_complete=False)

    text = summary["clinical_summary"]
    assert "Presenting concern" in text and "leg pain" in text
    assert "Location/laterality" in text and "right" in text
    assert "Onset/duration" in text and "Three or four days" in text
    assert "knee surgery three years ago" in text
    assert "ಕಂಟೆಂಟ್" in text


class _Query:
    def __init__(self, client, table):
        self.client, self.name = client, table
        self.filters, self.payload, self.projection = {}, None, None
        self.one = False

    def select(self, columns='*', **_kwargs):
        self.projection = columns
        return self

    def update(self, payload):
        self.payload = dict(payload)
        return self

    def eq(self, key, value):
        self.filters[key] = value
        return self

    def maybe_single(self):
        self.one = True
        return self

    def single(self):
        self.one = True
        return self

    def order(self, *_args, **_kwargs):
        return self

    def limit(self, *_args):
        return self

    def execute(self):
        rows = self.client.tables.setdefault(self.name, [])
        if self.payload is not None:
            with self.client.lock:
                matched = [row for row in rows if all(row.get(k) == v for k, v in self.filters.items())]
                if self.name == 'ai_consultation_sessions' and self.client.fail_summary_persist and self.payload.get('status') == 'awaiting_doctor_review':
                    raise RuntimeError('simulated summary write outage')
                for row in matched:
                    row.update(self.payload)
                if self.name == 'ai_consultation_sessions' and 'summary' in self.payload:
                    self.client.summary_write_count += len(matched)
                    if self.client.ambiguous_summary_response:
                        # Model PostgREST committing the update but losing the
                        # acknowledgement before the caller reads returned rows.
                        return _Result([])
        else:
            matched = [row for row in rows if all(row.get(k) == v for k, v in self.filters.items())]
        if self.projection and self.projection != '*':
            columns = [part.strip() for part in self.projection.split(',')]
            matched = [{key: row.get(key) for key in columns} for row in matched]
        return _Result((matched[0] if matched else None) if self.one else matched)


class _Client:
    def __init__(self, session, messages, appointment):
        self.tables = {
            'ai_consultation_sessions': [session],
            'ai_consultation_messages': messages,
            'appointments': [appointment],
        }
        self.fail_summary_persist = False
        self.ambiguous_summary_response = False
        self.lock = threading.Lock()
        self.summary_write_count = 0

    def table(self, name):
        return _Query(self, name)


def _summary(*, interview_state='complete', complete=True):
    return {
        'clinical_summary': 'Patient reports headache for two days.',
        'interview_state': interview_state,
        'interview_complete': complete,
        'review_required': True,
    }


class ConsultationFinalizationStateTests(unittest.TestCase):
    def test_server_state_mapping_requires_bound_persisted_summary(self):
        base = {'id': 's-1', 'mode': 'ai_virtual', 'status': 'awaiting_doctor_review'}
        complete = {**base, 'summary': {**_summary(), 'summary_id': 'sum-1', 'session_id': 's-1'}}
        incomplete = {**base, 'summary': {**_summary(interview_state='ended_early', complete=False), 'summary_id': 'sum-2', 'session_id': 's-1'}}
        self.assertEqual(ai_consultations._finalization_state(complete)['state'], 'completed')
        self.assertTrue(ai_consultations._finalization_state(complete)['summary_persisted'])
        self.assertEqual(ai_consultations._finalization_state(incomplete)['state'], 'incomplete')
        for status, expected in [('failed', 'failed'), ('processing', 'pending'), ('pending', 'pending'), ('mystery', 'unknown')]:
            self.assertEqual(ai_consultations._finalization_state({**complete, 'status': status})['state'], expected)
        for summary in [None, _summary(), {**_summary(), 'summary_id': 'sum-3'}, {**_summary(), 'summary_id': 'sum-4', 'session_id': 'other'}]:
            self.assertEqual(ai_consultations._finalization_state({**base, 'summary': summary})['state'], 'unknown')

    def test_generation_failure_persists_factual_incomplete_fallback(self):
        session = {'id': 's-1', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'in_progress', 'summary': {'interview_complete': True}}
        messages = [{'id': 'm-1', 'session_id': 's-1', 'role': 'patient', 'content': 'My headache began two days ago.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        claims = {'sub': 'auth-p-1', 'app_metadata': {'role': 'patient'}}
        with patch.object(ai_consultations, 'db', return_value=client), patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), patch.object(ai_consultations, 'patient_context', return_value={}), patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', side_effect=[RuntimeError('model unavailable'), _summary()]) as generate:
            saved = ai_consultations.end_consultation('s-1', claims)
            draft = saved['data']['session']['summary']
            self.assertEqual(saved['data']['finalization']['state'], 'incomplete')
            self.assertEqual(saved['data']['session']['status'], 'awaiting_doctor_review')
            self.assertEqual(draft['summary_generation_status'], 'failed')
            self.assertTrue(draft['summary_id'])
            self.assertEqual(draft['session_id'], 's-1')
            self.assertIn('headache began two days ago', draft['clinical_summary'])
            self.assertEqual(generate.call_count, 1)

            retry = ai_consultations.end_consultation('s-1', claims)
            self.assertEqual(retry['data']['finalization']['state'], 'incomplete')
            self.assertEqual(retry['data']['session']['summary']['summary_id'], draft['summary_id'])
            self.assertEqual(generate.call_count, 1)

    def test_summary_persistence_failure_stays_failed_not_completed(self):
        session = {'id': 's-1', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'in_progress'}
        messages = [{'id': 'm-1', 'session_id': 's-1', 'role': 'patient', 'content': 'I feel unwell.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        client.fail_summary_persist = True
        claims = {'sub': 'auth-p-1', 'app_metadata': {'role': 'patient'}}
        with patch.object(ai_consultations, 'db', return_value=client), patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), patch.object(ai_consultations, 'patient_context', return_value={}), patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', return_value=_summary()):
            with self.assertRaises(HTTPException) as error:
                ai_consultations.end_consultation('s-1', claims)
        self.assertEqual(error.exception.status_code, 503)
        row = client.tables['ai_consultation_sessions'][0]
        self.assertEqual(row['status'], 'failed')
        self.assertEqual(ai_consultations._finalization_state(row)['state'], 'failed')

    def test_committed_summary_with_lost_write_response_is_verified_not_failed(self):
        session = {'id': 's-ambiguous', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'in_progress', 'summary': {'interview_complete': True}}
        messages = [{'id': 'm-1', 'session_id': 's-ambiguous', 'role': 'patient', 'content': 'Headache for two days.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        client.ambiguous_summary_response = True
        claims = {'sub': 'auth-p-1', 'app_metadata': {'role': 'patient'}}
        with patch.object(ai_consultations, 'db', return_value=client), \
             patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), \
             patch.object(ai_consultations, 'patient_context', return_value={}), \
             patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', return_value=_summary()):
            response = ai_consultations.end_consultation('s-ambiguous', claims)
        durable = client.tables['ai_consultation_sessions'][0]
        self.assertEqual(durable['status'], 'awaiting_doctor_review')
        self.assertEqual(response['data']['finalization']['state'], 'completed')
        self.assertEqual(response['data']['session']['summary']['summary_id'], durable['summary']['summary_id'])
        self.assertEqual(client.summary_write_count, 1)

    def test_idempotent_end_returns_same_summary_id(self):
        summary = {**_summary(), 'summary_id': 'stable-summary', 'session_id': 's-1'}
        session = {'id': 's-1', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'mode': 'ai_virtual', 'status': 'awaiting_doctor_review', 'summary': summary}
        client = _Client(session, [], {'id': 'a-1'})
        with patch.object(ai_consultations, 'db', return_value=client), patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), patch.object(ai_consultations.gemini_live, 'generate_consultation_summary') as generate:
            first = ai_consultations.end_consultation('s-1', {'sub': 'p-1'})
            second = ai_consultations.end_consultation('s-1', {'sub': 'p-1'})
        self.assertEqual(first['data']['session']['summary']['summary_id'], 'stable-summary')
        self.assertEqual(second['data']['session']['summary']['summary_id'], 'stable-summary')
        generate.assert_not_called()

    def test_repeated_end_call_from_active_session_persists_only_one_draft(self):
        session = {'id': 's-1', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'in_progress'}
        messages = [{'id': 'm-1', 'session_id': 's-1', 'role': 'patient', 'content': 'Headache for two days.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        with patch.object(ai_consultations, 'db', return_value=client), \
             patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), \
             patch.object(ai_consultations, 'patient_context', return_value={}), \
             patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', return_value=_summary()) as generate:
            first = ai_consultations.end_consultation('s-1', {'sub': 'p-1'})
            second = ai_consultations.end_consultation('s-1', {'sub': 'p-1'})
        self.assertEqual(first['data']['session']['summary']['summary_id'], second['data']['session']['summary']['summary_id'])
        self.assertEqual(len(client.tables['ai_consultation_sessions']), 1)
        self.assertEqual(client.summary_write_count, 1)
        generate.assert_called_once()

    def test_concurrent_end_calls_claim_and_persist_exactly_once(self):
        session = {'id': 's-race', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'in_progress'}
        messages = [{'id': 'm-1', 'session_id': 's-race', 'role': 'patient', 'content': 'Headache for two days.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        start = threading.Barrier(3)
        generating = threading.Event()
        release = threading.Event()

        def slow_summary(**_kwargs):
            generating.set()
            if not release.wait(5):
                raise TimeoutError('test did not release summary generation')
            return _summary()

        results = []
        def end_call():
            start.wait(timeout=5)
            try:
                results.append(ai_consultations.end_consultation('s-race', {'sub': 'p-1'}))
            except Exception as exc:  # Capture the expected 409 from the losing request.
                results.append(exc)

        with patch.object(ai_consultations, 'db', return_value=client), \
             patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), \
             patch.object(ai_consultations, 'patient_context', return_value={}), \
             patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', side_effect=slow_summary) as generate:
            workers = [threading.Thread(target=end_call) for _ in range(2)]
            for worker in workers:
                worker.start()
            start.wait(timeout=5)
            self.assertTrue(generating.wait(5), 'one request should claim finalization and start summary generation')
            time.sleep(0.1)
            release.set()
            for worker in workers:
                worker.join(timeout=5)

        self.assertTrue(all(not worker.is_alive() for worker in workers))
        successes = [item for item in results if isinstance(item, dict)]
        conflicts = [item for item in results if isinstance(item, HTTPException)]
        self.assertEqual(len(successes), 1)
        self.assertEqual(len(conflicts), 1)
        self.assertEqual(conflicts[0].status_code, 409)
        self.assertEqual(client.summary_write_count, 1)
        self.assertEqual(len(client.tables['ai_consultation_sessions']), 1)
        generate.assert_called_once()

    def test_stale_processing_session_can_be_reclaimed_and_finalized(self):
        session = {
            'id': 's-stale', 'patient_id': 'p-1', 'appointment_id': 'a-1',
            'hospital_id': 'h-1', 'mode': 'ai_virtual', 'status': 'processing',
            'updated_at': (datetime.now(timezone.utc) - timedelta(minutes=6)).isoformat(),
            'summary': {'interview_complete': True},
        }
        messages = [{'id': 'm-1', 'session_id': 's-stale', 'role': 'patient', 'content': 'Headache for two days.'}]
        client = _Client(session, messages, {'id': 'a-1'})
        claims = {'sub': 'auth-p-1', 'app_metadata': {'role': 'patient'}}
        with patch.object(ai_consultations, 'db', return_value=client), \
             patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}), \
             patch.object(ai_consultations, 'patient_context', return_value={}), \
             patch.object(ai_consultations.gemini_live, 'generate_consultation_summary', return_value=_summary()):
            result = ai_consultations.end_consultation('s-stale', claims)
        self.assertEqual(result['data']['finalization']['state'], 'completed')
        self.assertEqual(client.tables['ai_consultation_sessions'][0]['status'], 'awaiting_doctor_review')

    def test_recent_processing_session_is_not_claimed_concurrently(self):
        session = {
            'id': 's-active', 'patient_id': 'p-1', 'appointment_id': 'a-1',
            'mode': 'ai_virtual', 'status': 'processing',
            'updated_at': datetime.now(timezone.utc).isoformat(),
        }
        client = _Client(session, [], {'id': 'a-1'})
        with patch.object(ai_consultations, 'db', return_value=client), \
             patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}):
            with self.assertRaises(HTTPException) as error:
                ai_consultations.end_consultation('s-active', {'sub': 'p-1'})
        self.assertEqual(error.exception.status_code, 409)
        self.assertEqual(client.tables['ai_consultation_sessions'][0]['status'], 'processing')

    def test_hangup_before_first_turn_persists_incomplete_summary(self):
        session = {'id': 's-early', 'patient_id': 'p-1', 'appointment_id': 'a-1', 'mode': 'ai_virtual', 'status': 'in_progress'}
        client = _Client(session, [], {'id': 'a-1'})
        with patch.object(ai_consultations, 'db', return_value=client), patch.object(ai_consultations, 'get_current_patient', return_value={'id': 'p-1'}):
            response = ai_consultations.end_consultation('s-early', {'sub': 'p-1'})
        durable = response['data']['session']
        self.assertEqual(response['data']['finalization']['state'], 'incomplete')
        self.assertEqual(durable['status'], 'awaiting_doctor_review')
        self.assertEqual(durable['summary']['session_id'], 's-early')
        self.assertTrue(durable['summary']['summary_id'])


if __name__ == '__main__':
    unittest.main()
