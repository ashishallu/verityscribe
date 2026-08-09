import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:supabase_flutter/supabase_flutter.dart';

const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8000/api/v1');

class DoctorAuthService {
  SupabaseClient get client => Supabase.instance.client;
  Session? get session => client.auth.currentSession;
  Future<AuthResponse> signIn(String email, String password) => client.auth.signInWithPassword(email: email, password: password);
  Future<void> signOut() => client.auth.signOut();
  Future<String?> token() async { final s=client.auth.currentSession; if(s==null)return null; if(s.isExpired){final r=await client.auth.refreshSession();return r.session?.accessToken;} return s.accessToken; }
}
class DoctorApiClient {
  DoctorApiClient(this.auth); final DoctorAuthService auth;
  Future<dynamic> request(String path,{String method='GET',Map<String,dynamic>? body}) async { final token=await auth.token(); if(token==null)throw Exception('SESSION_EXPIRED'); final response=await (method=='POST'?http.post(Uri.parse('$apiBaseUrl$path'),headers:{'Authorization':'Bearer $token','Content-Type':'application/json'},body:jsonEncode(body)):http.get(Uri.parse('$apiBaseUrl$path'),headers:{'Authorization':'Bearer $token'})).timeout(const Duration(seconds:15)); if(response.statusCode<200||response.statusCode>=300)throw Exception('${response.statusCode}'); return jsonDecode(response.body); }
}
class DoctorRepository { DoctorRepository(this.api); final DoctorApiClient api; Map<String,dynamic> _map(dynamic v)=>Map<String,dynamic>.from(v as Map); Future<Map<String,dynamic>> doctor() async=>_map(_map(await api.request('/me/doctor'))['data']); Future<List<Map<String,dynamic>>> appointments() async=>(_map(await api.request('/me/appointments'))['data'] as List).map(_map).toList(); Future<Map<String,dynamic>> consultation(Map<String,dynamic> payload) async=>_map(_map(await api.request('/consultations',method:'POST',body:payload))['data']); }
