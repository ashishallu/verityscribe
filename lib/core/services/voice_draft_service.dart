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
      required this.conflicts});
  final String transcriptId,
      finalTranscript,
      primaryTranscript,
      secondaryTranscript;
  final List<String> conflicts;
  factory VoiceDraftResult.fromJson(Map<String, dynamic> body) {
    final data = Map<String, dynamic>.from((body['data'] as Map?) ?? body);
    final transcript =
        Map<String, dynamic>.from(data['transcript'] as Map? ?? const {});
    final asr = Map<String, dynamic>.from(data['asr'] as Map? ?? const {});
    return VoiceDraftResult(
      transcriptId: (transcript['id'] ?? '').toString(),
      finalTranscript: (transcript['transcript_text'] ?? '').toString(),
      primaryTranscript: (asr['primary_transcript'] ?? '').toString(),
      secondaryTranscript: (asr['secondary_transcript'] ?? '').toString(),
      conflicts: (asr['conflicts'] as List? ?? const [])
          .map((value) => value.toString())
          .toList(),
    );
  }
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
    final response = await request.send().timeout(const Duration(seconds: 180));
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
        filename: 'voice_consultation.webm'));
    final response = await request.send().timeout(const Duration(seconds: 180));
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
