/// Safe client-side interpretation of the server's durable finalization state.
///
/// Success requires a persisted summary explicitly associated with this
/// session. Unknown or legacy payloads intentionally fail closed.
enum ConsultationFinalizationState {
  inProgress,
  processing,
  completed,
  incomplete,
  failed,
  unknown,
}

ConsultationFinalizationState consultationFinalizationState(
  Map<String, dynamic> session, {
  required String expectedSessionId,
  required bool isVirtual,
}) {
  final status = session['status']?.toString();
  if (status == 'failed') return ConsultationFinalizationState.failed;
  if (const {'processing', 'finalizing', 'pending'}.contains(status)) {
    return ConsultationFinalizationState.processing;
  }
  if (status == 'in_progress') {
    return ConsultationFinalizationState.inProgress;
  }

  final summaryValue = session['summary'];
  if (summaryValue is! Map) return ConsultationFinalizationState.unknown;
  final summary = Map<String, dynamic>.from(summaryValue);
  final sessionId = session['id']?.toString();
  final summarySessionId = summary['session_id']?.toString();
  final summaryId = summary['summary_id']?.toString().trim();
  final clinicalText = summary['clinical_summary']?.toString().trim();
  final persisted = sessionId == expectedSessionId &&
      summarySessionId == expectedSessionId &&
      summaryId != null &&
      summaryId.isNotEmpty &&
      clinicalText != null &&
      clinicalText.isNotEmpty;
  if (!persisted ||
      !const {
        'awaiting_doctor_review',
        'approved',
        'rejected',
        'completed',
        'incomplete',
      }.contains(status)) {
    return ConsultationFinalizationState.unknown;
  }

  if (status == 'incomplete' ||
      const {'incomplete', 'ended_early', 'summary_failed'}
          .contains(summary['completion_status']) ||
      summary['interview_state'] == 'ended_early' ||
      summary['interview_complete'] == false) {
    return ConsultationFinalizationState.incomplete;
  }
  if (status == 'completed' ||
      !isVirtual ||
      (summary['interview_state'] == 'complete' &&
          summary['interview_complete'] == true)) {
    return ConsultationFinalizationState.completed;
  }
  return ConsultationFinalizationState.unknown;
}
