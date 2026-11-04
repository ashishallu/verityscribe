import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:go_router/go_router.dart';
import 'package:record/record.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../core/constants/endpoints.dart';
import '../models/entities.dart';
import '../providers/app_providers.dart';

const _blue = Color(0xFF2458E6);
const _ink = Color(0xFF192B4D);

class AIAppointmentEntryScreen extends StatelessWidget {
  const AIAppointmentEntryScreen({required this.appointment, super.key});
  final LiveAppointment appointment;

  @override
  Widget build(BuildContext context) {
    final virtual = appointment.consultationType == 'ai_virtual';
    final accent = virtual ? const Color(0xFF08A8C9) : _blue;
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FC),
      appBar: AppBar(title: const Text('Appointment details')),
      body: ListView(padding: const EdgeInsets.all(20), children: [
        Card(
            child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(virtual ? 'AI Virtual' : 'AI Chat',
                          style: TextStyle(
                              color: accent,
                              fontWeight: FontWeight.w800,
                              fontSize: 13)),
                      const SizedBox(height: 8),
                      Text(
                          appointment.doctorName.isEmpty
                              ? 'Your care team'
                              : appointment.doctorName,
                          style: const TextStyle(
                              color: _ink,
                              fontSize: 22,
                              fontWeight: FontWeight.w800)),
                      const SizedBox(height: 8),
                      Text(
                          '${appointment.department} • ${appointment.hospital}',
                          style: const TextStyle(color: Color(0xFF667085))),
                      Text('${appointment.date} ${appointment.time}',
                          style: const TextStyle(color: Color(0xFF667085))),
                      const Divider(height: 28),
                      Text(
                          virtual
                              ? 'Speak with Verity in a live voice session with captions and interruption support.'
                              : 'Share what is bothering you in a private text conversation with Verity.',
                          style: const TextStyle(height: 1.45)),
                      const SizedBox(height: 12),
                      const Text(
                          'Verity is an AI intake assistant. It does not diagnose or prescribe. A doctor will review the summary before it becomes part of your clinical record.',
                          style: TextStyle(
                              color: Color(0xFF667085), height: 1.45)),
                      const SizedBox(height: 20),
                      SizedBox(
                          width: double.infinity,
                          child: FilledButton.icon(
                            onPressed: () => context.push(
                                '/ai-consultation/${appointment.id}',
                                extra: appointment),
                            icon: Icon(virtual
                                ? Icons.graphic_eq_rounded
                                : Icons.chat_bubble_outline_rounded),
                            label: const Text('Start consultation'),
                          )),
                    ]))),
      ]),
    );
  }
}

class AIConsultationScreen extends ConsumerStatefulWidget {
  const AIConsultationScreen({required this.appointment, super.key});
  final LiveAppointment appointment;
  @override
  ConsumerState<AIConsultationScreen> createState() =>
      _AIConsultationScreenState();
}

class _AIConsultationScreenState extends ConsumerState<AIConsultationScreen> {
  final _input = TextEditingController();
  final _recorder = AudioRecorder();
  final _tts = FlutterTts();
  final _scroll = ScrollController();
  final List<Map<String, dynamic>> _messages = [];
  Timer? _clock;
  StreamSubscription<Uint8List>? _audioSubscription;
  StreamSubscription<dynamic>? _voiceSubscription;
  WebSocketChannel? _voiceChannel;
  DateTime? _startedAt;
  String? _sessionId;
  String _liveTranscript = '';
  String? _pendingChatKey,
      _pendingChatText,
      _pendingVoiceKey,
      _pendingVoiceText;
  int _requestCounter = 0;
  int _voiceInterruptionCount = 0;
  final Map<String, int> _voiceTurnInterruptions = {};
  String? _error;
  Map<String, dynamic>? _summary;
  bool _consented = false, _loading = false, _recording = false;
  bool _speaking = false, _ended = false, _emergency = false, _muted = false;

  bool get _virtual => widget.appointment.consultationType == 'ai_virtual';

  String _newRequestKey() =>
      '${DateTime.now().microsecondsSinceEpoch}-${++_requestCounter}';

  @override
  void initState() {
    super.initState();
    _tts.setStartHandler(() {
      if (mounted) setState(() => _speaking = true);
    });
    _tts.setCompletionHandler(() {
      if (mounted) setState(() => _speaking = false);
    });
    _tts.setCancelHandler(() {
      if (mounted) setState(() => _speaking = false);
    });
    _tts.setErrorHandler((_) {
      if (mounted) {
        setState(() {
          _speaking = false;
          _error = 'Voice playback failed. Tap the speaker button to replay the response.';
        });
      }
    });
  }

  @override
  void dispose() {
    _clock?.cancel();
    _input.dispose();
    _scroll.dispose();
    _audioSubscription?.cancel();
    _voiceSubscription?.cancel();
    _voiceChannel?.sink.close();
    _recorder.dispose();
    _tts.stop();
    super.dispose();
  }

  Map<String, dynamic> _body(dynamic value) =>
      Map<String, dynamic>.from(value as Map);
  Map<String, dynamic> _data(dynamic value) {
    final body = _body(value);
    return _body(body['data'] ?? body);
  }

  void _loadSession(Map<String, dynamic> data) {
    final session = _body(data['session']);
    _sessionId = session['id'].toString();
    _messages
      ..clear()
      ..addAll((data['messages'] as List? ?? const []).map((e) => _body(e)));
    _summary = session['summary'] is Map ? _body(session['summary']) : null;
    _ended = {'awaiting_doctor_review', 'approved', 'rejected'}
        .contains(session['status']);
    _startedAt ??= DateTime.tryParse(session['started_at']?.toString() ?? '') ??
        DateTime.now();
    _clock ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _start() async {
    if (!_consented) {
      setState(() =>
          _error = 'Please confirm consent to AI processing before starting.');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await ref.read(apiClientProvider).post<dynamic>(
        '/ai-consultations/appointments/${widget.appointment.id}/start',
        {
          'consent': true,
          'voice_consent': _virtual,
          'similar_case_consent': true,
        },
      );
      setState(() => _loadSession(_data(response.data)));
      if (_virtual) {
        await _beginLiveVoice();
        if (_messages.isNotEmpty) {
          unawaited(_speak(_messages.last['content'].toString()));
        }
      }
    } catch (_) {
      setState(() => _error =
          'Unable to connect to the consultation. Check your connection and retry.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _speak(String text) async {
    final response = text.trim();
    if (!_virtual || _muted || response.isEmpty) return;
    if (mounted) setState(() => _error = null);
    try {
      await _tts.setVolume(1.0);
      await _tts.setSpeechRate(0.62);
      final result = await _tts.speak(response);
      if (result != 1 && mounted) {
        setState(() => _error =
            'Voice playback did not start. Tap the speaker button to try again.');
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error =
            'Voice playback failed. Tap the speaker button to replay the response.');
      }
    }
  }

  Future<void> _toggleSpeech() async {
    final muted = !_muted;
    setState(() => _muted = muted);
    if (muted) {
      await _tts.stop();
      return;
    }
    final lastAssistant = _messages.lastWhere(
      (message) => message['role'] == 'assistant',
      orElse: () => <String, dynamic>{},
    );
    if (lastAssistant.isNotEmpty) {
      unawaited(_speak(lastAssistant['content']?.toString() ?? ''));
    }
  }

  Future<void> _sendText() async {
    final text = _input.text.trim();
    if (text.isEmpty || _sessionId == null || _loading || _ended) return;
    final requestKey = _pendingChatText == text && _pendingChatKey != null
        ? _pendingChatKey!
        : _newRequestKey();
    _pendingChatKey = requestKey;
    _pendingChatText = text;
    _input.clear();
    setState(() {
      _loading = true;
      _error = null;
      _messages.add({'role': 'patient', 'content': text});
    });
    try {
      final response = await ref.read(apiClientProvider).post<dynamic>(
          '/ai-consultations/$_sessionId/messages',
          {'message': text, 'request_key': requestKey});
      final result = _data(response.data);
      final added = (result['messages'] as List? ?? const [])
          .map((e) => _body(e))
          .toList();
      setState(() {
        if (added.isNotEmpty) {
          _messages.removeWhere(
              (m) => m['role'] == 'patient' && m['content'] == text);
          _messages.addAll(added);
        }
        _emergency = result['emergency_warning'] == true;
      });
      _pendingChatKey = null;
      _pendingChatText = null;
      if (_virtual && added.length > 1) {
        unawaited(_speak(added.last['content'].toString()));
      }
    } catch (_) {
      try {
        final saved = _data((await ref
                .read(apiClientProvider)
                .get<dynamic>('/ai-consultations/$_sessionId'))
            .data);
        setState(() {
          _loadSession(saved);
          final messages = (saved['messages'] as List? ?? const [])
              .map((e) => _body(e))
              .toList();
          final matchingPatient = messages
              .cast<Map<String, dynamic>?>()
              .firstWhere(
                  (m) =>
                      m?['request_key'] == requestKey &&
                      m?['role'] == 'patient',
                  orElse: () => null);
          final matchingAssistant = messages.any((m) =>
              m['request_key'] == requestKey && m['role'] == 'assistant');
          _error = matchingPatient != null && !matchingAssistant
              ? 'Your message is saved. The AI reply is unavailable; resend the same message to retry.'
              : matchingAssistant
                  ? 'Your conversation is saved.'
                  : 'Your message was not saved. Please retry.';
          if (matchingAssistant || matchingPatient == null) {
            _pendingChatKey = null;
            _pendingChatText = null;
          }
          if (matchingPatient == null) _input.text = text;
        });
      } catch (_) {
        setState(() {
          _messages.removeWhere(
              (m) => m['role'] == 'patient' && m['content'] == text);
          _input.text = text;
          _pendingChatKey = null;
          _pendingChatText = null;
          _error =
              'Could not confirm that your message was saved. Please retry.';
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _beginLiveVoice() async {
    if (_sessionId == null || _voiceChannel != null || _ended) return;
    try {
      if (!await _recorder.hasPermission()) {
        setState(() => _error = 'Microphone permission is needed for AI Virtual.');
        return;
      }
      final ticketResponse = await ref.read(apiClientProvider).post<dynamic>(
          '/ai-consultations/$_sessionId/voice-ticket', const {});
      final ticket = _data(ticketResponse.data)['ticket'].toString();
      if (ticket.isEmpty || ticket == 'null') throw StateError('Voice ticket missing');
      final base = Uri.parse(apiBaseUrl);
      final wsUri = base.replace(
        scheme: base.scheme == 'https' ? 'wss' : 'ws',
        path: '${base.path}/ai-consultations/$_sessionId/voice-stream',
        query: null,
        fragment: null,
      );
      final protocol = 'verityscribe-ticket.$ticket';
      final channel = WebSocketChannel.connect(wsUri, protocols: [protocol]);
      _voiceChannel = channel;
      await channel.ready.timeout(const Duration(seconds: 15));
      _voiceSubscription = channel.stream.listen(
        _handleLiveVoiceEvent,
        onError: (_) {
          if (mounted) {
            setState(() {
              _recording = false;
              _error = 'The live voice connection was interrupted. Reconnect and try again.';
            });
          }
          unawaited(_stopLiveVoice());
        },
        onDone: () {
          if (!_ended) unawaited(_stopLiveVoice());
        },
      );
      final audio = await _recorder.startStream(const RecordConfig(
        encoder: AudioEncoder.pcm16bits,
        sampleRate: 16000,
        numChannels: 1,
        echoCancel: true,
        noiseSuppress: true,
        autoGain: true,
        streamBufferSize: 4096,
      ));
      _audioSubscription = audio.listen(
        (chunk) {
          if (_recording && _voiceChannel == channel) channel.sink.add(chunk);
        },
        onError: (_) {
          if (mounted) setState(() => _error = 'Microphone audio could not be streamed.');
        },
      );
      if (mounted) {
        setState(() {
          _recording = true;
          _error = null;
        });
      }
    } catch (_) {
      await _voiceChannel?.sink.close();
      _voiceChannel = null;
      if (mounted) {
        setState(() {
          _recording = false;
          _error = 'Live voice could not connect. Check microphone permission and retry.';
        });
      }
    }
  }

  void _handleLiveVoiceEvent(dynamic raw) {
    if (raw is! String) return;
    try {
      final event = Map<String, dynamic>.from(jsonDecode(raw) as Map);
      switch (event['type']) {
        case 'transcript':
          if (mounted) setState(() => _liveTranscript = event['text']?.toString() ?? '');
          break;
        case 'speech_started':
          _voiceInterruptionCount++;
          if (_speaking) unawaited(_tts.stop());
          if (mounted) setState(() => _speaking = false);
          break;
        case 'turn_started':
          final key = event['request_key']?.toString();
          _pendingVoiceKey = key;
          _pendingVoiceText = event['transcript']?.toString();
          if (key != null) _voiceTurnInterruptions[key] = _voiceInterruptionCount;
          break;
        case 'turn':
          final key = event['request_key']?.toString();
          final added = (event['messages'] as List? ?? const [])
              .map((item) => _body(item))
              .toList();
          if (mounted) {
            setState(() {
              _messages.removeWhere((row) => row['request_key'] == key);
              _messages.addAll(added);
              _emergency = event['emergency_warning'] == true;
              _liveTranscript = '';
            });
          }
          _pendingVoiceKey = null;
          _pendingVoiceText = null;
          final wasInterrupted = key != null &&
              _voiceTurnInterruptions.remove(key) != _voiceInterruptionCount;
          if (added.length > 1 && !wasInterrupted) {
            unawaited(_speak(added.last['content'].toString()));
          }
          break;
        case 'turn_error':
          final key = event['request_key']?.toString();
          _pendingVoiceKey = key;
          _pendingVoiceText = event['transcript']?.toString();
          if (key != null) _voiceTurnInterruptions.remove(key);
          if (mounted) setState(() => _error = event['message']?.toString() ?? 'Voice turn failed. Retry the saved transcript.');
          break;
        case 'error':
          if (mounted) setState(() => _error = event['message']?.toString());
          break;
      }
    } catch (_) {
      if (mounted) setState(() => _error = 'The live voice service returned an invalid response.');
    }
  }

  Future<void> _toggleLiveMicrophone() async {
    if (_voiceChannel == null) {
      await _beginLiveVoice();
      return;
    }
    try {
      if (_recording) {
        await _recorder.pause();
        if (mounted) setState(() => _recording = false);
      } else {
        await _recorder.resume();
        if (mounted) setState(() => _recording = true);
      }
    } catch (_) {
      if (mounted) setState(() => _error = 'The microphone could not be toggled.');
    }
  }

  Future<void> _stopLiveVoice() async {
    await _audioSubscription?.cancel();
    _audioSubscription = null;
    await _voiceSubscription?.cancel();
    _voiceSubscription = null;
    try {
      await _recorder.stop();
    } catch (_) {}
    await _voiceChannel?.sink.close();
    _voiceChannel = null;
    if (mounted) setState(() => _recording = false);
  }

  Future<void> _retryPendingVoice() async {
    final key = _pendingVoiceKey;
    final text = _pendingVoiceText;
    if (key == null || text == null || _sessionId == null || _loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await ref.read(apiClientProvider).post<dynamic>(
          '/ai-consultations/$_sessionId/messages',
          {'message': text, 'request_key': key});
      final result = _data(response.data);
      final added = (result['messages'] as List? ?? const [])
          .map((e) => _body(e))
          .toList();
      setState(() {
        _messages.removeWhere((row) => row['request_key'] == key);
        _messages.addAll(added);
        _emergency = result['emergency_warning'] == true;
      });
      _pendingVoiceKey = null;
      _pendingVoiceText = null;
      if (added.length > 1) unawaited(_speak(added.last['content'].toString()));
    } catch (_) {
      setState(() => _error =
          'The saved voice turn could not be retried. Your transcript remains saved.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _end() async {
    if (_sessionId == null || _loading || _ended) return;
    if (_virtual) await _stopLiveVoice();
    if (_speaking) await _tts.stop();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await ref
          .read(apiClientProvider)
          .post<dynamic>('/ai-consultations/$_sessionId/end', const {});
      setState(() => _loadSession(_data(response.data)));
    } catch (_) {
      setState(() => _error =
          'The draft could not be prepared. Your saved conversation is safe; please retry.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String get _duration {
    final seconds = (_startedAt == null
            ? 0
            : DateTime.now().difference(_startedAt!).inSeconds)
        .clamp(0, 99999);
    final h = seconds ~/ 3600, m = (seconds % 3600) ~/ 60, s = seconds % 60;
    return h > 0
        ? '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}'
        : '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final title = _virtual ? 'AI Virtual' : 'AI Chat';
    if (_sessionId == null) {
      return Scaffold(
        backgroundColor: const Color(0xFFF5F7FC),
        appBar: AppBar(title: Text(title)),
        body: ListView(padding: const EdgeInsets.all(22), children: [
          const Icon(Icons.verified_user_outlined, color: _blue, size: 48),
          const SizedBox(height: 14),
          Text('Before you begin',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(color: _ink, fontWeight: FontWeight.w800)),
          const SizedBox(height: 10),
          const Text(
              'You are speaking with an AI intake assistant, not a doctor. Your conversation and a draft summary will be shared securely with the doctor assigned to this appointment for review. The AI will not diagnose or prescribe.',
              style: TextStyle(height: 1.5)),
          const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                  'Your doctor may also see up to three symptom-pattern matches from previously doctor-approved AI consultations at this hospital. The matches omit patient names and are shown only to your care team; they are not diagnoses or recommendations.',
                  style: TextStyle(height: 1.5))),
          if (_virtual)
            const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Text(
                    'AI Virtual streams microphone audio to the configured Cloudflare Workers AI speech recognition service. VerityScribe does not save raw audio. Your transcript and conversation are securely shared with your assigned doctor.',
                    style: TextStyle(height: 1.5))),
          if (!_virtual)
            const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Text(
                    'Conversation text and selected health record context are processed by the configured Cloudflare Workers AI service and securely shared with your assigned doctor.',
                    style: TextStyle(height: 1.5))),
          CheckboxListTile(
              value: _consented,
              onChanged: (v) => setState(() => _consented = v == true),
              contentPadding: EdgeInsets.zero,
              title: const Text(
                  'I consent to AI processing, secure sharing with my assigned doctor, and de-identified symptom matching for my care team.'),
              controlAffinity: ListTileControlAffinity.leading),
          if (_error != null)
            Text(_error!, style: const TextStyle(color: Colors.red)),
          FilledButton.icon(
              onPressed: _loading ? null : _start,
              icon: const Icon(Icons.play_arrow_rounded),
              label: Text(_loading ? 'Connecting…' : 'Start consultation')),
        ]),
      );
    }

    if (_ended) {
      return Scaffold(
        appBar: AppBar(title: Text(title)),
        body: ListView(padding: const EdgeInsets.all(20), children: [
          const Icon(Icons.hourglass_top_rounded, size: 46, color: _blue),
          const SizedBox(height: 12),
          const Text('Sent for doctor review',
              style: TextStyle(
                  fontSize: 23, fontWeight: FontWeight.w800, color: _ink)),
          const Text(
              'Your conversation and AI draft are saved. Your doctor must review them before they become part of your clinical record.'),
          const SizedBox(height: 16),
          if (_summary != null)
            Card(
                child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Text((_summary!['clinical_summary'] ??
                            'Draft summary unavailable')
                        .toString()))),
          OutlinedButton.icon(
              onPressed: () => context.go('/appointments'),
              icon: const Icon(Icons.calendar_month),
              label: const Text('Back to appointments')),
        ]),
      );
    }

    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FC),
      appBar: AppBar(title: Text(title), actions: [
        Padding(
            padding: const EdgeInsets.only(right: 16, top: 18),
            child: Text(_duration,
                style: const TextStyle(fontWeight: FontWeight.w700)))
      ]),
      body: _virtual ? _voiceBody() : _chatBody(),
    );
  }

  Widget _chatBody() => Column(children: [
        const Padding(
            padding: EdgeInsets.fromLTRB(18, 8, 18, 0),
            child: Text('AI intake assistant • doctor review required',
                style: TextStyle(color: Color(0xFF667085)))),
        if (_emergency) _emergencyCard(),
        Expanded(
            child: ListView.builder(
                controller: _scroll,
                padding: const EdgeInsets.all(16),
                itemCount: _messages.length + (_loading ? 1 : 0),
                itemBuilder: (context, index) {
                  if (index == _messages.length) {
                    return const Padding(
                        padding: EdgeInsets.all(12),
                        child: Align(
                            alignment: Alignment.centerLeft,
                            child: SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                    strokeWidth: 2))));
                  }
                  return _messageBubble(_messages[index]);
                })),
        if (_error != null)
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(_error!, style: const TextStyle(color: Colors.red))),
        SafeArea(
            top: false,
            child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 6, 12, 8),
                child: Row(children: [
                  Expanded(
                      child: TextField(
                          controller: _input,
                          minLines: 1,
                          maxLines: 4,
                          enabled: !_loading,
                          textInputAction: TextInputAction.send,
                          onSubmitted: (_) => _sendText(),
                          decoration: InputDecoration(
                              hintText: 'Describe what is bothering you…',
                              filled: true,
                              fillColor: Colors.white,
                              border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(24),
                                  borderSide: BorderSide.none)))),
                  IconButton(
                      onPressed: _loading ? null : _sendText,
                      icon: const Icon(Icons.send_rounded, color: _blue)),
                  IconButton(
                      onPressed: _end,
                      tooltip: 'End consultation',
                      icon: const Icon(Icons.check_circle_outline,
                          color: Colors.redAccent)),
                ]))),
      ]);

  Widget _voiceBody() => Column(children: [
        if (_emergency) _emergencyCard(),
        Expanded(
            child: ListView(
                controller: _scroll,
                padding: const EdgeInsets.symmetric(horizontal: 18),
                children: [
              const SizedBox(height: 18),
              Center(
                  child: AnimatedContainer(
                      duration: const Duration(milliseconds: 450),
                      width: _recording || _speaking ? 174 : 150,
                      height: _recording || _speaking ? 174 : 150,
                      decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                              colors: _recording
                                  ? const [Color(0xFF14B8A6), Color(0xFF2463EB)]
                                  : const [
                                      Color(0xFF285EEA),
                                      Color(0xFF25B9D4)
                                    ]),
                          boxShadow: [
                            BoxShadow(
                                color: _blue.withValues(
                                    alpha: _recording || _speaking ? .27 : .12),
                                blurRadius: 35,
                                spreadRadius: 8)
                          ]),
                      child: Icon(
                          _speaking
                              ? Icons.graphic_eq_rounded
                              : _recording
                                  ? Icons.mic_rounded
                                  : Icons.auto_awesome_rounded,
                          color: Colors.white,
                          size: 60))),
              const SizedBox(height: 14),
              Center(
                  child: Text(
                      _speaking
                          ? 'Verity is speaking'
                          : _recording
                              ? 'Listening • microphone active'
                              : _loading
                                  ? 'Processing your turn…'
                                  : 'Ready when you are',
                      style: const TextStyle(
                          color: _ink,
                          fontSize: 17,
                          fontWeight: FontWeight.w700))),
              Center(
                  child: Text('Private voice session • $_duration',
                      style: const TextStyle(color: Color(0xFF667085)))),
              if (_liveTranscript.isNotEmpty)
                Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Card(
                        child: Padding(
                            padding: const EdgeInsets.all(14),
                            child: Text('Live caption: $_liveTranscript',
                                style: const TextStyle(height: 1.45))))),
              const SizedBox(height: 20),
              ..._messages.map(_messageBubble),
            ])),
        if (_error != null)
          Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(_error!, style: const TextStyle(color: Colors.red))),
        SafeArea(
            top: false,
            child: Padding(
                padding: const EdgeInsets.fromLTRB(18, 8, 18, 12),
                child:
                    Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  IconButton.filledTonal(
                      onPressed: _toggleSpeech,
                      tooltip: _muted
                          ? 'Unmute and replay last response'
                          : 'Mute AI speech',
                      icon: Icon(_muted
                          ? Icons.volume_off_outlined
                          : Icons.volume_up_outlined)),
                  const SizedBox(width: 16),
                  if (_pendingVoiceText != null)
                    FilledButton.icon(
                        onPressed: _loading ? null : _retryPendingVoice,
                        icon: const Icon(Icons.refresh_rounded),
                        label: const Text('Retry saved turn'))
                  else
                    GestureDetector(
                        onTap: _toggleLiveMicrophone,
                        child: Tooltip(
                            message: _voiceChannel == null
                                ? 'Connect microphone'
                                : _recording
                                    ? 'Mute microphone'
                                    : 'Unmute microphone',
                        child: AnimatedContainer(
                            duration: const Duration(milliseconds: 200),
                            width: 72,
                            height: 72,
                            decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: _recording ? Colors.redAccent : _blue),
                            child: Icon(
                                _voiceChannel == null || !_recording
                                    ? Icons.mic_off_rounded
                                    : Icons.mic_rounded,
                                color: Colors.white,
                                size: 32)))),
                  const SizedBox(width: 16),
                  IconButton.filledTonal(
                      onPressed: _end,
                      tooltip: 'End consultation',
                      icon: const Icon(Icons.call_end_rounded,
                          color: Colors.redAccent)),
                ]))),
      ]);

  Widget _messageBubble(Map<String, dynamic> message) {
    final patient = message['role'] == 'patient';
    final doctorRequest = message['role'] == 'system';
    return Align(
        alignment: patient ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
            margin: const EdgeInsets.symmetric(vertical: 5),
            padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 11),
            constraints: const BoxConstraints(maxWidth: 600),
            decoration: BoxDecoration(
                color: patient ? _blue : Colors.white,
                borderRadius: BorderRadius.circular(17)),
            child: Text(
                '${doctorRequest ? 'Your doctor asks: ' : ''}${message['content']?.toString() ?? ''}',
                style: TextStyle(
                    color: patient ? Colors.white : _ink, height: 1.4))));
  }

  Widget _emergencyCard() => Container(
      margin: const EdgeInsets.all(12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
          color: const Color(0xFFFFEEEE),
          borderRadius: BorderRadius.circular(12)),
      child: const Text(
          'Possible emergency symptoms were mentioned. Please contact local emergency services or seek urgent care now; do not wait for this consultation.',
          style: TextStyle(
              color: Color(0xFF9F1D20),
              fontWeight: FontWeight.w700,
              height: 1.35)));
}
