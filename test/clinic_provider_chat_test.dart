import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:verity_scribe/core/services/api_service.dart';
import 'package:verity_scribe/core/services/supabase_auth_service.dart';
import 'package:verity_scribe/models/entities.dart';
import 'package:verity_scribe/providers/clinic_provider.dart';
import 'package:verity_scribe/repositories/repositories.dart';

const _sessionA = '11111111-1111-4111-8111-111111111111';
const _sessionB = '22222222-2222-4222-8222-222222222222';

class _Health extends Fake implements HealthRepository {
  @override
  Future<List<Medicine>> medicines() async => const [];
  @override
  Future<List<Consultation>> consultations() async => const [];
}

class _Auth extends Fake implements SupabaseAuthService {
  @override
  Future<String?> accessToken() async => 'test-token';
}

class _Api extends Fake implements ApiClient {
  final calls = <(String, Map<String, dynamic>)>[];
  final sessionResponses = <String>[_sessionA, _sessionB];
  Completer<ApiResponse<dynamic>>? sessionCompleter;
  Object? sessionError;
  int sessionCreates = 0;

  @override
  Future<ApiResponse<T>> post<T>(String path, Map<String, dynamic> body) async {
    calls.add((path, Map<String, dynamic>.from(body)));
    if (path == '/chat/sessions') {
      sessionCreates++;
      if (sessionError != null) throw sessionError!;
      final pending = sessionCompleter;
      if (pending != null) {
        sessionCompleter = null;
        return await pending.future as ApiResponse<T>;
      }
      final index = (sessionCreates - 1).clamp(0, sessionResponses.length - 1);
      return ApiResponse<T>({
        'data': {'session_id': sessionResponses[index], 'status': 'active'},
      } as T);
    }
    if (path == '/chat') {
      return ApiResponse<T>({
        'data': {'answer': 'A general health answer.'}
      } as T);
    }
    if (path.endsWith('/end')) {
      return ApiResponse<T>({
        'data': {'status': 'ended'}
      } as T);
    }
    return ApiResponse<T>({'data': <String, dynamic>{}} as T);
  }

  @override
  Future<ApiResponse<T>> get<T>(String path) async =>
      ApiResponse<T>({'data': <String, dynamic>{}} as T);
}

class _UploadClient extends http.BaseClient {
  http.MultipartRequest? lastRequest;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    lastRequest = request as http.MultipartRequest;
    final body = utf8.encode(jsonEncode({
      'data': {
        'message': 'Saved privately.',
        'document': {'id': 'document-1'},
      }
    }));
    return http.StreamedResponse(Stream.value(body), 201,
        request: request, headers: {'content-type': 'application/json'});
  }
}

void main() {
  late _Api api;
  late ClinicNotifier notifier;
  late _UploadClient uploadClient;

  setUp(() {
    api = _Api();
    uploadClient = _UploadClient();
    notifier =
        ClinicNotifier(_Health(), api, _Auth(), uploadClient: uploadClient);
  });

  tearDown(() => notifier.dispose());

  test('concurrent chatbot opening initializes one server-owned UUID session',
      () async {
    api.sessionCompleter = Completer<ApiResponse<dynamic>>();
    final pendingSession = api.sessionCompleter!;
    final first = notifier.initializeChatSession();
    final second = notifier.initializeChatSession();
    expect(api.sessionCreates, 1);
    pendingSession.complete(ApiResponse<dynamic>({
      'data': {'session_id': _sessionA},
    }));
    await Future.wait([first, second]);
    expect(notifier.state.chatSessionId, _sessionA);
    expect(notifier.state.chatSessionLoading, isFalse);
  });

  test('typed and suggested-question submission paths send the same session ID',
      () async {
    await notifier.initializeChatSession();
    await notifier.sendMessage('What is a balanced diet?');
    await notifier.sendMessage('What is my recorded height?');
    final chatCalls = api.calls.where((call) => call.$1 == '/chat').toList();
    expect(chatCalls, hasLength(2));
    expect(chatCalls.map((call) => call.$2['session_id']),
        everyElement(_sessionA));
    expect(notifier.state.messages.last.text, 'A general health answer.');
  });

  test('session initialization failure prevents chat submission and is visible',
      () async {
    api.sessionError = const ApiException('temporary failure', statusCode: 503);
    await notifier.sendMessage('What is a balanced diet?');
    expect(api.calls.where((call) => call.$1 == '/chat'), isEmpty);
    expect(notifier.state.chatError, isA<ApiException>());
    expect((notifier.state.chatError! as ApiException).message,
        contains('Could not start a secure chat session'));
    expect(notifier.state.chatSessionId, isNull);
  });

  test('clearing ends the old session and the next message uses a new session',
      () async {
    await notifier.initializeChatSession();
    notifier.clearChat();
    await notifier.initializeChatSession();
    await notifier.sendMessage('What is a balanced diet?');
    expect(api.calls.any((call) => call.$1 == '/chat/sessions/$_sessionA/end'),
        isTrue);
    expect(notifier.state.chatSessionId, _sessionB);
    expect(api.calls.last.$2['session_id'], _sessionB);
    expect(notifier.state.sessionDocuments, isEmpty);
  });

  test('a session finishing after clear cannot replace the new conversation',
      () async {
    api.sessionCompleter = Completer<ApiResponse<dynamic>>();
    final pendingSession = api.sessionCompleter!;
    final staleInitialization = notifier.initializeChatSession();
    await Future<void>.delayed(Duration.zero);
    notifier.clearChat();
    await notifier.initializeChatSession();
    pendingSession.complete(ApiResponse<dynamic>({
      'data': {'session_id': _sessionA},
    }));
    await staleInitialization;
    expect(notifier.state.chatSessionId, _sessionB);
    expect(api.calls.any((call) => call.$1 == '/chat/sessions/$_sessionA/end'),
        isTrue);
  });

  test('report upload sends the initialized session ID in multipart fields',
      () async {
    await notifier.initializeChatSession();
    await notifier.uploadReport(
        reportType: 'prescription', filename: 'sample.png', bytes: [1, 2, 3]);
    expect(uploadClient.lastRequest?.fields['session_id'], _sessionA);
    expect(notifier.state.sessionDocuments.single.id, 'document-1');
  });

  test('invalid ID returned from session creation is not submitted', () async {
    api.sessionResponses[0] = 'not-a-session-id';
    await notifier.sendMessage('What is a balanced diet?');
    expect(api.calls.where((call) => call.$1 == '/chat'), isEmpty);
    expect(notifier.state.chatSessionId, isNull);
    expect(notifier.state.chatError, isA<ApiException>());
  });

  test(
      'session state remains in the notifier across screen-level rebuild lifetimes',
      () async {
    await notifier.initializeChatSession();
    final retainedId = notifier.state.chatSessionId;
    // Rebuilding a ConsumerWidget does not recreate the non-autoDispose
    // clinicProvider StateNotifier. A follow-up continues on the same ID.
    await notifier.sendMessage('Follow-up question');
    expect(notifier.state.chatSessionId, retainedId);
    expect(api.calls.last.$2['session_id'], retainedId);
  });
}
