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
              'Two speech-recognition services will independently transcribe this recording. A third AI service reconciles differences before clinician review.',
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
                      ? 'Running independent transcripts and reconciliation…'
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
          const SectionTitle('Reconciled transcript'),
          TextField(
              controller: transcript,
              maxLines: null,
              minLines: 8,
              decoration: const InputDecoration(hintText: 'Transcript')),
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
          const SizedBox(height: 20),
          const SectionTitle('Independent transcription check'),
          SoftCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const Text('Primary ASR',
                    style: TextStyle(fontWeight: FontWeight.w800)),
                const SizedBox(height: 6),
                Text(result.primaryTranscript),
                const Divider(height: 28),
                const Text('Secondary ASR',
                    style: TextStyle(fontWeight: FontWeight.w800)),
                const SizedBox(height: 6),
                Text(result.secondaryTranscript),
                if (result.conflicts.isNotEmpty) ...[
                  const Divider(height: 28),
                  const Text('Items requiring clinician attention',
                      style: TextStyle(fontWeight: FontWeight.w800)),
                  const SizedBox(height: 6),
                  ...result.conflicts.map((item) => Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Text('• $item')))
                ]
              ])),
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
}
