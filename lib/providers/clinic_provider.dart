import 'dart:convert';
import 'dart:async';
import 'dart:math';
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

class ClinicState {
  final DateTime selectedDate;
  final List<Medicine> medicines;
  final List<Consultation> consultations;
  final List<ChatMessage> messages;
  final bool chatLoading;
  final Object? chatError;
  const ClinicState(
      {required this.selectedDate,
      required this.medicines,
      required this.consultations,
      required this.messages,
      this.chatLoading = false,
      this.chatError});
  ClinicState copyWith(
          {DateTime? selectedDate,
          List<Medicine>? medicines,
          List<Consultation>? consultations,
          List<ChatMessage>? messages,
          bool? chatLoading,
          Object? chatError,
          bool clearChatError = false}) =>
      ClinicState(
          selectedDate: selectedDate ?? this.selectedDate,
          medicines: medicines ?? this.medicines,
          consultations: consultations ?? this.consultations,
          messages: messages ?? this.messages,
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
      final response = await _api.post<dynamic>('/chat', {
        'message': text,
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
    final now = DateTime.now();
    final label = 'Uploaded $filename';
    final updated = [...state.messages,
      ChatMessage(text: label, isUser: true, sentAt: now, attachment: reportType)];
    state = state.copyWith(
        messages: updated, chatLoading: true, clearChatError: true);
    try {
      final token = await _auth.accessToken();
      if (token == null) throw const ApiException('Your session has expired. Sign in again.');
      final uploadId = '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${Random.secure().nextInt(1 << 32).toRadixString(36)}';
      Future<http.StreamedResponse> send() {
        final request = http.MultipartRequest('POST', Uri.parse('$apiBaseUrl/chat/reports'))
          ..headers['Authorization'] = 'Bearer $token'
          ..headers['X-Upload-Id'] = uploadId
          ..fields['report_type'] = reportType
          ..files.add(http.MultipartFile.fromBytes('document', bytes,
              filename: filename, contentType: _reportMimeType(filename)));
        return request.send().timeout(const Duration(seconds: 120));
      }
      late http.StreamedResponse response;
      try {
        response = await send();
      } on TimeoutException catch (_) {
        // Render can replace a web process during an in-flight vision request.
        // Repeat once with the same idempotency key so the backend resumes it.
        response = await send();
      } on http.ClientException catch (_) {
        response = await send();
      }
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
      state = state.copyWith(messages: [...updated, ChatMessage(
        text: confirmation,
        isUser: false, sentAt: DateTime.now())], chatLoading: false);
    } catch (error) {
      state = state.copyWith(chatLoading: false, chatError: error);
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

  void clearChat() => state = state.copyWith(messages: [], clearChatError: true);
  void clearChatError() => state = state.copyWith(clearChatError: true);
}

final clinicProvider = StateNotifierProvider<ClinicNotifier, ClinicState>(
    (ref) => ClinicNotifier(
        ref.read(healthRepositoryProvider), ref.read(apiClientProvider),
        ref.read(supabaseAuthProvider)));
