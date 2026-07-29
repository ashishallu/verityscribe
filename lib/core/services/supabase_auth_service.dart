import 'package:supabase_flutter/supabase_flutter.dart';
import 'storage_service.dart';

class SupabaseAuthService {
  SupabaseAuthService({StorageService? storage}) : _storage = storage ?? SecureStorageService();
  final StorageService _storage;
  SupabaseClient get client => Supabase.instance.client;
  Session? get session => client.auth.currentSession;
  Future<String?> accessToken() async {
    final current = session;
    if (current == null) return null;
    if (current.isExpired) await client.auth.refreshSession();
    final token = client.auth.currentSession?.accessToken;
    if (token != null) await _storage.saveToken(token);
    return token;
  }

  Future<AuthResponse> signIn(String email, String password) async {
    final response = await client.auth.signInWithPassword(email: email, password: password);
    if (response.session != null) await _storage.saveToken(response.session!.accessToken);
    return response;
  }
  Future<AuthResponse> signUp(String email, String password, String name) =>
      client.auth
          .signUp(email: email, password: password, data: {'full_name': name});
  Future<void> signOut() async { await client.auth.signOut(); await _storage.deleteToken(); }
  Stream<AuthState> get changes => client.auth.onAuthStateChange;
}
