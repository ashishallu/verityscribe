import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/features/consultation_finalization_state.dart';

void main() {
  const sessionId = 'session-1';

  Map<String, dynamic> session({
    String status = 'awaiting_doctor_review',
    Map<String, dynamic>? summary,
  }) =>
      {
        'id': sessionId,
        'status': status,
        if (summary != null) 'summary': summary,
      };

  Map<String, dynamic> persistedSummary({
    String? forSession = sessionId,
    String? id = 'summary-1',
    String interviewState = 'complete',
    bool complete = true,
  }) =>
      {
        'summary_id': id,
        'session_id': forSession,
        'clinical_summary': 'Patient-reported cough for two days.',
        'interview_state': interviewState,
        'interview_complete': complete,
      };

  test('completed requires a saved summary bound to this session', () {
    expect(
      consultationFinalizationState(
        session(summary: persistedSummary()),
        expectedSessionId: sessionId,
        isVirtual: true,
      ),
      ConsultationFinalizationState.completed,
    );
  });

  test('saved early-end draft maps to incomplete', () {
    expect(
      consultationFinalizationState(
        session(
            summary: persistedSummary(
                interviewState: 'ended_early', complete: false)),
        expectedSessionId: sessionId,
        isVirtual: true,
      ),
      ConsultationFinalizationState.incomplete,
    );
  });

  test('persisted factual fallback stays incomplete, not failed or complete',
      () {
    final fallback = persistedSummary()..['completion_status'] = 'incomplete';
    expect(
      consultationFinalizationState(
        session(summary: fallback),
        expectedSessionId: sessionId,
        isVirtual: true,
      ),
      ConsultationFinalizationState.incomplete,
    );
  });

  test('processing and pending states remain processing', () {
    for (final status in ['processing', 'finalizing', 'pending']) {
      expect(
        consultationFinalizationState(
          session(status: status),
          expectedSessionId: sessionId,
          isVirtual: true,
        ),
        ConsultationFinalizationState.processing,
      );
    }
  });

  test('failed never maps to success, even if a summary is present', () {
    expect(
      consultationFinalizationState(
        session(status: 'failed', summary: persistedSummary()),
        expectedSessionId: sessionId,
        isVirtual: true,
      ),
      ConsultationFinalizationState.failed,
    );
  });

  test('missing summary, missing summary id, or wrong session fail closed', () {
    for (final value in <Map<String, dynamic>?>[
      null,
      persistedSummary(id: null),
      persistedSummary(forSession: 'other-session'),
      {...persistedSummary()..remove('session_id')},
    ]) {
      expect(
        consultationFinalizationState(
          session(summary: value),
          expectedSessionId: sessionId,
          isVirtual: true,
        ),
        ConsultationFinalizationState.unknown,
      );
    }
  });

  test('unknown states never claim success', () {
    expect(
      consultationFinalizationState(
        session(status: 'unexpected', summary: persistedSummary()),
        expectedSessionId: sessionId,
        isVirtual: true,
      ),
      ConsultationFinalizationState.unknown,
    );
  });
}
