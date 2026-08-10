import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import 'services.dart';

class ClinicalWorkspace extends StatefulWidget {
  const ClinicalWorkspace({required this.appointment, required this.patient, required this.repo, super.key});
  final Map<String, dynamic> appointment, patient;
  final DoctorRepository repo;
  @override State<ClinicalWorkspace> createState() => _ClinicalWorkspaceState();
}

class _ClinicalWorkspaceState extends State<ClinicalWorkspace> {
  final symptoms = TextEditingController(), diagnosis = TextEditingController(), plan = TextEditingController(), note = TextEditingController(), search = TextEditingController(), report = TextEditingController(), transcriptId = TextEditingController();
  String? consultationId, error;
  bool saving = false, aiLoading = false;
  final recorder = AudioRecorder();
  String? audioPath, voiceStatus;
  Duration recordingDuration = Duration.zero;
  DateTime? recordingStarted;
  List<Map<String, dynamic>> medicines = [];
  Map<String, dynamic>? selected, aiDraft;

  @override
  void dispose() { recorder.dispose(); super.dispose(); }

  Future<void> start() async {
    setState(() { saving = true; error = null; });
    try { final row = await widget.repo.consultation({'appointment_id': widget.appointment['id'], 'symptoms': symptoms.text, 'diagnosis': diagnosis.text, 'treatment_plan': plan.text, 'consultation_type': widget.appointment['consultation_type'] ?? 'in_person'}); setState(() => consultationId = row['id'].toString()); }
    catch (_) { setState(() => error = 'Unable to create consultation.'); }
    finally { if (mounted) setState(() => saving = false); }
  }

  Future<void> run(Future<void> Function() action) async {
    if (saving) return;
    setState(() { saving = true; error = null; });
    try { await action(); } catch (_) { setState(() => error = 'Unable to complete this clinical operation.'); }
    finally { if (mounted) setState(() => saving = false); }
  }

  Future<void> loadAi() async {
    if (consultationId == null || aiLoading) return;
    setState(() { aiLoading = true; error = null; });
    try { aiDraft = await widget.repo.ai(consultationId!); setState(() {}); }
    catch (_) { setState(() => error = 'AI draft unavailable. Continue manually.'); }
    finally { if (mounted) setState(() => aiLoading = false); }
  }

  Future<void> processAi() async {
    if (consultationId == null || transcriptId.text.trim().isEmpty || aiLoading) return;
    setState(() { aiLoading = true; error = null; });
    try { aiDraft = await widget.repo.processAi(consultationId!, transcriptId.text.trim()); setState(() {}); }
    catch (_) { setState(() => error = 'AI draft unavailable. You can continue manually.'); }
    finally { if (mounted) setState(() => aiLoading = false); }
  }

  Future<void> startRecording() async {
    if (saving || recordingStarted != null) return;
    try {
      if (!await recorder.hasPermission()) { setState(() => error = 'Microphone permission is required.'); return; }
      final dir = await getTemporaryDirectory();
      audioPath = '${dir.path}/verityscribe_${DateTime.now().millisecondsSinceEpoch}.wav';
      await recorder.start(const RecordConfig(encoder: AudioEncoder.wav, sampleRate: 16000, numChannels: 1), path: audioPath!);
      setState(() { recordingStarted = DateTime.now(); voiceStatus = 'RECORDING'; });
    } catch (_) { setState(() => error = 'Unable to start recording. Check microphone permission.'); }
  }

  Future<void> stopRecording() async {
    if (recordingStarted == null) return;
    try { await recorder.stop(); setState(() { recordingDuration = DateTime.now().difference(recordingStarted!); recordingStarted = null; voiceStatus = 'TRANSCRIBED'; }); }
    catch (_) { setState(() => error = 'Recording failed. Please try again.'); }
  }

  Future<void> cancelRecording() async { await recorder.cancel(); setState(() { recordingStarted = null; audioPath = null; recordingDuration = Duration.zero; voiceStatus = 'IDLE'; }); }

  Future<void> uploadRecording() async {
    if (audioPath == null || consultationId == null || saving) return;
    setState(() { saving = true; voiceStatus = 'UPLOADING'; error = null; });
    try {
      final result = await widget.repo.uploadVoice(consultationId!, audioPath!);
      final data = Map<String, dynamic>.from((result['data'] as Map?) ?? result);
      final transcript = data['transcript'];
      if (transcript is Map) { transcriptId.text = transcript['id'].toString(); voiceStatus = 'TRANSCRIBED'; }
      else { voiceStatus = 'ERROR'; error = 'Transcription is temporarily unavailable. You can retry or continue manually.'; }
      setState(() {});
    } catch (_) { setState(() { voiceStatus = 'ERROR'; error = 'Upload failed. You can retry or record again.'; }); }
    finally { if (mounted) setState(() => saving = false); }
  }

  Widget draftValue(String label, dynamic value) => Padding(padding: const EdgeInsets.only(top: 6), child: Text('$label: ${value ?? 'No data'}'));

  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Clinical workspace')),
    body: ListView(padding: const EdgeInsets.all(20), children: [
      Text('Patient: ${widget.patient['first_name'] ?? ''} ${widget.patient['last_name'] ?? ''}', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
      Text('${widget.appointment['appointment_date'] ?? ''} ${widget.appointment['appointment_time'] ?? ''} • ${widget.appointment['status'] ?? ''}'),
      TextField(controller: symptoms, decoration: const InputDecoration(labelText: 'Symptoms')),
      TextField(controller: diagnosis, decoration: const InputDecoration(labelText: 'Diagnosis')),
      TextField(controller: plan, decoration: const InputDecoration(labelText: 'Treatment plan')),
      if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
      FilledButton(onPressed: saving || consultationId != null ? null : start, child: Text(consultationId == null ? 'Start consultation' : 'Consultation created')),
      if (consultationId != null) ...[
        Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Voice consultation', style: TextStyle(fontWeight: FontWeight.w800)), Text(voiceStatus ?? 'IDLE'), if (recordingStarted != null) Text('Recording…'), if (recordingStarted == null && audioPath == null) FilledButton(onPressed: startRecording, child: const Text('Start recording')), if (recordingStarted != null) Wrap(spacing: 8, children: [FilledButton(onPressed: stopRecording, child: const Text('Stop')), OutlinedButton(onPressed: cancelRecording, child: const Text('Cancel'))]), if (audioPath != null && recordingStarted == null) Wrap(spacing: 8, children: [Text('Duration: ${recordingDuration.inSeconds}s'), FilledButton(onPressed: saving ? null : uploadRecording, child: const Text('Upload')), OutlinedButton(onPressed: () async { await cancelRecording(); await startRecording(); }, child: const Text('Record again'))])]))),
        Card(child: Padding(padding: const EdgeInsets.all(12), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('AI-GENERATED DRAFT — REQUIRES DOCTOR REVIEW', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.deepPurple)),
          TextField(controller: transcriptId, decoration: const InputDecoration(labelText: 'Verified transcript ID')),
          Wrap(spacing: 8, children: [OutlinedButton(onPressed: aiLoading ? null : processAi, child: Text(aiLoading ? 'Processing…' : 'Process AI')), OutlinedButton(onPressed: aiLoading ? null : loadAi, child: const Text('Load draft'))]),
          if (aiDraft != null) ...[draftValue('Summary', (aiDraft!['data'] ?? aiDraft)['summary'] ?? (aiDraft!['data'] ?? aiDraft)['summary_text']), draftValue('Key points', (aiDraft!['data'] ?? aiDraft)['key_points']), draftValue('Diagnosis candidates', (aiDraft!['data'] ?? aiDraft)['diagnoses']), draftValue('Medicine candidates', (aiDraft!['data'] ?? aiDraft)['medicines']), draftValue('Generated notes', (aiDraft!['data'] ?? aiDraft)['notes'])],
        ]))),
        TextField(controller: note, decoration: const InputDecoration(labelText: 'Consultation note')),
        OutlinedButton(onPressed: () => run(() => widget.repo.note(consultationId!, note.text)), child: const Text('Add note')),
        OutlinedButton(onPressed: () => run(() => widget.repo.diagnosis(consultationId!, diagnosis.text)), child: const Text('Add diagnosis')),
        TextField(controller: search, decoration: const InputDecoration(labelText: 'Search medicine')),
        OutlinedButton(onPressed: () => run(() async { medicines = await widget.repo.medicines(search.text); }), child: const Text('Search catalog')),
        if (medicines.isNotEmpty) DropdownButton<Map<String, dynamic>>(value: selected, items: medicines.map((m) => DropdownMenuItem(value: m, child: Text('${m['name'] ?? ''} ${m['strength'] ?? ''}'))).toList(), onChanged: (m) => setState(() => selected = m)),
        OutlinedButton(onPressed: selected == null ? null : () => run(() => widget.repo.prescription(consultationId!, selected!['id'].toString())), child: const Text('Create prescription')),
        TextField(controller: report, decoration: const InputDecoration(labelText: 'Report findings')),
        OutlinedButton(onPressed: () => run(() => widget.repo.report(consultationId!, report.text)), child: const Text('Create report')),
      ],
    ]),
  );
}
