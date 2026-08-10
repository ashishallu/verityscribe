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
class DoctorRepository { DoctorRepository(this.api); final DoctorApiClient api; Map<String,dynamic> _map(dynamic v)=>Map<String,dynamic>.from(v as Map); Future<Map<String,dynamic>> doctor() async=>_map(_map(await api.request('/me/doctor'))['data']); Future<List<Map<String,dynamic>>> appointments() async=>(_map(await api.request('/me/appointments'))['data'] as List).map(_map).toList(); Future<Map<String,dynamic>> consultation(Map<String,dynamic> payload) async=>_map(_map(await api.request('/consultations',method:'POST',body:payload))['data']); Future<Map<String,dynamic>> note(String id,String text) async=>_map(_map(await api.request('/consultations/$id/notes',method:'POST',body:{'note_text':text}))['data']); Future<Map<String,dynamic>> diagnosis(String id,String name) async=>_map(_map(await api.request('/consultations/$id/diagnoses',method:'POST',body:{'diagnosis_name':name}))['data']); Future<List<Map<String,dynamic>>> medicines(String search) async=>(_map(await api.request('/medicines?search=${Uri.encodeQueryComponent(search)}'))['data'] as List).map(_map).toList(); Future<Map<String,dynamic>> prescription(String id,String medicineId) async=>_map(_map(await api.request('/consultations/$id/prescription',method:'POST',body:{'items':[{'medicine_id':medicineId,'quantity':1,'dosage':'As directed','frequency':'Once daily'}]}))['data']); Future<Map<String,dynamic>> report(String id,String findings) async=>_map(_map(await api.request('/consultations/$id/reports',method:'POST',body:{'report_type':'discharge','report_date':DateTime.now().toIso8601String().split('T').first,'findings':findings}))['data']); }

extension DoctorAiRepository on DoctorRepository {
  Future<Map<String, dynamic>> ai(String consultationId) async =>
      Map<String, dynamic>.from(await api.request('/consultations/$consultationId/ai'));
  Future<Map<String, dynamic>> processAi(String consultationId, String transcriptId) async =>
      Map<String, dynamic>.from(await api.request('/consultations/$consultationId/ai/process', method: 'POST', body: {'voice_transcript_id': transcriptId}));
}

extension DoctorVoiceRepository on DoctorRepository {
  Future<dynamic> uploadVoice(String consultationId, String filePath) async {
    final token = await api.auth.token();
    if (token == null) throw Exception('SESSION_EXPIRED');
    final request = http.MultipartRequest('POST', Uri.parse('$apiBaseUrl/consultations/$consultationId/voice'));
    request.headers['Authorization'] = 'Bearer $token';
    request.files.add(await http.MultipartFile.fromPath('audio', filePath));
    final response = await request.send().timeout(const Duration(seconds: 60));
    final body = await response.stream.bytesToString();
    if (response.statusCode < 200 || response.statusCode >= 300) throw Exception('${response.statusCode}:$body');
    return jsonDecode(body);
  }
}
