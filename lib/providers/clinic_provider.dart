import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/entities.dart';
import '../repositories/repositories.dart';
import 'app_providers.dart';
import '../core/services/api_service.dart';
import '../core/services/supabase_auth_service.dart';
import '../core/constants/endpoints.dart';
import 'package:http/http.dart' as http;

class ChatMessage {
  final String text;
  final bool isUser;
  final DateTime sentAt;
  final String? attachment;
  const ChatMessage(
      {required this.text,
      required this.isUser,
      required this.sentAt,
      this.attachment});
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
          Object? chatError}) =>
      ClinicState(
          selectedDate: selectedDate ?? this.selectedDate,
          medicines: medicines ?? this.medicines,
          consultations: consultations ?? this.consultations,
          messages: messages ?? this.messages,
          chatLoading: chatLoading ?? this.chatLoading,
          chatError: chatError);
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
    state =
        state.copyWith(messages: updated, chatLoading: true, chatError: null);
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
      if (answer.isEmpty) {
        throw const ApiException('The care assistant returned no response.');
      }
      state = state.copyWith(messages: [
        ...updated,
        ChatMessage(text: answer, isUser: false, sentAt: DateTime.now())
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
    state = state.copyWith(messages: updated, chatLoading: true, chatError: null);
    try {
      final token = await _auth.accessToken();
      if (token == null) throw const ApiException('Your session has expired. Sign in again.');
      final request = http.MultipartRequest('POST', Uri.parse('$apiBaseUrl/chat/reports'))
        ..headers['Authorization'] = 'Bearer $token'
        ..fields['report_type'] = reportType
        ..files.add(http.MultipartFile.fromBytes('document', bytes, filename: filename));
      final response = await request.send().timeout(const Duration(seconds: 90));
      final text = await response.stream.bytesToString();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(text, statusCode: response.statusCode);
      }
      state = state.copyWith(messages: [...updated, ChatMessage(
        text: 'Your report was saved privately. Ask me a question about its readable content.',
        isUser: false, sentAt: DateTime.now())], chatLoading: false);
    } catch (error) {
      state = state.copyWith(chatLoading: false, chatError: error);
    }
  }

  void clearChat() => state = state.copyWith(messages: []);
}

final clinicProvider = StateNotifierProvider<ClinicNotifier, ClinicState>(
    (ref) => ClinicNotifier(
        ref.read(healthRepositoryProvider), ref.read(apiClientProvider),
        ref.read(supabaseAuthProvider)));
