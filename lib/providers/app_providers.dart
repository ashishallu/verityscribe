import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:async';
import '../models/entities.dart';
import '../repositories/repositories.dart';
import '../core/services/api_service.dart';
import '../core/services/supabase_auth_service.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

final supabaseAuthProvider = Provider((_) => SupabaseAuthService());
final apiClientProvider = Provider<ApiClient>((ref) {
  final auth = ref.read(supabaseAuthProvider);
  return HttpApiClient(
    accessToken: auth.accessToken,
    refreshAccessToken: auth.refreshAccessToken,
  );
});
final authRepositoryProvider = Provider<AuthRepository>(
    (_) => SupabaseAuthRepository(Supabase.instance.client.auth));
final healthRepositoryProvider = Provider<HealthRepository>(
    (ref) => ApiHealthRepository(ref.read(apiClientProvider)));
final authStateProvider =
    StateNotifierProvider<AuthNotifier, AsyncValue<UserModel?>>(
        (ref) => AuthNotifier(ref.read(authRepositoryProvider)));

class AuthNotifier extends StateNotifier<AsyncValue<UserModel?>> {
  final AuthRepository _repo;
  late final StreamSubscription<UserModel?> _authSubscription;
  bool _bootstrapping = true;
  bool _sessionRestoreFailed = false;
  bool _hasPendingAuthEvent = false;
  UserModel? _pendingAuthUser;

  bool get isBootstrapping => _bootstrapping;
  bool get sessionRestoreFailed => _sessionRestoreFailed;

  AuthNotifier(this._repo) : super(const AsyncLoading()) {
    _authSubscription = _repo.authChanges.listen(
      (user) {
        if (_bootstrapping) {
          _hasPendingAuthEvent = true;
          _pendingAuthUser = user;
          return;
        }
        state = AsyncData(user);
      },
      onError: (Object error, StackTrace stackTrace) {
        if (_bootstrapping) return;
        // A transient auth-stream error must not erase a restored session.
        if (state.valueOrNull == null) state = AsyncError(error, stackTrace);
      },
    );
    unawaited(restoreSession());
  }

  Future<void> restoreSession() async {
    _bootstrapping = true;
    _sessionRestoreFailed = false;
    _hasPendingAuthEvent = false;
    _pendingAuthUser = null;
    state = const AsyncLoading();
    try {
      final restoredUser = await _repo.restoreSession();
      if (!mounted) return;
      // An auth event may arrive while restoration is awaiting a token refresh.
      // Prefer the latest event (including a signed-out/null event) so a stale
      // restored session cannot overwrite a concurrent sign-out or account swap.
      final user = _hasPendingAuthEvent ? _pendingAuthUser : restoredUser;
      _bootstrapping = false;
      _sessionRestoreFailed = false;
      state = AsyncData(user);
    } catch (error, stackTrace) {
      if (!mounted) return;
      _bootstrapping = false;
      _sessionRestoreFailed = true;
      if (_hasPendingAuthEvent) {
        _sessionRestoreFailed = false;
        state = AsyncData(_pendingAuthUser);
      } else {
        state = AsyncError(error, stackTrace);
      }
    }
  }

  Future<bool> login(String email, String password) async {
    state = const AsyncLoading();
    try {
      state = AsyncData(await _repo.login(email, password));
      return true;
    } catch (e, st) {
      state = AsyncError(e, st);
      return false;
    }
  }

  Future<bool> register(String n, String e, String p) async {
    state = const AsyncLoading();
    try {
      state = AsyncData(await _repo.register(n, e, p));
      return true;
    } catch (e, st) {
      state = AsyncError(e, st);
      return false;
    }
  }

  Future<bool> verifyOtp(String code) => _repo.verifyOtp(code);
  Future<void> logout() => _repo.signOut();

  @override
  void dispose() {
    unawaited(_authSubscription.cancel());
    super.dispose();
  }
}

final loginProvider = Provider((ref) => ref.read(authStateProvider.notifier));
final registrationProvider =
    Provider((ref) => ref.read(authStateProvider.notifier));
final otpProvider = Provider((ref) => ref.read(authStateProvider.notifier));
final profileProvider = FutureProvider<UserModel>((ref) async =>
    ref.watch(authStateProvider).valueOrNull ??
    (throw StateError('Unauthenticated')));
final medicinesProvider = FutureProvider<List<Medicine>>(
    (ref) => ref.read(healthRepositoryProvider).medicines());
final appointmentsProvider = FutureProvider<Appointment>(
    (ref) => ref.read(healthRepositoryProvider).nextAppointment());
final recordsProvider = FutureProvider<List<Consultation>>(
    (ref) => ref.read(healthRepositoryProvider).consultations());
final prescriptionsProvider = FutureProvider<List<Prescription>>(
    (ref) => ref.read(healthRepositoryProvider).prescriptions());
final reportsProvider = FutureProvider<List<Report>>(
    (ref) => ref.read(healthRepositoryProvider).reports());
final patientProfileProvider = FutureProvider<Patient>(
    (ref) => ref.read(healthRepositoryProvider).patientProfile());
final patientProfileDataProvider =
    FutureProvider<Map<String, dynamic>>((ref) async {
  final client = ref.read(apiClientProvider);
  final profileBody =
      Map<String, dynamic>.from((await client.get<dynamic>('/me')).data as Map);
  final patientBody = Map<String, dynamic>.from(
      (await client.get<dynamic>('/me/patient')).data as Map);
  return {
    'profile': Map<String, dynamic>.from(profileBody['data'] as Map),
    'patient': Map<String, dynamic>.from(patientBody['data'] as Map)
  };
});
final hospitalsProvider = FutureProvider<List<Hospital>>(
    (ref) => ref.read(healthRepositoryProvider).hospitals());
final departmentsProvider = FutureProvider.family<List<Department>, String>(
    (ref, hospitalId) =>
        ref.read(healthRepositoryProvider).departments(hospitalId));
final doctorsDirectoryProvider = FutureProvider<List<DoctorDirectoryItem>>(
    (ref) => ref.read(healthRepositoryProvider).doctors());
final appointmentsLiveProvider = FutureProvider<List<LiveAppointment>>(
    (ref) => ref.read(healthRepositoryProvider).appointments());
final patientConsultationsProvider = FutureProvider<List<Map<String, dynamic>>>(
    (ref) => ref.read(healthRepositoryProvider).clinicalConsultations());
final patientPrescriptionsProvider = FutureProvider<List<Map<String, dynamic>>>(
    (ref) => ref.read(healthRepositoryProvider).prescriptionsLive());
final patientReportsProvider = FutureProvider<List<Map<String, dynamic>>>(
    (ref) => ref.read(healthRepositoryProvider).reportsLive());
final patientMedicalRecordProvider =
    FutureProvider<Map<String, dynamic>>((ref) async {
  final response = await ref
      .read(apiClientProvider)
      .get<dynamic>('/patients/me/medical-record');
  final body = Map<String, dynamic>.from(response.data as Map);
  return Map<String, dynamic>.from((body['data'] as Map?) ?? body);
});
final patientTimelineProvider = FutureProvider<List<dynamic>>((ref) async {
  final response =
      await ref.read(apiClientProvider).get<dynamic>('/patients/me/timeline');
  final body = Map<String, dynamic>.from(response.data as Map);
  return List<dynamic>.from((body['data'] as List?) ?? const []);
});
final patientInsuranceProvider =
    FutureProvider<Map<String, dynamic>>((ref) async {
  final response =
      await ref.read(apiClientProvider).get<dynamic>('/me/insurance');
  final body = Map<String, dynamic>.from(response.data as Map);
  return Map<String, dynamic>.from((body['data'] as Map?) ?? body);
});
final patientNotificationsProvider =
    FutureProvider<Map<String, dynamic>>((ref) async {
  final response =
      await ref.read(apiClientProvider).get<dynamic>('/notifications');
  final body = Map<String, dynamic>.from(response.data as Map);
  return Map<String, dynamic>.from(
      (body['data'] is List) ? body : ((body['data'] as Map?) ?? body));
});
final patientMedicationScheduleProvider =
    FutureProvider<List<dynamic>>((ref) async {
  final response = await ref
      .read(apiClientProvider)
      .get<dynamic>('/me/medication-schedules');
  final body = Map<String, dynamic>.from(response.data as Map);
  return List<dynamic>.from((body['data'] as List?) ?? const []);
});
final patientPharmacyOrdersProvider =
    FutureProvider<List<dynamic>>((ref) async {
  final response =
      await ref.read(apiClientProvider).get<dynamic>('/me/pharmacy-orders');
  final body = Map<String, dynamic>.from(response.data as Map);
  return List<dynamic>.from((body['data'] as List?) ?? const []);
});
