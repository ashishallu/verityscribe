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
  final String? reportTitle;
  final String? reportSummary;
  final String? extractedText;
  const ChatMessage(
      {required this.text,
      required this.isUser,
      required this.sentAt,
      this.attachment,
      this.action,
      this.reportTitle,
      this.reportSummary,
      this.extractedText});
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
  final bool chatSessionLoading;
  final Object? chatError;
  const ClinicState(
      {required this.selectedDate,
      required this.medicines,
      required this.consultations,
      required this.messages,
      this.chatSessionId,
      this.sessionDocuments = const [],
      this.chatLoading = false,
      this.chatSessionLoading = false,
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
          bool? chatSessionLoading,
          Object? chatError,
          bool clearChatError = false}) =>
      ClinicState(
          selectedDate: selectedDate ?? this.selectedDate,
          medicines: medicines ?? this.medicines,
          consultations: consultations ?? this.consultations,
          messages: messages ?? this.messages,
          chatSessionId:
              clearChatSession ? null : (chatSessionId ?? this.chatSessionId),
          sessionDocuments: sessionDocuments ?? this.sessionDocuments,
          chatLoading: chatLoading ?? this.chatLoading,
          chatSessionLoading: chatSessionLoading ?? this.chatSessionLoading,
          chatError: clearChatError ? null : (chatError ?? this.chatError));
}

class ClinicNotifier extends StateNotifier<ClinicState> {
  ClinicNotifier(this._health, this._api, this._auth,
      {http.Client? uploadClient})
      : _uploadClient = uploadClient ?? http.Client(),
        super(ClinicState(
            selectedDate: DateTime.now(),
            medicines: const [],
            consultations: const [],
            messages: const [])) {
    load();
  }
  final HealthRepository _health;
  final ApiClient _api;
  final SupabaseAuthService _auth;
  final http.Client _uploadClient;
  // Reuse the upload ID when the same attachment is retried. The backend
  // stores the private object before inference and resumes it by this ID.
  String? _pendingReportUploadId;
  String? _pendingReportFingerprint;
  Future<String>? _chatSessionInitialization;
  int _chatSessionGeneration = 0;

  @override
  void dispose() {
    _uploadClient.close();
    super.dispose();
  }

  static final RegExp _uuidPattern = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$');

  Future<String> _ensureChatSession() async {
    final existing = state.chatSessionId;
    if (existing != null && _uuidPattern.hasMatch(existing)) return existing;
    final pending = _chatSessionInitialization;
    if (pending != null) return pending;

    final generation = _chatSessionGeneration;
    final creation = _createChatSession(generation);
    _chatSessionInitialization = creation;
    try {
      return await creation;
    } finally {
      if (identical(_chatSessionInitialization, creation)) {
        _chatSessionInitialization = null;
      }
    }
  }

  Future<String> _createChatSession(int generation) async {
    state = state.copyWith(chatSessionLoading: true, clearChatError: true);
    try {
      final response = await _api.post<dynamic>('/chat/sessions', const {});
      final body = Map<String, dynamic>.from(response.data as Map);
      final data = Map<String, dynamic>.from((body['data'] as Map?) ?? body);
      final id = data['session_id']?.toString();
      if (id == null || !_uuidPattern.hasMatch(id)) {
        throw const ApiException(
            'The secure chat service returned an invalid session. Please retry.');
      }
      if (generation != _chatSessionGeneration) {
        unawaited(_endChatSession(id));
        throw const ApiException(
            'This conversation was cleared. Please send your message again.');
      }
      state = state.copyWith(
          chatSessionId: id, chatSessionLoading: false, clearChatError: true);
      return id;
    } catch (error) {
      if (generation == _chatSessionGeneration) {
        final safeError = error is ApiException && error.statusCode == 422
            ? const ApiException(
                'The secure chat session could not be verified. Please clear the conversation and retry.')
            : error is ApiException &&
                    error.message.startsWith('The secure chat')
                ? error
                : const ApiException(
                    'Could not start a secure chat session. Check your connection and retry.');
        state = state.copyWith(chatSessionLoading: false, chatError: safeError);
        throw safeError;
      }
      rethrow;
    }
  }

  Future<void> initializeChatSession() async {
    try {
      await _ensureChatSession();
    } catch (_) {
      // _createChatSession stores a safe, user-visible error in state.
    }
  }

  Future<void> _endChatSession(String sessionId) async {
    try {
      await _api.post<dynamic>('/chat/sessions/$sessionId/end', const {});
    } catch (_) {
      // Ending an old session is best effort; it cannot grant access to it.
    }
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
    if (state.chatLoading) return;
    final generation = _chatSessionGeneration;
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
      if (generation != _chatSessionGeneration) return;
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
      if (generation == _chatSessionGeneration) {
        final visibleError = e is ApiException && e.statusCode == 422
            ? const ApiException(
                'The chat could not verify its secure session. Clear the conversation and retry.')
            : e is ApiException && e.statusCode == 410
                ? const ApiException(
                    'This chat session has ended. Clear the conversation to start a new one.')
                : e;
        state = state.copyWith(chatLoading: false, chatError: visibleError);
      }
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
    final generation = _chatSessionGeneration;

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
        final request =
            http.MultipartRequest('POST', Uri.parse('$apiBaseUrl/chat/reports'))
              ..headers['Authorization'] = 'Bearer $token'
              ..headers['X-Upload-Id'] = uploadId
              ..fields['report_type'] = reportType
              ..fields['session_id'] = sessionId
              ..files.add(http.MultipartFile.fromBytes('document', bytes,
                  filename: filename, contentType: _reportMimeType(filename)));
        return _uploadClient
            .send(request)
            .timeout(const Duration(seconds: 120));
      }

      // The API already performs model fallback. Do not send the full image a
      // second time after a model-capacity response or browser network error:
      // that adds latency and cannot make an unreachable/CORS-blocked API work.
      uploadStage = 'sending the report to the API';
      final response = await send();
      uploadStage = 'reading the API response';
      final text = await response.stream.bytesToString();
      if (generation != _chatSessionGeneration) return;

      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(_uploadError(text), statusCode: response.statusCode);
      }
      final decoded = Map<String, dynamic>.from(jsonDecode(text) as Map);
      final data = decoded['data'] is Map
          ? Map<String, dynamic>.from(decoded['data'] as Map)
          : decoded;
      final confirmation =
          (data['message'] ?? 'Your report was saved privately.').toString();
      final summary = data['summary'] is Map
          ? Map<String, dynamic>.from(data['summary'] as Map)
          : <String, dynamic>{};
      final extractedText = data['extracted_text']?.toString();
      final document = data['document'] is Map
          ? Map<String, dynamic>.from(data['document'] as Map)
          : <String, dynamic>{};
      final documentId = document['id']?.toString();
      final sessionDocuments = [...state.sessionDocuments];
      if (documentId != null &&
          !sessionDocuments.any((item) => item.id == documentId)) {
        sessionDocuments
            .add(ChatSessionDocument(id: documentId, name: filename));
      }
      state = state.copyWith(messages: [
        ...existingMessages,
        ChatMessage(
          text: label,
          isUser: true,
          sentAt: now,
          attachment: reportType,
        ),
        ChatMessage(
          text: confirmation,
          isUser: false,
          sentAt: DateTime.now(),
          reportTitle: summary['title']?.toString(),
          reportSummary: summary['summary']?.toString(),
          extractedText: extractedText,
        ),
      ], sessionDocuments: sessionDocuments, chatLoading: false);
      _pendingReportUploadId = null;
      _pendingReportFingerprint = null;
    } on TimeoutException {
      if (generation != _chatSessionGeneration) return;
      state = state.copyWith(
        chatLoading: false,
        chatError: const ApiException(
          'Report processing timed out. The file may be saved privately; retry after checking your connection.',
        ),
      );
    } on http.ClientException catch (error) {
      if (generation != _chatSessionGeneration) return;
      final host = Uri.tryParse(apiBaseUrl)?.host ?? 'the configured API';
      state = state.copyWith(
        chatLoading: false,
        chatError: ApiException(
          'Could not reach $host from this browser. Check the API URL and CORS settings, then retry. (${error.message})',
        ),
      );
    } on FormatException {
      if (generation != _chatSessionGeneration) return;
      state = state.copyWith(
        chatLoading: false,
        chatError: const ApiException(
          'The report service returned an unreadable response. Please retry.',
        ),
      );
    } on ApiException catch (error) {
      if (generation != _chatSessionGeneration) return;
      // Keep server-side details instead of replacing them with a generic
      // client parse error, especially for retryable inference-capacity 503s.
      state = state.copyWith(chatLoading: false, chatError: error);
    } catch (error, stackTrace) {
      if (generation != _chatSessionGeneration) return;
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
      if (state.chatSessionId != sessionId) return;
      state = state.copyWith(
          sessionDocuments: state.sessionDocuments
              .where((document) => document.id != documentId)
              .toList());
    } catch (error) {
      if (state.chatSessionId == sessionId) {
        state = state.copyWith(chatError: error);
      }
    }
  }

  void clearChat() {
    final sessionId = state.chatSessionId;
    _chatSessionGeneration++;
    _chatSessionInitialization = null;
    state = state.copyWith(
        messages: [],
        chatSessionId: null,
        clearChatSession: true,
        sessionDocuments: const [],
        chatLoading: false,
        chatSessionLoading: false,
        clearChatError: true);
    if (sessionId != null) {
      unawaited(_endChatSession(sessionId));
    }
    // Start the new conversation now; send/upload also await the shared future.
    unawaited(initializeChatSession());
  }

  void clearChatError() => state = state.copyWith(clearChatError: true);
}

final clinicProvider = StateNotifierProvider<ClinicNotifier, ClinicState>(
    (ref) => ClinicNotifier(ref.read(healthRepositoryProvider),
        ref.read(apiClientProvider), ref.read(supabaseAuthProvider)));
