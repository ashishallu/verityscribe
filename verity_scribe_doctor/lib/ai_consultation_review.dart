import 'package:flutter/material.dart';

import 'clinical_workspace.dart';
import 'services.dart';

const _reviewBlue = Color(0xFF1769E0);

class AIConsultationReviewScreen extends StatefulWidget {
  const AIConsultationReviewScreen(
      {required this.appointment, required this.repo, super.key});
  final Map<String, dynamic> appointment;
  final DoctorRepository repo;
  @override
  State<AIConsultationReviewScreen> createState() =>
      _AIConsultationReviewScreenState();
}

class _AIConsultationReviewScreenState
    extends State<AIConsultationReviewScreen> {
  late Future<Map<String, dynamic>> _future;
  final _summary = TextEditingController();
  final _note = TextEditingController();
  Map<String, dynamic>? _data, _session, _patient;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _future = widget.repo.aiConsultation(widget.appointment['id'].toString());
  }

  @override
  void dispose() {
    _summary.dispose();
    _note.dispose();
    super.dispose();
  }

  void _accept(Map<String, dynamic> data) {
    _data = data;
    _session = data['session'] is Map
        ? Map<String, dynamic>.from(data['session'] as Map)
        : null;
    _patient = Map<String, dynamic>.from((data['patient'] as Map?) ?? {});
    final summary =
        Map<String, dynamic>.from((_session?['summary'] as Map?) ?? {});
    if (_summary.text.isEmpty) {
      _summary.text = summary['clinical_summary']?.toString() ?? '';
    }
  }

  Future<void> _review(String decision) async {
    if (_session == null || _saving) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final current =
          Map<String, dynamic>.from((_session!['summary'] as Map?) ?? {});
      current['clinical_summary'] = _summary.text.trim();
      final result = await widget.repo.reviewAiConsultation(
          _session!['id'].toString(),
          decision: decision,
          summary: current,
          note: _note.text);
      setState(() {
        _session = result;
      });
    } catch (_) {
      setState(() => _error = 'Unable to save the review. Please try again.');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('AI consultation review')),
        body: FutureBuilder<Map<String, dynamic>>(
          future: _future,
          builder: (context, state) {
            if (state.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (state.hasError) {
              return Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                const Text('Unable to load this AI consultation.'),
                TextButton(
                    onPressed: () => setState(() => _future = widget.repo
                        .aiConsultation(widget.appointment['id'].toString())),
                    child: const Text('Retry'))
              ]));
            }
            if (_data == null) {
              _accept(state.data!);
            }
            if (_session == null) {
              return ListView(padding: const EdgeInsets.all(20), children: [
                Text(
                    '${_patient?['first_name'] ?? ''} ${_patient?['last_name'] ?? ''}'
                        .trim(),
                    style: const TextStyle(
                        fontSize: 20, fontWeight: FontWeight.w800)),
                Text(
                    '${widget.appointment['appointment_date'] ?? ''} ${widget.appointment['appointment_time'] ?? ''} • ${widget.appointment['consultation_type'] == 'ai_virtual' ? 'AI Virtual' : 'AI Chat'}'),
                const SizedBox(height: 16),
                const Card(
                    child: Padding(
                        padding: EdgeInsets.all(18),
                        child: Text(
                            'The patient has not started this AI consultation yet. Opening this appointment does not start the patient session.'))),
              ]);
            }
            final session = _session!;
            final summary =
                Map<String, dynamic>.from((session['summary'] as Map?) ?? {});
            final messages = (_data!['messages'] as List? ?? const [])
                .map((e) => Map<String, dynamic>.from(e as Map))
                .toList();
            final status = session['status']?.toString() ?? 'in_progress';
            final canReview = status == 'awaiting_doctor_review';
            final similarCases = Map<String, dynamic>.from(
                (summary['similar_cases'] as Map?) ?? const {});
            final similarMatches = (similarCases['matches'] as List? ?? const [])
                .map((item) => Map<String, dynamic>.from(item as Map))
                .toList();
            final consultationId = session['consultation_id']?.toString();
            final type = widget.appointment['consultation_type'] == 'ai_virtual'
                ? 'AI Virtual'
                : 'AI Chat';
            return ListView(padding: const EdgeInsets.all(18), children: [
              _card('Patient and appointment', [
                Text(
                    '${_patient?['first_name'] ?? ''} ${_patient?['last_name'] ?? ''}'
                        .trim(),
                    style: const TextStyle(
                        fontSize: 19, fontWeight: FontWeight.w800)),
                Text(
                    '${widget.appointment['appointment_date'] ?? ''} ${widget.appointment['appointment_time'] ?? ''} • $type'),
                Text('Status: ${status.replaceAll('_', ' ')}'),
              ]),
              _card('AI-generated clinical summary • edit before approval', [
                const Text(
                    'This is a draft. Confirm every fact against the transcript and patient record.',
                    style: TextStyle(
                        color: Colors.deepOrange, fontWeight: FontWeight.w600)),
                TextField(
                    controller: _summary,
                    minLines: 4,
                    maxLines: 9,
                    enabled: canReview,
                    decoration: const InputDecoration(
                        labelText: 'Clinical summary',
                        border: OutlineInputBorder())),
              ]),
              _card('Extracted clinical information', [
                _items('Symptoms', summary['symptoms']),
                _items('Relevant history', summary['relevant_history']),
                _items('Medications', summary['medications']),
                _items('Allergies', summary['allergies']),
                _items('Clinical considerations (unconfirmed)',
                    summary['clinical_considerations']),
                _items('Suggested investigations',
                    summary['suggested_investigations']),
                _items('Evidence references', summary['evidence_references']),
                _items('Uncertainties', summary['uncertainties']),
                _items('Warnings', summary['warning_indicators']),
                _similarCases(similarCases, similarMatches),
                const SizedBox(height: 6),
                const Text(
                    'Draft prescription: none generated. Any prescription must be created and signed separately by the doctor.',
                    style: TextStyle(fontWeight: FontWeight.w600)),
              ]),
              _card(
                  'Complete transcript',
                  messages.isEmpty
                      ? [const Text('No messages saved yet.')]
                      : messages
                          .map((message) => Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: Text(
                                  '${message['role'] == 'patient' ? 'Patient' : message['role'] == 'system' ? 'Doctor request' : 'Verity'}: ${message['content'] ?? ''}',
                                  style: const TextStyle(height: 1.4))))
                          .toList()),
              if (canReview)
                _card('Doctor review', [
                  TextField(
                      controller: _note,
                      minLines: 2,
                      maxLines: 4,
                      decoration: const InputDecoration(
                          labelText: 'Review note / request details',
                          border: OutlineInputBorder())),
                  if (_error != null)
                    Text(_error!, style: const TextStyle(color: Colors.red)),
                  Wrap(spacing: 10, children: [
                    FilledButton.icon(
                        onPressed: _saving ? null : () => _review('approved'),
                        icon: const Icon(Icons.verified_outlined),
                        label: Text(
                            _saving ? 'Saving…' : 'Approve reviewed summary')),
                    OutlinedButton.icon(
                        onPressed: _saving
                            ? null
                            : () => _review('request_information'),
                        icon: const Icon(Icons.question_answer_outlined),
                        label: const Text('Request more information')),
                    OutlinedButton.icon(
                        onPressed: _saving ? null : () => _review('rejected'),
                        icon: const Icon(Icons.cancel_outlined),
                        label: const Text('Reject draft')),
                  ]),
                ]),
              if (status == 'approved')
                _card('Review recorded', [
                  const Text(
                      'Doctor approval is recorded. The AI did not create a diagnosis or prescription.'),
                  if (consultationId != null)
                    FilledButton.icon(
                        onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                                builder: (_) => ClinicalWorkspace(
                                    appointment: widget.appointment,
                                    patient: _patient ?? {},
                                    repo: widget.repo,
                                    initialConsultationId: consultationId))),
                        icon: const Icon(Icons.medical_information_outlined),
                        label: const Text('Continue in clinical workspace')),
                ]),
              if (status == 'rejected')
                _card('Draft rejected', [
                  Text(session['review_note']?.toString() ??
                      'The draft was rejected by the assigned doctor.')
                ]),
            ]);
          },
        ),
      );

  Widget _card(String title, List<Widget> children) => Card(
      margin: const EdgeInsets.only(bottom: 14),
      child: Padding(
          padding: const EdgeInsets.all(16),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title,
                style: const TextStyle(
                    color: _reviewBlue,
                    fontWeight: FontWeight.w800,
                    fontSize: 16)),
            const SizedBox(height: 12),
            ...children.map((child) => Padding(
                padding: const EdgeInsets.only(bottom: 8), child: child))
          ])));

  Widget _items(String label, dynamic value) {
    final list = value is List ? value : const [];
    final text = list.isEmpty
        ? 'None reported'
        : list
            .map((e) => e is Map
                ? e.entries
                    .map((entry) => '${entry.key}: ${entry.value}')
                    .join(' • ')
                : e.toString())
            .join('\n');
    return Text('$label: $text', style: const TextStyle(height: 1.4));
  }

  Widget _similarCases(Map<String, dynamic> data, List<Map<String, dynamic>> matches) {
    final status = data['status']?.toString() ?? 'not_available';
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('De-identified, doctor-approved symptom matches',
          style: TextStyle(fontWeight: FontWeight.w700)),
      const SizedBox(height: 6),
      if (matches.isEmpty)
        Text(status == 'no_matches'
            ? 'No matching approved cases were found at this hospital.'
            : status == 'unavailable'
                ? 'Case matching is temporarily unavailable.'
                : 'No matching cases are available for this consultation.'),
      for (final match in matches)
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text((match['symptom_terms'] as List? ?? const [])
              .map((term) => term.toString())
              .join(', ')),
          subtitle: const Text('Clinician-approved intake symptom pattern'),
        ),
      const Text(
          'Matches are contextual examples only. They do not establish a diagnosis or recommend treatment.',
          style: TextStyle(fontSize: 12, color: Colors.black54)),
    ]);
  }
}
