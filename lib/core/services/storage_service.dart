import 'package:flutter_secure_storage/flutter_secure_storage.dart';
abstract class StorageService { Future<void> saveToken(String token); Future<String?> getToken(); Future<void> deleteToken(); }
class SecureStorageService implements StorageService {
  SecureStorageService({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();
  static const _key = 'verityscribe_access_token'; final FlutterSecureStorage _storage;
  @override Future<void> saveToken(String token) => _storage.write(key: _key, value: token);
  @override Future<String?> getToken() => _storage.read(key: _key);
  @override Future<void> deleteToken() => _storage.delete(key: _key);
}
