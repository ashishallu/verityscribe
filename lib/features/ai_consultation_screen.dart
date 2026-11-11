import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_sound/flutter_sound.dart';
import 'package:go_router/go_router.dart';
import 'package:record/record.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../core/constants/endpoints.dart';
import '../core/services/api_service.dart';
import '../models/entities.dart';
import '../providers/app_providers.dart';
import 'consultation_finalization_state.dart';
import 'voice_handshake_error.dart';

const _blue = Color(0xFF2458E6);
const _ink = Color(0xFF192B4D);

class AIAppointmentEntryScreen extends StatefulWidget {
  const AIAppointmentEntryScreen({required this.appointment, super.key});
  final LiveAppointment appointment;

  @override
  State<AIAppointmentEntryScreen> createState() =>
      _AIAppointmentEntryScreenState();
}

class _AIAppointmentEntryScreenState extends State<AIAppointmentEntryScreen> {
  Timer? _expiryTicker;

  @override
  void initState() {
    super.initState();
    _expiryTicker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _expiryTicker?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final appointment = widget.appointment;
    final virtual = appointment.consultationType == 'ai_virtual';
    final remaining = appointment.timeRemaining;
    final expired = appointment.isExpired;
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
                      const SizedBox(height: 8),
                      Text(
                        remaining == null
                            ? 'Appointment expiry could not be verified. Refresh your appointments.'
                            : expired
                                ? 'This appointment expired 30 minutes after its scheduled start.'
                                : 'Time remaining: ${remaining.inMinutes.toString().padLeft(2, '0')}:${(remaining.inSeconds % 60).toString().padLeft(2, '0')}',
                        style: TextStyle(
                          color: expired ? Colors.red.shade700 : accent,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
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
                            onPressed: expired
                                ? null
                                : () => context.push(
                                    '/ai-consultation/${appointment.id}',
                                    extra: appointment),
                            icon: Icon(virtual
                                ? Icons.graphic_eq_rounded
                                : Icons.chat_bubble_outline_rounded),
                            label: Text(expired
                                ? 'Appointment expired'
                                : 'Start consultation'),
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
  final _player = FlutterSoundPlayer();
  final _scroll = ScrollController();
  final List<Map<String, dynamic>> _messages = [];
  final List<Uint8List> _turnAudioChunks = [];
  Timer? _clock;
  Timer? _playbackCompletionTimer;
  Timer? _completionTimer;
  Timer? _finalizationPollTimer;
  Completer<bool>? _voiceSaveAck;
  Completer<void>? _voiceReadyAck;
  Future<bool>? _voiceStopFuture;
  StreamSubscription<Uint8List>? _audioSubscription;
  StreamSubscription<dynamic>? _voiceSubscription;
  WebSocketChannel? _voiceChannel;
  DateTime? _startedAt;
  String? _sessionId;
  String _sessionStatus = 'unknown';
  Stopwatch? _voiceLatency;
  final Set<String> _voiceLatencyMilestones = {};
  String _liveTranscript = '';
  String? _liveCaptionTurnId;
  final Set<String> _discardedCaptionTurns = {};
  String? _pendingChatKey,
      _pendingChatText,
      _pendingVoiceKey,
      _pendingVoiceText;
  int _requestCounter = 0;
  int _voiceInterruptionCount = 0;
  int _voiceLifecycleGeneration = 0;
  final Map<String, int> _voiceTurnInterruptions = {};
  String? _error;
  String _captionSpeaker = 'VERITY';
  bool _showCaptions = true;
  String? _liveServerStartupError;
  Map<String, dynamic>? _summary;
  bool _consented = false, _loading = false, _recording = false;
  bool _speaking = false, _ended = false, _emergency = false;
  bool _playerStreaming = false, _stoppingVoice = false, _endingCall = false;
  bool _voiceSessionReady = false;
  bool _voiceCancelledBeforeReady = false;
  bool _recoveringVoice = false;
  bool _voiceStarting = false;
  bool _voiceAuthorizationRejected = false;
  bool _endRequested = false;
  bool _finalizationPollInFlight = false;
  bool _finalizationPollExhausted = false;
  int _finalizationPollAttempts = 0;
  int _voiceReconnectAttempts = 0;
  int _playbackGeneration = 0;
  Future<void> _playbackTail = Future<void>.value();

  bool get _virtual => widget.appointment.consultationType == 'ai_virtual';

  ConsultationFinalizationState get _finalizationState {
    final sessionId = _sessionId;
    if (sessionId == null) return ConsultationFinalizationState.unknown;
    return consultationFinalizationState({
      'id': sessionId,
      'status': _sessionStatus,
      if (_summary != null) 'summary': _summary,
    }, expectedSessionId: sessionId, isVirtual: _virtual);
  }

  void _recordVoiceLatency(String milestone) {
    if (!_voiceLatencyMilestones.add(milestone)) return;
    debugPrint(
        'AI Virtual latency stage=$milestone elapsed_ms=${_voiceLatency?.elapsedMilliseconds ?? -1}');
  }

  String _newRequestKey() =>
      '${DateTime.now().microsecondsSinceEpoch}-${++_requestCounter}';

  @override
  void initState() {
    super.initState();
  }

  @override
  void dispose() {
    _clock?.cancel();
    _playbackCompletionTimer?.cancel();
    _completionTimer?.cancel();
    _finalizationPollTimer?.cancel();
    _input.dispose();
    _scroll.dispose();
    unawaited(_disposeVoiceResources());
    super.dispose();
  }

  Future<void> _disposeVoiceResources() async {
    await _stopLiveVoice();
    await _recorder.dispose();
    if (_player.isOpen()) await _player.closePlayer();
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
    if (_virtual &&
        _messages.length == 1 &&
        _messages.first['role'] == 'assistant') {
      // The API stores a generic starter line for both modes. AI Virtual's
      // actual greeting is generated and spoken by Gemini Live after connect.
      _messages.clear();
    }
    _sessionStatus = session['status']?.toString() ?? 'unknown';
    if (_sessionStatus != 'in_progress') _endRequested = true;
    _summary = session['summary'] is Map ? _body(session['summary']) : null;
    _error = null;
    _ended = {
      ConsultationFinalizationState.completed,
      ConsultationFinalizationState.incomplete,
    }.contains(_finalizationState);
    if (_finalizationState == ConsultationFinalizationState.failed) {
      _error =
          'Finalization failed. Your conversation is preserved; retry to prepare the doctor-review draft.';
    } else if (_finalizationState == ConsultationFinalizationState.processing) {
      _error =
          'Your consultation is still being finalized. Please retry shortly.';
    } else if (_finalizationState == ConsultationFinalizationState.unknown &&
        _sessionStatus != 'in_progress') {
      _error =
          'The server could not confirm a saved doctor-review summary. Retry finalization to continue.';
    }
    _syncFinalizationPolling();
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

  void _syncFinalizationPolling() {
    if (!_virtual ||
        !_endRequested ||
        _finalizationState != ConsultationFinalizationState.processing) {
      _finalizationPollTimer?.cancel();
      _finalizationPollTimer = null;
      _finalizationPollAttempts = 0;
      _finalizationPollExhausted = false;
      return;
    }
    if (_finalizationPollTimer != null || _finalizationPollExhausted) return;
    _finalizationPollTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      if (_finalizationPollAttempts >= 20) {
        _finalizationPollTimer?.cancel();
        _finalizationPollTimer = null;
        if (mounted) setState(() => _finalizationPollExhausted = true);
        return;
      }
      unawaited(_refreshFinalization());
    });
  }

  Future<void> _refreshFinalization({bool manual = false}) async {
    if (_sessionId == null || _finalizationPollInFlight) return;
    if (manual) {
      _finalizationPollAttempts = 0;
      _finalizationPollExhausted = false;
      _finalizationPollTimer ??= Timer.periodic(
        const Duration(seconds: 2),
        (_) => unawaited(_refreshFinalization()),
      );
    }
    _finalizationPollInFlight = true;
    _finalizationPollAttempts++;
    try {
      final response = await ref
          .read(apiClientProvider)
          .get<dynamic>('/ai-consultations/$_sessionId');
      if (mounted) setState(() => _loadSession(_data(response.data)));
    } catch (_) {
      // Keep the bounded poll alive; the status page remains truthful and
      // offers a manual check if the API stays unavailable.
    } finally {
      _finalizationPollInFlight = false;
    }
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
      // Open the audio output directly from the Start button gesture so web
      // browsers can initialize playback before Gemini's first audio packet.
      if (_virtual) await _startLivePlayerStream();
      final response = await ref.read(apiClientProvider).post<dynamic>(
        '/ai-consultations/appointments/${widget.appointment.id}/start',
        {
          'consent': true,
          'voice_consent': _virtual,
          'similar_case_consent': true,
        },
      );
      if (mounted) setState(() => _loadSession(_data(response.data)));
      // Starting an appointment can return its existing durable session.
      // Only an active session may receive a voice ticket; trying to reconnect
      // a failed/finalizing/reviewed session produces a 409 that used to be
      // misreported as a microphone-permission failure.
      if (_virtual &&
          _sessionStatus == 'in_progress' &&
          !_ended &&
          _finalizationState == ConsultationFinalizationState.inProgress) {
        await _beginLiveVoice();
      }
    } catch (_) {
      if (_virtual) await _stopLivePlayback();
      setState(() => _error =
          'Unable to connect to the consultation. Check your connection and retry.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _ensureLivePlayer() async {
    if (!_player.isOpen()) await _player.openPlayer();
  }

  Future<void> _startLivePlayerStream() async {
    await _ensureLivePlayer();
    if (_playerStreaming) return;
    await _player.startPlayerFromStream(
      codec: Codec.pcm16,
      interleaved: true,
      numChannels: 1,
      sampleRate: 24000,
      bufferSize: 8192,
    );
    _playerStreaming = true;
  }

  void _queueLiveAudio(Uint8List bytes) {
    if (bytes.isEmpty) return;
    _recordVoiceLatency('first_gemini_audio_chunk_received');
    final copy = Uint8List.fromList(bytes);
    _turnAudioChunks.add(copy);
    final generation = _playbackGeneration;
    _playbackTail = _playbackTail.then((_) async {
      if (generation != _playbackGeneration) return;
      await _startLivePlayerStream();
      if (generation != _playbackGeneration) return;
      await _player.feedUint8FromStream(copy);
      _recordVoiceLatency('first_audio_chunk_played');
      if (mounted && !_speaking) setState(() => _speaking = true);
    }).catchError((_) {
      if (mounted) {
        setState(() {
          _speaking = false;
          _error = 'Gemini audio playback could not start on this device.';
        });
      }
    });
  }

  void _finishLiveAudioTurn() {
    final turnBytes =
        _turnAudioChunks.fold<int>(0, (total, chunk) => total + chunk.length);
    _turnAudioChunks.clear();
    _playbackCompletionTimer?.cancel();
    final generation = _playbackGeneration;
    unawaited(_playbackTail.then((_) async {
      await Future<void>.delayed(Duration(
        milliseconds: (turnBytes / 48).ceil() + 250,
      ));
      if (generation != _playbackGeneration) return;
      await _stopLivePlayback();
    }));
  }

  Future<void> _stopLivePlayback() async {
    _playbackGeneration++;
    _playbackCompletionTimer?.cancel();
    try {
      if (_player.isOpen() && _playerStreaming) await _player.stopPlayer();
    } catch (_) {}
    _playerStreaming = false;
    if (mounted && _speaking) setState(() => _speaking = false);
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
    if (_sessionId == null ||
        _voiceChannel != null ||
        _voiceStarting ||
        _voiceSessionReady ||
        _ended) return;
    if (_sessionStatus != 'in_progress' ||
        _finalizationState != ConsultationFinalizationState.inProgress) {
      if (mounted && _error == null) {
        setState(() {
          _error = switch (_finalizationState) {
            ConsultationFinalizationState.failed =>
              'This consultation needs finalization. Retry finalization before starting another voice session.',
            ConsultationFinalizationState.processing =>
              'This consultation is still being saved. Retry in a moment.',
            ConsultationFinalizationState.completed ||
            ConsultationFinalizationState.incomplete =>
              'This consultation is already saved for doctor review. Start a new appointment to begin another session.',
            _ => 'The server has not confirmed an active consultation session.',
          };
        });
      }
      return;
    }
    _voiceStarting = true;
    _voiceAuthorizationRejected = false;
    _voiceLatency = Stopwatch()..start();
    _voiceLatencyMilestones.clear();
    final generation = ++_voiceLifecycleGeneration;
    _voiceSessionReady = false;
    _voiceCancelledBeforeReady = false;
    _liveServerStartupError = null;
    if (mounted) setState(() => _error = null);
    try {
      final microphoneGranted = await _recorder.hasPermission();
      if (generation != _voiceLifecycleGeneration || _stoppingVoice || _ended) {
        return;
      }
      if (!microphoneGranted) {
        setState(
            () => _error = 'Microphone permission is needed for AI Virtual.');
        return;
      }
      final ticketResponse = await ref.read(apiClientProvider).post<dynamic>(
          '/ai-consultations/$_sessionId/voice-ticket', const {});
      if (generation != _voiceLifecycleGeneration || _stoppingVoice || _ended) {
        return;
      }
      final ticket = _data(ticketResponse.data)['ticket'].toString();
      if (ticket.isEmpty || ticket == 'null') {
        throw StateError('Voice ticket missing');
      }
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
      _voiceReadyAck = Completer<void>();
      _voiceSubscription = channel.stream.listen(
        _handleLiveVoiceEvent,
        onError: (Object error) {
          if (!(_voiceReadyAck?.isCompleted ?? true)) {
            if (isDefinitiveVoiceAuthorizationFailure(error)) {
              _voiceAuthorizationRejected = true;
              _liveServerStartupError = voiceHandshakeFailureMessage(error);
            }
            _voiceReadyAck?.completeError(error);
            return;
          }
          if (mounted && !_stoppingVoice) {
            setState(() {
              _recording = false;
              _error =
                  'The live voice connection was interrupted. Reconnecting…';
            });
          }
          unawaited(_recoverLiveVoice());
        },
        onDone: () {
          if (!(_voiceReadyAck?.isCompleted ?? true)) {
            _voiceReadyAck?.completeError(
                StateError('Gemini Live closed before startup completed'));
            return;
          }
          if (!_ended && !_stoppingVoice) {
            if (mounted) {
              setState(() {
                _recording = false;
                _speaking = false;
                _error = 'The live voice connection closed. Reconnecting…';
              });
            }
            unawaited(_recoverLiveVoice());
          }
        },
      );
      // The transport handshake is not enough: wait until FastAPI has observed
      // Gemini's first Live event before opening the microphone stream.
      await Future.wait<void>([
        channel.ready.timeout(const Duration(seconds: 15)),
        // The ADK stack may take over a minute to import on a cold local or
        // free-tier instance. A short timeout made a healthy, permission-
        // granted call cancel itself before Gemini could produce its first
        // event. The backend now warms ADK at process startup; keep this
        // bounded fallback for a request racing that warm-up.
        _voiceReadyAck!.future.timeout(const Duration(seconds: 150)),
      ]);
      _recordVoiceLatency('gemini_first_event');
      _voiceReadyAck = null;
      if (generation != _voiceLifecycleGeneration ||
          _voiceCancelledBeforeReady ||
          _stoppingVoice ||
          _ended ||
          _voiceChannel != channel) {
        return;
      }
      _voiceSessionReady = true;
      _voiceReconnectAttempts = 0;
      final audio = await _recorder.startStream(const RecordConfig(
        encoder: AudioEncoder.pcm16bits,
        sampleRate: 16000,
        numChannels: 1,
        echoCancel: true,
        noiseSuppress: true,
        autoGain: true,
        // record_web measures this in target-rate samples. 640 samples at
        // 16 kHz is 40 ms (rather than the previous 256 ms), keeping speech
        // input responsive enough for Gemini Live's VAD and turn detection.
        streamBufferSize: 640,
      ));
      if (generation != _voiceLifecycleGeneration ||
          _stoppingVoice ||
          _ended ||
          _voiceChannel != channel) {
        await _recorder.stop();
        return;
      }
      _audioSubscription = audio.listen(
        (chunk) {
          _recordVoiceLatency('first_microphone_chunk_captured');
          if (_recording && _voiceChannel == channel) {
            _recordVoiceLatency('first_microphone_chunk_transmitted');
            channel.sink.add(chunk);
          }
        },
        onError: (_) {
          if (mounted) {
            setState(() => _error = 'Microphone audio could not be streamed.');
          }
        },
      );
      if (mounted) {
        setState(() {
          _recording = true;
          _error = null;
        });
      }
    } catch (error) {
      _voiceSessionReady = false;
      _voiceReadyAck = null;
      await _audioSubscription?.cancel();
      _audioSubscription = null;
      try {
        await _recorder.stop();
      } catch (_) {}
      await _voiceSubscription?.cancel();
      _voiceSubscription = null;
      try {
        await _voiceChannel?.sink.close();
      } catch (_) {}
      _voiceChannel = null;
      if (mounted &&
          generation == _voiceLifecycleGeneration &&
          !_voiceCancelledBeforeReady &&
          !_stoppingVoice &&
          !_ended) {
        setState(() {
          _recording = false;
          _error = _liveServerStartupError ??
              (error is ApiException &&
                      (error.statusCode == 401 || error.statusCode == 403)
                  ? 'The voice session could not be authorized. Sign in again and retry.'
                  : voiceHandshakeFailureMessage(error));
        });
      }
      _liveServerStartupError = null;
    } finally {
      _voiceStarting = false;
    }
  }

  void _handleLiveVoiceEvent(dynamic raw) {
    if (raw is Uint8List) {
      _queueLiveAudio(raw);
      return;
    }
    if (raw is List<int>) {
      _queueLiveAudio(Uint8List.fromList(raw));
      return;
    }
    if (raw is! String) return;
    try {
      final event = Map<String, dynamic>.from(jsonDecode(raw) as Map);
      switch (event['type']) {
        case 'ready':
          if (!(_voiceReadyAck?.isCompleted ?? true)) {
            _voiceReadyAck?.complete();
          }
          break;
        case 'transcript':
          if (mounted) {
            setState(() => _liveTranscript = event['text']?.toString() ?? '');
          }
          break;
        case 'caption':
          final turnId = event['turn_id']?.toString();
          if (turnId != null && _discardedCaptionTurns.contains(turnId)) break;
          if (turnId != null && turnId != _liveCaptionTurnId) {
            _liveCaptionTurnId = turnId;
            _liveTranscript = '';
          }
          if (event['role'] == 'patient' && mounted) {
            if (_speaking) unawaited(_stopLivePlayback());
            _turnAudioChunks.clear();
            setState(() {
              _speaking = false;
              if (_captionSpeaker != 'YOU') _liveTranscript = '';
              _captionSpeaker = 'YOU';
              _liveTranscript += event['text']?.toString() ?? '';
            });
          } else if (event['role'] == 'assistant' && mounted) {
            setState(() {
              _speaking = true;
              if (_captionSpeaker != 'VERITY') _liveTranscript = '';
              _captionSpeaker = 'VERITY';
              _liveTranscript += event['text']?.toString() ?? '';
            });
          }
          break;
        case 'interrupted':
          final interruptedTurn = event['turn_id']?.toString();
          if (interruptedTurn != null) {
            _discardedCaptionTurns.add(interruptedTurn);
          }
          _liveCaptionTurnId = null;
          _turnAudioChunks.clear();
          unawaited(_stopLivePlayback());
          if (mounted) {
            setState(() {
              _speaking = false;
              _liveTranscript = '';
            });
          }
          break;
        case 'turn_complete':
          final key = event['request_key']?.toString();
          if (key != null) {
            _discardedCaptionTurns.add(key);
            if (_liveCaptionTurnId == key) _liveCaptionTurnId = null;
          }
          final input = event['input_text']?.toString().trim() ?? '';
          final output = event['output_text']?.toString().trim() ?? '';
          final closingAudioMs = _turnAudioChunks.fold<int>(
                0,
                (total, chunk) => total + chunk.length,
              ) ~/
              48;
          if (mounted) {
            setState(() {
              if (key != null) {
                _messages
                    .removeWhere((message) => message['request_key'] == key);
              }
              if (input.isNotEmpty) {
                _messages.add({
                  'role': 'patient',
                  'content': input,
                  if (key != null) 'request_key': key,
                });
              }
              if (output.isNotEmpty) {
                _messages.add({
                  'role': 'assistant',
                  'content': output,
                  if (key != null) 'request_key': key,
                });
              }
              _emergency = event['emergency_warning'] == true;
              _loading = false;
              _liveTranscript = '';
            });
          }
          if (key != null && _pendingChatKey == key) {
            _pendingChatKey = null;
            _pendingChatText = null;
          }
          _finishLiveAudioTurn();
          if (event['consultation_complete'] == true) {
            // The agent only sets this after the patient confirms the recap.
            // Let its native-audio closing finish, then persist the final draft.
            _completionTimer?.cancel();
            _completionTimer = Timer(
              Duration(milliseconds: closingAudioMs + 900),
              () => unawaited(_end()),
            );
          }
          break;
        case 'session_saved':
          if (!(_voiceSaveAck?.isCompleted ?? true)) {
            _voiceSaveAck?.complete(true);
          }
          break;
        case 'speech_started':
          _voiceInterruptionCount++;
          _turnAudioChunks.clear();
          if (_speaking) unawaited(_stopLivePlayback());
          if (mounted) setState(() => _speaking = false);
          break;
        case 'turn_started':
          final key = event['request_key']?.toString();
          _pendingVoiceKey = key;
          _pendingVoiceText = event['transcript']?.toString();
          if (key != null) {
            _voiceTurnInterruptions[key] = _voiceInterruptionCount;
          }
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
          if (wasInterrupted) _turnAudioChunks.clear();
          break;
        case 'turn_error':
          final key = event['request_key']?.toString();
          _pendingVoiceKey = key;
          _pendingVoiceText = event['transcript']?.toString();
          if (key != null) _voiceTurnInterruptions.remove(key);
          if (mounted) {
            setState(() => _error = event['message']?.toString() ??
                'Voice turn failed. Retry the saved transcript.');
          }
          break;
        case 'error':
          if (!(_voiceReadyAck?.isCompleted ?? true)) {
            _liveServerStartupError = event['message']?.toString() ??
                'Gemini Live is unavailable. Please retry.';
            _voiceReadyAck!.completeError(StateError(_liveServerStartupError!));
          }
          final connectedCallFailed = _voiceSessionReady &&
              !_endingCall &&
              !_ended &&
              _sessionId != null;
          if (mounted) {
            setState(() {
              _error = connectedCallFailed
                  ? null
                  : event['message']?.toString() ??
                      'The live voice connection ended unexpectedly.';
              _recording = false;
              _speaking = false;
            });
          }
          if (connectedCallFailed) {
            // A transient provider/socket close is not End Call. Keep the
            // durable consultation active and reconnect; explicit patient
            // hang-up or Gemini's confirmed completion alone finalizes it.
            unawaited(_recoverLiveVoice());
          } else if (_voiceSessionReady) {
            unawaited(_stopLiveVoice());
          }
          break;
      }
    } catch (_) {
      if (mounted) {
        setState(() =>
            _error = 'The live voice service returned an invalid response.');
      }
    }
  }

  Future<void> _toggleLiveMicrophone() async {
    if (_voiceChannel == null) {
      _voiceReconnectAttempts = 0;
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
      if (mounted) {
        setState(() => _error = 'The microphone could not be toggled.');
      }
    }
  }

  Future<void> _recoverLiveVoice() async {
    if (_recoveringVoice ||
        _voiceAuthorizationRejected ||
        _endingCall ||
        _ended ||
        _sessionId == null) return;
    if (_voiceReconnectAttempts >= 2) {
      if (mounted) {
        setState(() {
          _recording = false;
          _speaking = false;
          _error =
              'The voice connection could not recover. Your saved conversation is safe; tap the microphone to retry or end the call.';
        });
      }
      await _stopLiveVoice();
      return;
    }

    _recoveringVoice = true;
    _voiceReconnectAttempts++;
    if (mounted) {
      setState(() {
        _recording = false;
        _speaking = false;
        _error = 'Voice connection lost. Reconnecting…';
      });
    }
    try {
      await _stopLiveVoice();
      if (_endingCall || _ended || !mounted) return;
      await Future<void>.delayed(
          Duration(milliseconds: 350 * _voiceReconnectAttempts));
      if (_endingCall || _ended || !mounted) return;
      await _beginLiveVoice();
    } finally {
      _recoveringVoice = false;
    }
  }

  Future<bool> _stopLiveVoice() {
    if (_stoppingVoice) return _voiceStopFuture ?? Future<bool>.value(false);
    _stoppingVoice = true;
    _voiceLifecycleGeneration++;
    final stopping = _stopLiveVoiceInternal();
    _voiceStopFuture = stopping;
    return stopping;
  }

  Future<bool> _stopLiveVoiceInternal() async {
    var persisted = false;
    _playbackCompletionTimer?.cancel();
    _completionTimer?.cancel();
    _turnAudioChunks.clear();
    // Hang Up must reflect immediately in the UI, even while we wait briefly
    // for the backend's transcript-save acknowledgement.
    if (mounted) {
      setState(() {
        _recording = false;
        _speaking = false;
      });
    }
    final ready = _voiceSessionReady;
    if (!ready && !(_voiceReadyAck?.isCompleted ?? true)) {
      _voiceCancelledBeforeReady = true;
      // Release _beginLiveVoice's handshake wait if the patient hangs up
      // while Gemini is still initializing.
      _voiceReadyAck!.completeError(StateError('Call ended during startup'));
    }
    try {
      await _audioSubscription?.cancel().timeout(const Duration(seconds: 1));
    } catch (_) {}
    _audioSubscription = null;
    try {
      await _recorder.stop().timeout(const Duration(seconds: 2));
    } catch (_) {}
    try {
      await _stopLivePlayback().timeout(const Duration(seconds: 2));
    } catch (_) {}
    final channel = _voiceChannel;
    if (channel != null) {
      if (ready) {
        _voiceSaveAck = Completer<bool>();
        try {
          channel.sink.add(jsonEncode({'type': 'end'}));
          persisted = await _voiceSaveAck!.future.timeout(
            const Duration(seconds: 3),
          );
        } catch (_) {
          // Closing the socket still proceeds; already committed turns remain
          // durable and the UI must never leave capture/playback active.
          persisted = false;
        }
      }
      try {
        await channel.sink.close().timeout(const Duration(seconds: 1));
      } catch (_) {}
    }
    try {
      await _voiceSubscription?.cancel().timeout(const Duration(seconds: 1));
    } catch (_) {}
    _voiceSubscription = null;
    _voiceChannel = null;
    _voiceSaveAck = null;
    _voiceSessionReady = false;
    _stoppingVoice = false;
    _voiceStopFuture = null;
    if (mounted) setState(() => _recording = false);
    return persisted;
  }

  Future<void> _retryPendingVoice() async {
    final key = _pendingVoiceKey;
    final text = _pendingVoiceText;
    if (key == null || text == null || _sessionId == null || _loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    if (_virtual) {
      try {
        if (_voiceChannel == null) await _beginLiveVoice();
        final channel = _voiceChannel;
        if (channel == null) throw StateError('Gemini Live is disconnected');
        channel.sink.add(jsonEncode({
          'type': 'text',
          'text': text,
          'request_key': key,
        }));
        _pendingVoiceKey = null;
        _pendingVoiceText = null;
      } catch (_) {
        if (mounted) {
          setState(() => _error =
              'Gemini Live is not connected. Reconnect before retrying the saved transcript.');
        }
      } finally {
        if (mounted) setState(() => _loading = false);
      }
      return;
    }
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
    } catch (_) {
      setState(() => _error =
          'The saved voice turn could not be retried. Your transcript remains saved.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _end() async {
    if (_sessionId == null || _ended || _endingCall) return;
    _endingCall = true;
    _endRequested = true;
    if (mounted) {
      setState(() {
        _loading = true;
        _recording = false;
        _speaking = false;
        _error = null;
      });
    }
    // Always release local audio/socket resources first. A missing WebSocket
    // acknowledgement must never trap the patient in an active call screen;
    // the backend finalizes the durable transcript (or records an empty,
    // explicitly incomplete session) after the bounded socket close.
    if (_virtual) await _stopLiveVoice();
    try {
      final response = await ref
          .read(apiClientProvider)
          .post<dynamic>('/ai-consultations/$_sessionId/end', const {});
      if (mounted) setState(() => _loadSession(_data(response.data)));
    } catch (_) {
      // The POST can time out after the backend committed the draft. Read the
      // durable session before asking the patient to retry finalization.
      try {
        final recovered = await ref
            .read(apiClientProvider)
            .get<dynamic>('/ai-consultations/$_sessionId');
        final data = _data(recovered.data);
        if (mounted) setState(() => _loadSession(data));
        if (mounted &&
            _finalizationState == ConsultationFinalizationState.unknown) {
          setState(() => _error =
              'The server returned an unrecognized finalization state. Your conversation is preserved; retry finalization or contact your care team.');
        }
      } catch (_) {
        if (mounted) {
          setState(() => _error =
              'The conversation is preserved. The server could not confirm finalization; retry when connected.');
        }
      }
    } finally {
      _endingCall = false;
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
                    'AI Virtual streams microphone audio to Gemini Live for real-time conversation. Raw audio is not saved. Your transcript and conversation are securely shared with your assigned doctor.',
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

    if (_ended &&
        const {
          ConsultationFinalizationState.completed,
          ConsultationFinalizationState.incomplete,
        }.contains(_finalizationState)) {
      final incomplete =
          _finalizationState == ConsultationFinalizationState.incomplete;
      final summaryFallback =
          _summary?['summary_generation_status'] == 'failed';
      final interviewEndedEarly = _summary?['interview_state'] == 'ended_early';
      return Scaffold(
        appBar: AppBar(title: Text(title)),
        body: ListView(padding: const EdgeInsets.all(20), children: [
          Icon(
              incomplete
                  ? Icons.hourglass_top_rounded
                  : Icons.check_circle_outline_rounded,
              size: 46,
              color: _blue),
          const SizedBox(height: 12),
          const Text('Thank you for your time.',
              style: TextStyle(fontSize: 23, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(
              incomplete
                  ? summaryFallback
                      ? 'Your conversation is saved as a draft for your doctor to review.'
                      : interviewEndedEarly
                          ? 'Your interview ended before all questions were completed. The summary below is saved for your doctor to review.'
                          : 'Your available answers are saved as a draft for your doctor to review.'
                  : 'Your interview is complete. The summary below has been sent securely to your doctor for review.',
              style: TextStyle(fontSize: 16, height: 1.4, color: _ink)),
          const SizedBox(height: 16),
          if (_summary != null)
            Card(
                child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Summary for your doctor',
                              style: TextStyle(
                                  fontSize: 18, fontWeight: FontWeight.w700)),
                          const SizedBox(height: 10),
                          Text(
                              (_summary!['clinical_summary'] ??
                                      'Your conversation was saved. A concise summary is not available yet; your doctor can review the recorded details.')
                                  .toString(),
                              style: const TextStyle(height: 1.5)),
                          if ((_summary!['uncertainties'] as List? ?? const [])
                              .isNotEmpty) ...[
                            const SizedBox(height: 14),
                            const Text('To clarify with your doctor',
                                style: TextStyle(fontWeight: FontWeight.w700)),
                            const SizedBox(height: 4),
                            ...(_summary!['uncertainties'] as List)
                                .where((item) => item != null)
                                .take(4)
                                .map((item) => Padding(
                                      padding: const EdgeInsets.only(top: 3),
                                      child: Text('• ${item.toString()}'),
                                    )),
                          ],
                          const SizedBox(height: 14),
                          Text(
                            incomplete
                                ? 'Saved as an incomplete draft. Your doctor will review it before it becomes part of your medical record.'
                                : 'Sent securely to your doctor. It remains a draft until your doctor reviews it.',
                            style: const TextStyle(
                                color: Color(0xFF626786), height: 1.4),
                          ),
                        ]))),
          OutlinedButton.icon(
              onPressed: () => context.go('/appointments'),
              icon: const Icon(Icons.calendar_month),
              label: const Text('Back to appointments')),
        ]),
      );
    }

    // Once the patient ends a voice call, keep them out of the live-call
    // controls while the backend finalizes. The persisted backend state is
    // polled briefly and remains the only authority for showing success.
    if (_virtual && _endRequested && !_ended) {
      return _finalizationStatusPage(title);
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

  Widget _finalizationStatusPage(String title) {
    final state = _finalizationState;
    final isProcessing = _endingCall ||
        _loading ||
        state == ConsultationFinalizationState.processing;
    final isFailed = state == ConsultationFinalizationState.failed;
    final isPending = state == ConsultationFinalizationState.processing;
    final message = isProcessing
        ? 'Your call has ended. We’re saving the conversation and preparing the doctor-review draft.'
        : isFailed
            ? 'The conversation is preserved, but its doctor-review draft could not be saved yet.'
            : 'The server has not confirmed a saved doctor-review draft. Your conversation remains available for recovery.';

    return Scaffold(
      backgroundColor: const Color(0xFFF5F6FF),
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (isProcessing)
                  const SizedBox(
                    width: 34,
                    height: 34,
                    child: CircularProgressIndicator(strokeWidth: 3),
                  )
                else
                  Icon(
                    isFailed ? Icons.sync_problem_rounded : Icons.info_outline,
                    size: 42,
                    color: isFailed ? Colors.deepOrange : _blue,
                  ),
                const SizedBox(height: 18),
                Text(
                  isProcessing
                      ? 'Saving your consultation'
                      : isFailed
                          ? 'Finalization needs a retry'
                          : 'We couldn’t confirm finalization',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    color: _ink,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(height: 1.5, color: Color(0xFF626786)),
                ),
                if (isPending && _finalizationPollExhausted) ...[
                  const SizedBox(height: 8),
                  const Text(
                    'This is taking longer than expected. You can check again; success will appear only after the saved summary is confirmed.',
                    textAlign: TextAlign.center,
                    style: TextStyle(height: 1.45, color: Color(0xFF626786)),
                  ),
                ],
                if (!isProcessing ||
                    (isPending && _finalizationPollExhausted)) ...[
                  const SizedBox(height: 22),
                  FilledButton.icon(
                    onPressed: _loading || _endingCall
                        ? null
                        : isPending
                            ? () => _refreshFinalization(manual: true)
                            : _end,
                    icon: Icon(isPending
                        ? Icons.refresh_rounded
                        : Icons.replay_rounded),
                    label:
                        Text(isPending ? 'Check status' : 'Retry finalization'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
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

  Widget _voiceBody() => Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                Color(0xFFE4E7FF),
                Color(0xFFD9E3FF),
                Color(0xFFECEBFF)
              ]),
        ),
        child: SafeArea(child: LayoutBuilder(builder: (context, constraints) {
          final orbSize = (constraints.maxHeight * .38).clamp(190.0, 330.0);
          return Column(children: [
            if (_emergency) _emergencyCard(),
            Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                child: Row(children: [
                  IconButton(
                      onPressed: () => context.pop(),
                      icon: const Icon(Icons.arrow_back_ios_new_rounded)),
                  const Expanded(
                      child: Center(
                          child: Text('Voice chat',
                              style: TextStyle(
                                  fontSize: 18, fontWeight: FontWeight.w600)))),
                  Text(_duration, style: const TextStyle(fontFeatures: [])),
                  PopupMenuButton<String>(
                      icon: const Icon(Icons.more_vert),
                      itemBuilder: (_) => const [
                            PopupMenuItem(
                                value: 'end', child: Text('End consultation'))
                          ],
                      onSelected: (_) => _end()),
                ])),
            const Spacer(),
            AnimatedSwitcher(
                duration: const Duration(milliseconds: 220),
                child: Text(
                    _endingCall
                        ? 'Ending call…'
                        : _loading
                            ? 'Processing…'
                            : _finalizationState ==
                                    ConsultationFinalizationState.failed
                                ? 'Draft could not be saved'
                                : _finalizationState ==
                                        ConsultationFinalizationState.processing
                                    ? 'Saving consultation…'
                                    : _voiceSessionReady
                                        ? _speaking
                                            ? 'Verity is speaking…'
                                            : _recording
                                                ? 'Listening…'
                                                : 'Connected'
                                        : _error != null
                                            ? 'Voice connection needs attention'
                                            : 'Connecting…',
                    key: ValueKey(
                        '$_endingCall-$_loading-$_speaking-$_recording'),
                    style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                        fontStyle: FontStyle.italic,
                        color: Color(0xFF555A91)))),
            const SizedBox(height: 24),
            _IridescentVoiceOrb(
                size: orbSize, speaking: _speaking, listening: _recording),
            const SizedBox(height: 24),
            SizedBox(
              height: 74,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 28),
                child: Center(
                  child: _showCaptions && _liveTranscript.isNotEmpty
                      ? Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(_captionSpeaker,
                                style: const TextStyle(
                                  fontSize: 11,
                                  letterSpacing: 2,
                                  fontWeight: FontWeight.w700,
                                  color: Color(0xFF7176A2),
                                )),
                            const SizedBox(height: 5),
                            Text(_liveTranscript,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                    fontSize: 16,
                                    height: 1.35,
                                    color: Color(0xFF454968))),
                          ],
                        )
                      : const Text(
                          'Speak your thoughts freely and get personalized guidance',
                          textAlign: TextAlign.center,
                          style:
                              TextStyle(color: Color(0xFF626786), height: 1.4),
                        ),
                ),
              ),
            ),
            const Spacer(),
            if (_error != null)
              Padding(
                  padding: const EdgeInsets.all(8),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(_error!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Colors.redAccent)),
                    if (_pendingVoiceKey != null)
                      TextButton.icon(
                          onPressed: _endingCall || _loading
                              ? null
                              : _retryPendingVoice,
                          icon: const Icon(Icons.replay_rounded),
                          label: const Text('Retry saved turn')),
                    if (_finalizationState ==
                            ConsultationFinalizationState.failed ||
                        _finalizationState ==
                            ConsultationFinalizationState.processing ||
                        _finalizationState ==
                            ConsultationFinalizationState.unknown)
                      TextButton.icon(
                          onPressed: _endingCall || _loading ? null : _end,
                          icon: const Icon(Icons.refresh_rounded),
                          label: const Text('Retry finalization')),
                  ])),
            Padding(
                padding: const EdgeInsets.fromLTRB(28, 12, 28, 18),
                child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      _glassControl(
                          icon: _showCaptions
                              ? Icons.closed_caption_rounded
                              : Icons.closed_caption_off_rounded,
                          label: 'Captions',
                          onTap: () =>
                              setState(() => _showCaptions = !_showCaptions)),
                      _glassControl(
                          icon: !_recording
                              ? Icons.mic_off_rounded
                              : Icons.mic_rounded,
                          label: _recording
                              ? 'Mute microphone'
                              : 'Unmute microphone',
                          onTap: _toggleLiveMicrophone,
                          primary: true),
                      _glassControl(
                          icon: Icons.close_rounded,
                          label: 'End call',
                          onTap: _endingCall ? null : _end,
                          danger: true),
                    ])),
          ]);
        })),
      );

  Widget _glassControl(
          {required IconData icon,
          required String label,
          required VoidCallback? onTap,
          bool primary = false,
          bool danger = false}) =>
      Semantics(
          button: true,
          enabled: onTap != null,
          label: label,
          child: Tooltip(
            message: label,
            child: InkWell(
              onTap: onTap,
              customBorder: const CircleBorder(),
              child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: primary ? 84 : 74,
                  height: primary ? 84 : 74,
                  decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: primary
                          ? const Color(0xFF7564D8)
                          : danger
                              ? const Color(0xFFFFE4E8)
                              : const Color(0xFFE9E8FF),
                      border: Border.all(
                          color: danger
                              ? const Color(0xFFE8AAB5)
                              : const Color(0xFFCAC7F4),
                          width: 1.2),
                      boxShadow: [
                        BoxShadow(
                            color: (danger
                                    ? const Color(0xFFB93452)
                                    : const Color(0xFF6556B8))
                                .withValues(alpha: .16),
                            blurRadius: 15,
                            offset: const Offset(0, 5))
                      ]),
                  child: Icon(
                    icon,
                    color: primary
                        ? Colors.white
                        : danger
                            ? const Color(0xFF9E2944)
                            : const Color(0xFF343765),
                    size: primary ? 28 : 24,
                  )),
            ),
          ));

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

class _IridescentVoiceOrb extends StatefulWidget {
  const _IridescentVoiceOrb({
    required this.size,
    required this.speaking,
    required this.listening,
  });

  final double size;
  final bool speaking;
  final bool listening;

  @override
  State<_IridescentVoiceOrb> createState() => _IridescentVoiceOrbState();
}

class _IridescentVoiceOrbState extends State<_IridescentVoiceOrb>
    with SingleTickerProviderStateMixin {
  late final AnimationController _motion = AnimationController(
    vsync: this,
    duration: Duration(seconds: widget.speaking ? 4 : 7),
  )..repeat();

  @override
  void didUpdateWidget(covariant _IridescentVoiceOrb oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.speaking != widget.speaking ||
        oldWidget.listening != widget.listening) {
      _motion.duration = Duration(seconds: widget.speaking ? 4 : 7);
      _motion.repeat();
    }
  }

  @override
  void dispose() {
    _motion.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
        label: widget.speaking
            ? 'Verity is speaking'
            : widget.listening
                ? 'Verity is listening'
                : 'Verity voice assistant',
        child: SizedBox.square(
          dimension: widget.size,
          child: Stack(
            alignment: Alignment.center,
            children: [
              AnimatedContainer(
                duration: const Duration(milliseconds: 450),
                width: widget.size * .72,
                height: widget.size * .72,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: RadialGradient(
                    colors: [
                      const Color(0xFFB5FBF3)
                          .withValues(alpha: widget.speaking ? .7 : .48),
                      const Color(0xFFACBDFB).withValues(alpha: .3),
                      const Color(0xFFD786E8).withValues(alpha: 0),
                    ],
                  ),
                ),
              ),
              AnimatedBuilder(
                animation: _motion,
                builder: (context, child) {
                  final phase = _motion.value * math.pi * 2;
                  final breath = math.sin(phase);
                  final motion = widget.speaking ? .035 : .018;
                  return Transform.translate(
                    offset: Offset(0, breath * widget.size * .012),
                    child: Transform.rotate(
                      angle: breath * (widget.speaking ? .022 : .01),
                      child: Transform.scale(
                        scale: 1 + breath * motion,
                        child: child,
                      ),
                    ),
                  );
                },
                child: ColorFiltered(
                  colorFilter: const ColorFilter.matrix([
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    0,
                    0,
                    .72,
                    .72,
                    .72,
                    0,
                    0,
                  ]),
                  child: Image.asset(
                    'assets/images/verity_voice_orb.png',
                    width: widget.size,
                    height: widget.size,
                    fit: BoxFit.contain,
                    filterQuality: FilterQuality.medium,
                    gaplessPlayback: true,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
}
