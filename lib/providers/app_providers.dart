import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/entities.dart';
import '../repositories/repositories.dart';
import '../core/services/api_service.dart';
import '../core/services/supabase_auth_service.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

final supabaseAuthProvider = Provider((_) => SupabaseAuthService());
final apiClientProvider = Provider<ApiClient>((ref) =>
    HttpApiClient(accessToken: ref.read(supabaseAuthProvider).accessToken));
final authRepositoryProvider = Provider<AuthRepository>(
    (_) => SupabaseAuthRepository(Supabase.instance.client.auth));
final healthRepositoryProvider = Provider<HealthRepository>(
    (ref) => ApiHealthRepository(ref.read(apiClientProvider)));
final authStateProvider =
    StateNotifierProvider<AuthNotifier, AsyncValue<UserModel?>>(
        (ref) => AuthNotifier(ref.read(authRepositoryProvider)));

class AuthNotifier extends StateNotifier<AsyncValue<UserModel?>> {
  final AuthRepository _repo;
  AuthNotifier(this._repo) : super(const AsyncData(null));
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
  void logout() => state = const AsyncData(null);
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
final patientProfileProvider = FutureProvider<Patient>((ref) => ref.read(healthRepositoryProvider).patientProfile());
final hospitalsProvider = FutureProvider<List<Hospital>>((ref) => ref.read(healthRepositoryProvider).hospitals());
final departmentsProvider = FutureProvider.family<List<Department>, String>((ref, hospitalId) => ref.read(healthRepositoryProvider).departments(hospitalId));
final doctorsDirectoryProvider = FutureProvider<List<DoctorDirectoryItem>>((ref) => ref.read(healthRepositoryProvider).doctors());
final appointmentsLiveProvider = FutureProvider<List<LiveAppointment>>((ref) => ref.read(healthRepositoryProvider).appointments());
