import 'dart:convert';
import 'package:http/http.dart' as http;
import '../constants/endpoints.dart';
import 'supabase_auth_service.dart';

class VoiceDraftResult {
  const VoiceDraftResult(
      {required this.transcriptId,
      required this.finalTranscript,
      required this.primaryTranscript,
      required this.secondaryTranscript,
      required this.conflicts,
      required this.primaryModel,
      required this.secondaryModel,
      required this.reconciliationStatus,
      required this.predictions});
  final String transcriptId,
      finalTranscript,
      primaryTranscript,
      secondaryTranscript;
  final List<String> conflicts;
  final String primaryModel, secondaryModel, reconciliationStatus;
  final List<ASRModelPrediction> predictions;
  factory VoiceDraftResult.fromJson(Map<String, dynamic> body) {
    final data = Map<String, dynamic>.from((body['data'] as Map?) ?? body);
    final transcript =
        Map<String, dynamic>.from(data['transcript'] as Map? ?? const {});
    final asr = Map<String, dynamic>.from(data['asr'] as Map? ?? const {});
    final predictions = (asr['predictions'] as List? ?? const [])
        .whereType<Map>()
        .map((value) => ASRModelPrediction.fromJson(
            Map<String, dynamic>.from(value)))
        .toList();
    if (predictions.isEmpty) {
      for (final entry in [
        (asr['primary_model'], asr['primary_transcript']),
        (asr['secondary_model'], asr['secondary_transcript']),
      ]) {
        final model = (entry.$1 ?? '').toString();
        final text = (entry.$2 ?? '').toString();
        if (model.isNotEmpty || text.isNotEmpty) {
          predictions.add(ASRModelPrediction(
              model: model.isEmpty ? 'ASR model' : model,
              text: text,
              status: text.isEmpty ? 'unavailable' : 'success',
              weight: 1,
              latencyMs: 0));
        }
      }
    }
    return VoiceDraftResult(
      transcriptId: (transcript['id'] ?? '').toString(),
      finalTranscript: (transcript['transcript_text'] ?? '').toString(),
      primaryTranscript: (asr['primary_transcript'] ?? '').toString(),
      secondaryTranscript: (asr['secondary_transcript'] ?? '').toString(),
      primaryModel: (asr['primary_model'] ?? '').toString(),
      secondaryModel: (asr['secondary_model'] ?? '').toString(),
      reconciliationStatus:
          (asr['reconciliation_status'] ?? 'unknown').toString(),
      predictions: predictions,
      conflicts: (asr['conflicts'] as List? ?? const [])
          .map((value) => value.toString())
          .toList(),
    );
  }
}

class ASRModelPrediction {
  const ASRModelPrediction({
    required this.model,
    required this.text,
    required this.status,
    required this.weight,
    required this.latencyMs,
    this.errorCode,
  });

  final String model, text, status;
  final double weight;
  final int latencyMs;
  final String? errorCode;

  factory ASRModelPrediction.fromJson(Map<String, dynamic> json) =>
      ASRModelPrediction(
        model: (json['model'] ?? 'ASR model').toString(),
        text: (json['text'] ?? '').toString(),
        status: (json['status'] ?? 'unknown').toString(),
        weight: double.tryParse('${json['weight'] ?? 1}') ?? 1,
        latencyMs: int.tryParse('${json['latency_ms'] ?? 0}') ?? 0,
        errorCode: json['error_code']?.toString(),
      );
}

class VoiceDraftService {
  VoiceDraftService(this._auth);
  final SupabaseAuthService _auth;
  Future<VoiceDraftResult> upload(
      {required String appointmentId, required String filePath}) async {
    final token = await _auth.accessToken();
    if (token == null)
      throw const VoiceDraftException(
          'Your session has expired. Sign in again.');
    final request = http.MultipartRequest(
        'POST', Uri.parse('$apiBaseUrl/appointments/$appointmentId/voice'));
    request.headers['Authorization'] = 'Bearer $token';
    request.files.add(await http.MultipartFile.fromPath('audio', filePath));
    final response = await request.send().timeout(const Duration(seconds: 300));
    final text = await response.stream.bytesToString();
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw VoiceDraftException(_message(text, response.statusCode));
    }
    return VoiceDraftResult.fromJson(
        Map<String, dynamic>.from(jsonDecode(text) as Map));
  }

  Future<VoiceDraftResult> uploadBytes(
      {required String appointmentId, required List<int> bytes}) async {
    final token = await _auth.accessToken();
    if (token == null)
      throw const VoiceDraftException(
          'Your session has expired. Sign in again.');
    final request = http.MultipartRequest(
        'POST', Uri.parse('$apiBaseUrl/appointments/$appointmentId/voice'));
    request.headers['Authorization'] = 'Bearer $token';
    request.files.add(http.MultipartFile.fromBytes('audio', bytes,
        filename: 'voice_consultation.wav'));
    final response = await request.send().timeout(const Duration(seconds: 300));
    final text = await response.stream.bytesToString();
    if (response.statusCode < 200 || response.statusCode >= 300)
      throw VoiceDraftException(_message(text, response.statusCode));
    return VoiceDraftResult.fromJson(
        Map<String, dynamic>.from(jsonDecode(text) as Map));
  }

  Future<void> saveCorrection({
    required String transcriptId,
    required String transcriptText,
  }) async {
    final token = await _auth.accessToken();
    if (token == null) {
      throw const VoiceDraftException(
          'Your session has expired. Sign in again.');
    }
    final response = await http
        .post(
          Uri.parse('$apiBaseUrl/voice-transcripts/$transcriptId/correction'),
          headers: {
            'Authorization': 'Bearer $token',
            'Content-Type': 'application/json',
          },
          body: jsonEncode({'transcript_text': transcriptText}),
        )
        .timeout(const Duration(seconds: 30));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw VoiceDraftException(_message(response.body, response.statusCode));
    }
  }

  String _message(String text, int status) {
    try {
      final body = Map<String, dynamic>.from(jsonDecode(text) as Map);
      return (body['detail'] ?? 'Voice processing failed ($status)').toString();
    } catch (_) {
      return 'Voice processing failed ($status).';
    }
  }
}

class VoiceDraftException implements Exception {
  const VoiceDraftException(this.message);
  final String message;
  @override
  String toString() => message;
}
