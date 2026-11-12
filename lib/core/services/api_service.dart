import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import '../constants/endpoints.dart';

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  const ApiException(this.message, {this.statusCode});
  @override
  String toString() => message;
}

class ApiResponse<T> {
  final T data;
  final int statusCode;
  const ApiResponse(this.data, {this.statusCode = 200});
}

abstract class ApiClient {
  Future<ApiResponse<T>> get<T>(String path);
  Future<ApiResponse<T>> post<T>(String path, Map<String, dynamic> body);
}

class HttpApiClient implements ApiClient {
  HttpApiClient({
    required this.accessToken,
    this.refreshAccessToken,
    http.Client? client,
  }) : _client = client ?? http.Client();
  final Future<String?> Function() accessToken;

  /// Refreshes the Supabase session after an API rejects an otherwise cached
  /// access token. It is called at most once per request.
  final Future<String?> Function()? refreshAccessToken;
  final http.Client _client;
  Future<Map<String, String>> _headers() async {
    final token = await accessToken();
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token'
    };
  }

  Uri _uri(String path) => Uri.parse('$apiBaseUrl$path');
  Future<ApiResponse<T>> _request<T>(
      Future<http.Response> Function() call) async {
    try {
      // A cold free-tier backend can take longer than a normal interactive
      // request to wake. The server now batches appointment relationships,
      // while this avoids misrepresenting a still-running request as empty.
      var response = await call().timeout(const Duration(seconds: 30));
      if (response.statusCode == 401 && refreshAccessToken != null) {
        // Supabase can reject a token before the local expiry check considers
        // it expired (for example after session revocation or clock drift).
        // Refresh and retry once; never loop or downgrade authorization.
        final refreshed = await refreshAccessToken!();
        if (refreshed != null && refreshed.isNotEmpty) {
          response = await call().timeout(const Duration(seconds: 30));
        }
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(response.body, statusCode: response.statusCode);
      }
      return ApiResponse<T>(jsonDecode(response.body) as T,
          statusCode: response.statusCode);
    } on TimeoutException {
      throw const ApiException('The service is taking too long. Please retry.');
    } on http.ClientException {
      throw const ApiException('You appear to be offline.');
    }
  }

  @override
  Future<ApiResponse<T>> get<T>(String path) async => _request<T>(
      () async => _client.get(_uri(path), headers: await _headers()));
  @override
  Future<ApiResponse<T>> post<T>(
          String path, Map<String, dynamic> body) async =>
      _request<T>(() async => _client.post(_uri(path),
          headers: await _headers(), body: jsonEncode(body)));
}

class ApiService {
  final ApiClient client;
  const ApiService(this.client);
}
