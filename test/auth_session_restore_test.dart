import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/models/entities.dart';
import 'package:verity_scribe/providers/app_providers.dart';
import 'package:verity_scribe/repositories/repositories.dart';

void main() {
  group('AuthNotifier session restoration', () {
    late StreamController<UserModel?> events;
    late _FakeAuthRepository repository;
    late AuthNotifier notifier;

    setUp(() {
      events = StreamController<UserModel?>.broadcast(sync: true);
      repository = _FakeAuthRepository(events);
      notifier = AuthNotifier(repository);
    });

    tearDown(() async {
      notifier.dispose();
      await events.close();
    });

    test('waits for and restores a persisted user', () async {
      expect(notifier.state.isLoading, isTrue);
      repository.restores[0].complete(_user('patient-a'));
      await _flush();

      expect(notifier.state.valueOrNull?.id, 'patient-a');
      expect(notifier.isBootstrapping, isFalse);
      expect(notifier.sessionRestoreFailed, isFalse);
    });

    test('a sign-out event during restore wins over stale restored session',
        () async {
      events.add(null);
      repository.restores[0].complete(_user('patient-a'));
      await _flush();

      expect(notifier.state.valueOrNull, isNull);
      expect(notifier.state.hasError, isFalse);
    });

    test('transient restore failure is explicit and can be retried', () async {
      repository.restores[0].completeError(StateError('offline'));
      await _flush();
      expect(notifier.state.hasError, isTrue);
      expect(notifier.sessionRestoreFailed, isTrue);

      final retry = Completer<UserModel?>();
      repository.restores.add(retry);
      final retryFuture = notifier.restoreSession();
      expect(notifier.state.isLoading, isTrue);
      retry.complete(_user('patient-a'));
      await retryFuture;

      expect(notifier.state.valueOrNull?.id, 'patient-a');
      expect(notifier.sessionRestoreFailed, isFalse);
    });

    test('auth stream changes identity and logout clears the user', () async {
      repository.restores[0].complete(_user('patient-a'));
      await _flush();
      events.add(_user('patient-b'));
      expect(notifier.state.valueOrNull?.id, 'patient-b');

      await notifier.logout();
      expect(repository.signOutCalled, isTrue);
      expect(notifier.state.valueOrNull, isNull);
    });
  });
}

Future<void> _flush() => Future<void>.delayed(Duration.zero);

UserModel _user(String id) => UserModel(
      id: id,
      name: 'Patient',
      email: '$id@example.test',
      medicalId: id,
      dateOfBirth: DateTime(2000),
    );

class _FakeAuthRepository implements AuthRepository {
  _FakeAuthRepository(this.events) : restores = [Completer<UserModel?>()];

  final StreamController<UserModel?> events;
  final List<Completer<UserModel?>> restores;
  int restoreCalls = 0;
  bool signOutCalled = false;

  @override
  Stream<UserModel?> get authChanges => events.stream;

  @override
  Future<UserModel?> restoreSession() => restores[restoreCalls++].future;

  @override
  Future<void> signOut() async {
    signOutCalled = true;
    events.add(null);
  }

  @override
  Future<UserModel> login(String email, String password) async =>
      _user('logged-in');

  @override
  Future<UserModel> register(
          String name, String email, String password) async =>
      _user('registered');

  @override
  Future<bool> verifyOtp(String code) async => code.length == 6;
}
