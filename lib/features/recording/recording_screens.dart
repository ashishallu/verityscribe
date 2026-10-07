import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';
import '../../core/services/voice_draft_service.dart';
import '../../providers/app_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/ui.dart';

class RecordingScreen extends ConsumerStatefulWidget {
  const RecordingScreen({super.key});
  @override
  ConsumerState<RecordingScreen> createState() => _RecordingScreenState();
}

class _RecordingScreenState extends ConsumerState<RecordingScreen>
    with SingleTickerProviderStateMixin {
  final recorder = AudioRecorder();
  Timer? timer;
  StreamSubscription<Uint8List>? webAudio;
  final webBytes = <int>[];
  late final AnimationController pulse;
  String? audioPath, selectedAppointment, error;
  Duration elapsed = Duration.zero;
  bool recording = false, processing = false;
  @override
  void initState() {
    super.initState();
    pulse = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 900))
      ..repeat(reverse: true);
  }

  @override
  void dispose() {
    timer?.cancel();
    webAudio?.cancel();
    recorder.dispose();
    pulse.dispose();
    super.dispose();
  }

  Future<void> start() async {
    if (selectedAppointment == null) {
      setState(() => error = 'Choose an appointment before recording.');
      return;
    }
    try {
      if (!await recorder.hasPermission()) {
        setState(() => error =
            'Microphone permission is required to record a voice draft.');
        return;
      }
      if (kIsWeb) {
        // `startStream` on the web implementation only supports raw PCM16.
        // The bytes are wrapped in a WAV container before the upload so the
        // transcription service receives a standard audio file.
        const config = RecordConfig(
            encoder: AudioEncoder.pcm16bits,
            sampleRate: 16000,
            numChannels: 1);
        webBytes.clear();
        webAudio = (await recorder.startStream(config)).listen(webBytes.addAll);
      } else {
        const config = RecordConfig(
            encoder: AudioEncoder.wav, sampleRate: 16000, numChannels: 1);
        final directory = await getTemporaryDirectory();
        audioPath =
            '${directory.path}/verityscribe_${DateTime.now().millisecondsSinceEpoch}.wav';
        await recorder.start(config, path: audioPath!);
      }
      setState(() {
        error = null;
        recording = true;
        elapsed = Duration.zero;
      });
      timer = Timer.periodic(
          const Duration(seconds: 1),
          (_) => mounted
              ? setState(() => elapsed += const Duration(seconds: 1))
              : null);
    } catch (exception) {
      setState(() => error =
          'Unable to start recording: $exception. Check that no other app is using the microphone.');
    }
  }

  Future<void> stopAndProcess() async {
    if (!recording ||
        (!kIsWeb && audioPath == null) ||
        selectedAppointment == null) return;
    timer?.cancel();
    setState(() {
      recording = false;
      processing = true;
      error = null;
    });
    try {
      await recorder.stop();
      await webAudio?.cancel();
      final service = VoiceDraftService(ref.read(supabaseAuthProvider));
      final result = kIsWeb
          ? await service.uploadBytes(
              appointmentId: selectedAppointment!, bytes: _asWav(webBytes))
          : await service.upload(
              appointmentId: selectedAppointment!, filePath: audioPath!);
      if (!mounted) return;
      context.go('/session-review', extra: result);
    } on VoiceDraftException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } catch (_) {
      if (mounted)
        setState(
            () => error = 'Unable to process the recording. Please try again.');
    } finally {
      if (mounted) setState(() => processing = false);
    }
  }

  Future<void> cancel() async {
    await recorder.cancel();
    timer?.cancel();
    setState(() {
      recording = false;
      audioPath = null;
      elapsed = Duration.zero;
      error = null;
    });
  }

  String get clock =>
      '${elapsed.inMinutes.toString().padLeft(2, '0')}:${(elapsed.inSeconds % 60).toString().padLeft(2, '0')}';

  List<int> _asWav(List<int> pcm) {
    const sampleRate = 16000;
    const channels = 1;
    const bitsPerSample = 16;
    final header = ByteData(44)
      ..setUint32(0, 0x46464952, Endian.little) // RIFF
      ..setUint32(4, 36 + pcm.length, Endian.little)
      ..setUint32(8, 0x45564157, Endian.little) // WAVE
      ..setUint32(12, 0x20746d66, Endian.little) // format chunk
      ..setUint32(16, 16, Endian.little)
      ..setUint16(20, 1, Endian.little)
      ..setUint16(22, channels, Endian.little)
      ..setUint32(24, sampleRate, Endian.little)
      ..setUint32(28, sampleRate * channels * bitsPerSample ~/ 8,
          Endian.little)
      ..setUint16(32, channels * bitsPerSample ~/ 8, Endian.little)
      ..setUint16(34, bitsPerSample, Endian.little)
      ..setUint32(36, 0x61746164, Endian.little) // data
      ..setUint32(40, pcm.length, Endian.little);
    return Uint8List.fromList([...header.buffer.asUint8List(), ...pcm]);
  }

  @override
  Widget build(BuildContext context) {
    final appointments = ref.watch(appointmentsLiveProvider);
    return Scaffold(
        appBar: AppBar(title: const Text('Record securely')),
        body: ListView(padding: const EdgeInsets.all(20), children: [
          Text('Voice consultation',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          const Text(
              'Two ASR passes run in parallel. When both return a transcript, a reconciliation step compares them before clinician review.',
              style: TextStyle(color: AppTheme.muted, height: 1.45)),
          const SizedBox(height: 20),
          appointments.when(
              loading: () => const LinearProgressIndicator(),
              error: (_, __) => const SoftCard(
                  child: Text(
                      'Appointments are unavailable. Return to Home and retry.')),
              data: (items) {
                final eligible = items.where((item) {
                  final status = item.status.toLowerCase();
                  final scheduled =
                      DateTime.tryParse('${item.date} ${item.time}');
                  return !{'cancelled', 'completed', 'no_show'}
                          .contains(status) &&
                      scheduled != null &&
                      scheduled.isAfter(
                          DateTime.now().subtract(const Duration(minutes: 10)));
                }).toList();
                if (eligible.isEmpty)
                  return const SoftCard(
                      child: Text(
                          'A current appointment is required before recording a voice draft.'));
                if (selectedAppointment == null ||
                    !eligible.any((item) => item.id == selectedAppointment))
                  selectedAppointment = eligible.first.id;
                return SoftCard(
                    child: DropdownButtonFormField<String>(
                        initialValue: selectedAppointment,
                        decoration:
                            const InputDecoration(labelText: 'Appointment'),
                        items: eligible
                            .map((item) => DropdownMenuItem(
                                value: item.id,
                                child: Text(
                                    '${item.doctorName.isEmpty ? 'Appointment' : item.doctorName} • ${item.date} ${item.time}')))
                            .toList(),
                        onChanged: recording || processing
                            ? null
                            : (value) =>
                                setState(() => selectedAppointment = value)));
              }),
          const SizedBox(height: 28),
          Center(
              child: ScaleTransition(
                  scale: Tween<double>(begin: .92, end: 1.08).animate(pulse),
                  child: Container(
                      width: 150,
                      height: 150,
                      decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: const LinearGradient(
                              colors: [AppTheme.blue, AppTheme.cyan]),
                          boxShadow: [
                            BoxShadow(
                                color: AppTheme.blue.withValues(alpha: .25),
                                blurRadius: 30)
                          ]),
                      child: Icon(
                          recording
                              ? Icons.graphic_eq_rounded
                              : Icons.mic_rounded,
                          size: 64,
                          color: Colors.white)))),
          const SizedBox(height: 20),
          Center(
              child: Text(clock,
                  style: Theme.of(context)
                      .textTheme
                      .displaySmall
                      ?.copyWith(fontWeight: FontWeight.w800))),
          const SizedBox(height: 5),
          Center(
              child: Text(
                  processing
                      ? 'Running configured ASR models in parallel…'
                      : recording
                          ? 'Recording securely'
                          : 'Ready to record',
                  style: const TextStyle(color: AppTheme.muted))),
          const SizedBox(height: 24),
          SoftCard(
              child:
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Icon(Icons.verified_user_outlined, color: AppTheme.blue),
            const SizedBox(width: 12),
            const Expanded(
                child: Text(
                    'This creates a draft only. Your assigned doctor must review and approve any clinical summary, diagnosis, or prescription.',
                    style: TextStyle(height: 1.45)))
          ])),
          if (error != null)
            Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(error!, style: const TextStyle(color: Colors.red))),
          const SizedBox(height: 18),
          if (!recording)
            FilledButton.icon(
                onPressed: processing ? null : start,
                icon: const Icon(Icons.mic_rounded),
                label: const Text('Start recording'),
                style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(54)))
          else
            Row(children: [
              Expanded(
                  child: OutlinedButton.icon(
                      onPressed: cancel,
                      icon: const Icon(Icons.close_rounded),
                      label: const Text('Cancel'),
                      style: OutlinedButton.styleFrom(
                          minimumSize: const Size.fromHeight(54)))),
              const SizedBox(width: 12),
              Expanded(
                  child: FilledButton.icon(
                      onPressed: stopAndProcess,
                      icon: const Icon(Icons.stop_rounded),
                      label: const Text('Stop & review'),
                      style: FilledButton.styleFrom(
                          minimumSize: const Size.fromHeight(54))))
            ]),
        ]));
  }
}

class SessionReviewScreen extends ConsumerStatefulWidget {
  const SessionReviewScreen({this.result, super.key});
  final VoiceDraftResult? result;
  @override
  ConsumerState<SessionReviewScreen> createState() =>
      _SessionReviewScreenState();
}

class _SessionReviewScreenState extends ConsumerState<SessionReviewScreen> {
  late final TextEditingController transcript;
  bool saving = false;
  String? saveError;
  @override
  void initState() {
    super.initState();
    transcript =
        TextEditingController(text: widget.result?.finalTranscript ?? '');
  }

  @override
  void dispose() {
    transcript.dispose();
    super.dispose();
  }

  Future<void> saveCorrection() async {
    final result = widget.result;
    final text = transcript.text.trim();
    if (result == null || text.isEmpty) {
      setState(() => saveError = 'The transcript cannot be empty.');
      return;
    }
    setState(() {
      saving = true;
      saveError = null;
    });
    try {
      await VoiceDraftService(ref.read(supabaseAuthProvider)).saveCorrection(
        transcriptId: result.transcriptId,
        transcriptText: text,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Transcript correction saved for doctor review.')),
      );
    } on VoiceDraftException catch (exception) {
      if (mounted) setState(() => saveError = exception.message);
    } catch (_) {
      if (mounted) {
        setState(() => saveError = 'Unable to save the transcript correction.');
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = widget.result;
    if (result == null)
      return const Scaffold(
          body: Center(
              child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                      'No voice draft is open. Record a consultation voice draft from Home.'))));
    return Scaffold(
        appBar: AppBar(title: const Text('Review voice draft')),
        body: ListView(padding: const EdgeInsets.all(20), children: [
          Text('Transcript ready for clinician review',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          const Text(
              'You may correct wording for clarity. This draft is not a diagnosis or prescription and cannot be accepted as a clinical record until your assigned doctor reviews it.',
              style: TextStyle(color: AppTheme.muted, height: 1.45)),
          const SizedBox(height: 22),
          SoftCard(
              child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Icon(
                    result.reconciliationStatus == 'reconciled' ||
                            result.reconciliationStatus == 'identical'
                        ? Icons.verified_outlined
                        : Icons.info_outline_rounded,
                    color: AppTheme.blue),
                const SizedBox(width: 10),
                Expanded(
                    child: Text(_reconciliationMessage(result),
                        style: const TextStyle(height: 1.4)))
              ])),
          const SizedBox(height: 14),
          const SectionTitle('Curated transcript draft'),
          TextField(
              controller: transcript,
              maxLines: null,
              minLines: 8,
              decoration: const InputDecoration(hintText: 'Transcript')),
          const SizedBox(height: 8),
          Card(
              clipBehavior: Clip.antiAlias,
              child: ExpansionTile(
                  leading: const Icon(Icons.graphic_eq_rounded,
                      color: AppTheme.blue),
                  title: Text('View model predictions (${result.predictions.length})'),
                  subtitle: const Text('Open to compare each model with the curated draft'),
                  childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  children: [
                    Wrap(spacing: 10, runSpacing: 4, children: [
                      _legendDot(Colors.deepOrange, 'Missing from final'),
                      _legendDot(Colors.amber.shade800, 'Model disagreement'),
                    ]),
                    const SizedBox(height: 10),
                    ...result.predictions.map(_predictionCard),
                    if (result.conflicts.isNotEmpty) ...[
                      const Divider(),
                      Align(
                          alignment: Alignment.centerLeft,
                          child: Text('Review these differences',
                              style: Theme.of(context).textTheme.titleSmall)),
                      ...result.conflicts.map((item) => Align(
                          alignment: Alignment.centerLeft,
                          child: Padding(
                              padding: const EdgeInsets.only(top: 6),
                              child: Text('• $item')))),
                    ]
                  ])),
          if (saveError != null)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child:
                  Text(saveError!, style: const TextStyle(color: Colors.red)),
            ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: saving ? null : saveCorrection,
            icon: const Icon(Icons.save_outlined),
            label: Text(saving ? 'Saving…' : 'Save transcript correction'),
          ),
          const SizedBox(height: 18),
          SoftCard(
              color: const Color(0xFFF3EDFF),
              child: const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.medical_information_outlined,
                        color: Colors.deepPurple),
                    SizedBox(width: 12),
                    Expanded(
                        child: Text(
                            'The transcript has been submitted as a voice draft. A doctor must create, edit, and approve the clinical summary and prescription in the Doctor App.'))
                  ])),
          const SizedBox(height: 18),
          FilledButton(
              onPressed: () => context.go('/records'),
              style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(54)),
              child: const Text('View medical records'))
        ]));
  }

  String _reconciliationMessage(VoiceDraftResult result) => switch (
        result.reconciliationStatus) {
          'reconciled' => 'Available ASR drafts were compared and reconciled. Review the wording and any flagged differences below.',
          'identical' => 'All available ASR drafts match. Please still review the transcript before submitting it to your clinician.',
          'single_provider' => 'Only one ASR model returned a transcript. It is shown as a draft, not as a cross-checked result.',
          'fallback_selected' => 'Automatic comparison was unavailable. Weighted model agreement selected a best-effort draft; please review it carefully.',
          _ => 'Review this best-effort transcript carefully before submitting it to your clinician.',
        };

  Widget _legendDot(Color color, String label) => Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 5),
        Text(label, style: Theme.of(context).textTheme.labelSmall),
      ]);

  Widget _predictionCard(ASRModelPrediction prediction) {
    final successful = widget.result!.predictions
        .where((item) => item.status == 'success')
        .toList();
    final tokenCounts = <String, int>{};
    for (final item in successful) {
      final uniqueWords = RegExp(r"[a-z0-9']+")
          .allMatches(item.text.toLowerCase())
          .map((match) => match.group(0)!)
          .toSet();
      for (final word in uniqueWords) {
        tokenCounts.update(word, (count) => count + 1, ifAbsent: () => 1);
      }
    }
    final finalWords = RegExp(r"[a-z0-9']+")
        .allMatches(transcript.text.toLowerCase())
        .map((match) => match.group(0)!)
        .toSet();
    final statusColor = prediction.status == 'success'
        ? Colors.teal
        : Theme.of(context).colorScheme.error;
    final predictionStyle = Theme.of(context).textTheme.bodyMedium?.copyWith(
          fontSize: 14,
          height: 1.5,
          color: Theme.of(context).colorScheme.onSurface,
        ) ??
        const TextStyle(fontSize: 14, height: 1.5);
    final predictionText = prediction.text.isEmpty
        ? Text('No transcript returned (${prediction.errorCode ?? 'provider unavailable'}).',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: statusColor,
                  height: 1.35,
                ))
        : RichText(
            text: TextSpan(
                style: predictionStyle,
                children: RegExp(r'\S+\s*')
                    .allMatches(prediction.text)
                    .map((match) {
                  final token = match.group(0)!;
                  final wordMatch = RegExp(r"[a-z0-9']+")
                      .firstMatch(token.toLowerCase());
                  final word = wordMatch?.group(0);
                  final missing = word != null && !finalWords.contains(word);
                  final disputed = word != null &&
                      (tokenCounts[word] ?? 0) < successful.length;
                  final color = missing
                      ? Colors.deepOrange
                      : disputed
                          ? Colors.amber.shade800
                          : null;
                  return TextSpan(
                      text: token,
                      style: color == null
                          ? null
                          : TextStyle(
                              color: color,
                              decoration: TextDecoration.underline,
                              decorationColor: color,
                              decorationThickness: 1.25));
                }).toList()));
    return Card(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Icon(Icons.circle, size: 10, color: statusColor),
                    const SizedBox(width: 8),
                    Expanded(
                        child: Text(prediction.model,
                            style: const TextStyle(fontWeight: FontWeight.w700))),
                    Text(prediction.status == 'success'
                        ? '${prediction.latencyMs} ms'
                        : 'unavailable')
                  ]),
                  const SizedBox(height: 8),
                  predictionText,
                ])));
  }
}
