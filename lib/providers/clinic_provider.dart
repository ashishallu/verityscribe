import 'dart:convert';
import 'dart:async';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/entities.dart';
import '../repositories/repositories.dart';
import 'app_providers.dart';
import '../core/services/api_service.dart';
import '../core/services/supabase_auth_service.dart';
import '../core/constants/endpoints.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

class ChatMessage {
  final String text;
  final bool isUser;
  final DateTime sentAt;
  final String? attachment;
  final String? action;
  const ChatMessage(
      {required this.text,
      required this.isUser,
      required this.sentAt,
      this.attachment,
      this.action});
}

class ChatSessionDocument {
  final String id;
  final String name;
  const ChatSessionDocument({required this.id, required this.name});
}

class ClinicState {
  final DateTime selectedDate;
  final List<Medicine> medicines;
  final List<Consultation> consultations;
  final List<ChatMessage> messages;
  final String? chatSessionId;
  final List<ChatSessionDocument> sessionDocuments;
  final bool chatLoading;
  final Object? chatError;
  const ClinicState(
      {required this.selectedDate,
      required this.medicines,
      required this.consultations,
      required this.messages,
      this.chatSessionId,
      this.sessionDocuments = const [],
      this.chatLoading = false,
      this.chatError});
  ClinicState copyWith(
          {DateTime? selectedDate,
          List<Medicine>? medicines,
          List<Consultation>? consultations,
          List<ChatMessage>? messages,
          String? chatSessionId,
          bool clearChatSession = false,
          List<ChatSessionDocument>? sessionDocuments,
          bool? chatLoading,
          Object? chatError,
          bool clearChatError = false}) =>
      ClinicState(
          selectedDate: selectedDate ?? this.selectedDate,
          medicines: medicines ?? this.medicines,
          consultations: consultations ?? this.consultations,
          messages: messages ?? this.messages,
          chatSessionId: clearChatSession ? null : (chatSessionId ?? this.chatSessionId),
          sessionDocuments: sessionDocuments ?? this.sessionDocuments,
          chatLoading: chatLoading ?? this.chatLoading,
          chatError: clearChatError ? null : (chatError ?? this.chatError));
}

class ClinicNotifier extends StateNotifier<ClinicState> {
  ClinicNotifier(this._health, this._api, this._auth)
      : super(ClinicState(
            selectedDate: DateTime.now(),
            medicines: const [],
            consultations: const [],
            messages: const [])) {
    load();
  }
  final HealthRepository _health;
  final ApiClient _api;
  final SupabaseAuthService _auth;
  // Reuse the upload ID when the same attachment is retried. The backend
  // stores the private object before inference and resumes it by this ID.
  String? _pendingReportUploadId;
  String? _pendingReportFingerprint;

  Future<String> _ensureChatSession() async {
    final existing = state.chatSessionId;
    if (existing != null) return existing;
    final response = await _api.post<dynamic>('/chat/sessions', const {});
    final body = Map<String, dynamic>.from(response.data as Map);
    final data = Map<String, dynamic>.from((body['data'] as Map?) ?? body);
    final id = data['session_id']?.toString();
    if (id == null || id.isEmpty) {
      throw const ApiException('Could not start a secure chat session. Please retry.');
    }
    state = state.copyWith(chatSessionId: id);
    return id;
  }

  Future<void> load() async {
    try {
      final results =
          await Future.wait([_health.medicines(), _health.consultations()]);
      state = state.copyWith(
          medicines: results[0] as List<Medicine>,
          consultations: results[1] as List<Consultation>);
    } catch (_) {}
  }

  void selectDate(DateTime date) => state = state.copyWith(selectedDate: date);
  void markTaken(String id) => state = state.copyWith(
      medicines: state.medicines
          .map((medicine) => medicine.id == id
              ? Medicine(
                  id: medicine.id,
                  name: medicine.name,
                  dosage: medicine.dosage,
                  purpose: medicine.purpose,
                  schedule: medicine.schedule,
                  remaining: medicine.remaining,
                  initialQuantity: medicine.initialQuantity,
                  dailyUsage: medicine.dailyUsage,
                  taken: true,
                  doctor: medicine.doctor,
                  howToTake: medicine.howToTake,
                  foodInstructions: medicine.foodInstructions,
                  sideEffects: medicine.sideEffects,
                  notes: medicine.notes)
              : medicine)
          .toList());
  Future<void> sendMessage(String text, {String? attachment}) async {
    final now = DateTime.now();
    final updated = [
      ...state.messages,
      ChatMessage(text: text, isUser: true, sentAt: now, attachment: attachment)
    ];
    state = state.copyWith(
        messages: updated, chatLoading: true, clearChatError: true);
    try {
      final sessionId = await _ensureChatSession();
      final response = await _api.post<dynamic>('/chat', {
        'message': text,
        'session_id': sessionId,
        'history': updated
            .map((m) =>
                {'text': m.text, 'role': m.isUser ? 'user' : 'assistant'})
            .toList(),
        if (attachment != null) 'attachment': attachment
      });
      final body = Map<String, dynamic>.from(response.data as Map);
      final data = body['data'] is Map
          ? Map<String, dynamic>.from(body['data'] as Map)
          : body;
      final answer = (data['answer'] ?? data['message'] ?? '').toString();
      final action = data['action']?.toString();
      if (answer.isEmpty) {
        throw const ApiException('The care assistant returned no response.');
      }
      state = state.copyWith(messages: [
        ...updated,
        ChatMessage(
            text: answer, isUser: false, sentAt: DateTime.now(), action: action)
      ], chatLoading: false);
    } catch (e) {
      state = state.copyWith(chatLoading: false, chatError: e);
    }
  }

  Future<void> uploadReport({
    required String reportType,
    required String filename,
    required List<int> bytes,
  }) async {
    // Do not submit the same selected file twice while its first submission
    // is still in progress.  This prevents duplicate attachment bubbles and
    // duplicate processing requests.
    if (state.chatLoading) return;

    final now = DateTime.now();
    final label = 'Uploaded $filename';
    final existingMessages = state.messages;
    var uploadStage = 'reading your session';
    state = state.copyWith(chatLoading: true, clearChatError: true);
    try {
      final token = await _auth.accessToken();
      if (token == null) {
        throw const ApiException('Your session has expired. Sign in again.');
      }
      uploadStage = 'preparing the secure upload';
      // Avoid `1 << 32` here: the Dart-to-JavaScript web compiler can lower
      // that bit shift to zero, causing Random.nextInt(0) to throw RangeError.
      final fingerprint = '$reportType:$filename:${bytes.length}';
      final sessionId = await _ensureChatSession();
      final uploadId = _pendingReportFingerprint == fingerprint &&
              _pendingReportUploadId != null
          ? _pendingReportUploadId!
          : '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${Random.secure().nextInt(0x7fffffff).toRadixString(36)}';
      _pendingReportFingerprint = fingerprint;
      _pendingReportUploadId = uploadId;
      Future<http.StreamedResponse> send() {
        uploadStage = 'building the multipart request';
        final request = http.MultipartRequest(
            'POST', Uri.parse('$apiBaseUrl/chat/reports'))
          ..headers['Authorization'] = 'Bearer $token'
          ..headers['X-Upload-Id'] = uploadId
          ..fields['report_type'] = reportType
          ..fields['session_id'] = sessionId
          ..files.add(http.MultipartFile.fromBytes('document', bytes,
              filename: filename, contentType: _reportMimeType(filename)));
        return request.send().timeout(const Duration(seconds: 120));
      }
      // The API already performs model fallback. Do not send the full image a
      // second time after a model-capacity response or browser network error:
      // that adds latency and cannot make an unreachable/CORS-blocked API work.
      uploadStage = 'sending the report to the API';
      final response = await send();
      uploadStage = 'reading the API response';
      final text = await response.stream.bytesToString();

      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(_uploadError(text), statusCode: response.statusCode);
      }
      final decoded = Map<String, dynamic>.from(jsonDecode(text) as Map);
      final data = decoded['data'] is Map
          ? Map<String, dynamic>.from(decoded['data'] as Map)
          : decoded;
      final confirmation = (data['message'] ??
              'Your report was saved privately.')
          .toString();
      final document = data['document'] is Map
          ? Map<String, dynamic>.from(data['document'] as Map)
          : <String, dynamic>{};
      final documentId = document['id']?.toString();
      final sessionDocuments = [...state.sessionDocuments];
      if (documentId != null && !sessionDocuments.any((item) => item.id == documentId)) {
        sessionDocuments.add(ChatSessionDocument(id: documentId, name: filename));
      }
      state = state.copyWith(messages: [
        ...existingMessages,
        ChatMessage(
          text: label,
          isUser: true,
          sentAt: now,
          attachment: reportType,
        ),
        ChatMessage(text: confirmation, isUser: false, sentAt: DateTime.now()),
      ], sessionDocuments: sessionDocuments, chatLoading: false);
      _pendingReportUploadId = null;
      _pendingReportFingerprint = null;
    } on TimeoutException {
      state = state.copyWith(
        chatLoading: false,
        chatError: const ApiException(
          'Report processing timed out. The file may be saved privately; retry after checking your connection.',
        ),
      );
    } on http.ClientException catch (error) {
      final host = Uri.tryParse(apiBaseUrl)?.host ?? 'the configured API';
      state = state.copyWith(
        chatLoading: false,
        chatError: ApiException(
          'Could not reach $host from this browser. Check the API URL and CORS settings, then retry. (${error.message})',
        ),
      );
    } on FormatException {
      state = state.copyWith(
        chatLoading: false,
        chatError: const ApiException(
          'The report service returned an unreadable response. Please retry.',
        ),
      );
    } on ApiException catch (error) {
      // Keep server-side details instead of replacing them with a generic
      // client parse error, especially for retryable inference-capacity 503s.
      state = state.copyWith(chatLoading: false, chatError: error);
    } catch (error, stackTrace) {
      debugPrint(
          'Report upload failed while $uploadStage (${error.runtimeType}): $error');
      debugPrintStack(stackTrace: stackTrace);
      state = state.copyWith(
        chatLoading: false,
        chatError: ApiException(
          'Report upload failed while $uploadStage (${error.runtimeType}). Check the browser console for diagnostic details.',
        ),
      );
    }
  }

  MediaType _reportMimeType(String filename) {
    final extension = filename.split('.').last.toLowerCase();
    return switch (extension) {
      'pdf' => MediaType('application', 'pdf'),
      'png' => MediaType('image', 'png'),
      'jpg' || 'jpeg' => MediaType('image', 'jpeg'),
      'webp' => MediaType('image', 'webp'),
      _ => MediaType('application', 'octet-stream'),
    };
  }

  String _uploadError(String response) {
    try {
      final decoded = Map<String, dynamic>.from(jsonDecode(response) as Map);
      return (decoded['detail'] ?? 'Unable to upload this report.').toString();
    } catch (_) {
      return 'Unable to upload this report.';
    }
  }

  Future<void> removeSessionDocument(String documentId) async {
    final sessionId = state.chatSessionId;
    if (sessionId == null) return;
    try {
      await _api.post<dynamic>(
          '/chat/sessions/$sessionId/documents/$documentId/remove', const {});
      state = state.copyWith(sessionDocuments: state.sessionDocuments
          .where((document) => document.id != documentId).toList());
    } catch (error) {
      state = state.copyWith(chatError: error);
    }
  }

  void clearChat() {
    final sessionId = state.chatSessionId;
    state = state.copyWith(messages: [], chatSessionId: null,
        clearChatSession: true, sessionDocuments: const [], clearChatError: true);
    if (sessionId != null) {
      unawaited(_api.post<dynamic>('/chat/sessions/$sessionId/end', const {}).catchError((_) => const ApiResponse<dynamic>({})));
    }
  }
  void clearChatError() => state = state.copyWith(clearChatError: true);
}

final clinicProvider = StateNotifierProvider<ClinicNotifier, ClinicState>(
    (ref) => ClinicNotifier(
        ref.read(healthRepositoryProvider), ref.read(apiClientProvider),
        ref.read(supabaseAuthProvider)));
