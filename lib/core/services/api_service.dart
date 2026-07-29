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
  HttpApiClient({required this.accessToken, http.Client? client})
      : _client = client ?? http.Client();
  final Future<String?> Function() accessToken;
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
      final response = await call().timeout(const Duration(seconds: 12));
      if (response.statusCode < 200 || response.statusCode >= 300)
        throw ApiException(response.body, statusCode: response.statusCode);
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

class MockApiClient implements ApiClient {
  @override
  Future<ApiResponse<T>> get<T>(String path) async {
    await Future.delayed(const Duration(milliseconds: 650));
    throw const ApiException('Mock client requires a repository response.');
  }

  @override
  Future<ApiResponse<T>> post<T>(String path, Map<String, dynamic> body) async {
    await Future.delayed(const Duration(milliseconds: 650));
    throw const ApiException('Mock client requires a repository response.');
  }
}

class ApiService {
  final ApiClient client;
  const ApiService(this.client);
}
