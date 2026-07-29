import '../models/entities.dart';
import '../core/services/api_service.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

abstract class AuthRepository {
  Future<UserModel> login(String email, String password);
  Future<UserModel> register(String name, String email, String password);
  Future<bool> verifyOtp(String code);
}


class SupabaseAuthRepository implements AuthRepository {
  SupabaseAuthRepository(this._auth);
  final GoTrueClient _auth;
  UserModel _model(User user) => UserModel(
      id: user.id,
      name: (user.userMetadata?['full_name'] ?? 'Patient') as String,
      email: user.email ?? '',
      medicalId: user.id,
      dateOfBirth: DateTime(2000));
  @override
  Future<UserModel> login(String email, String password) async {
    final result =
        await _auth.signInWithPassword(email: email, password: password);
    if (result.user == null) throw const ApiException('Unable to sign in.');
    return _model(result.user!);
  }

  @override
  Future<UserModel> register(String name, String email, String password) async {
    final result = await _auth
        .signUp(email: email, password: password, data: {'full_name': name});
    if (result.user == null)
      throw const ApiException('Unable to create account.');
    return _model(result.user!);
  }

  @override
  Future<bool> verifyOtp(String code) async => code.length == 6;
}

abstract class HealthRepository {
  Future<List<Medicine>> medicines();
  Future<List<Consultation>> consultations();
  Future<Appointment> nextAppointment();
  Future<HealthMetrics> healthMetrics();
  Future<List<Prescription>> prescriptions();
  Future<List<Report>> reports();
}

class ApiHealthRepository implements HealthRepository {
  const ApiHealthRepository(this._client);
  final ApiClient _client;
  Map<String, dynamic> _row(dynamic value) =>
      Map<String, dynamic>.from(value as Map);
  List<Map<String, dynamic>> _rows(dynamic value) {
    final root = _row(value);
    return (root['data'] as List? ?? const []).map(_row).toList();
  }

  @override
  Future<List<Medicine>> medicines() async =>
      _rows((await _client.get<dynamic>('/medicines')).data)
          .map((x) => Medicine(
              id: x['id'] as String,
              name: (x['name'] ?? 'Medicine') as String,
              dosage: (x['dosage'] ?? 'As prescribed') as String,
              purpose: (x['purpose'] ?? 'Treatment') as String,
              schedule: (x['schedule'] ?? 'Daily') as String,
              remaining: (x['remaining'] ?? 0) as int))
          .toList();
  @override
  Future<List<Consultation>> consultations() async =>
      _rows((await _client.get<dynamic>('/consultations')).data)
          .map((x) => Consultation(
              id: x['id'] as String,
              doctorName: (x['doctor_name'] ?? 'Care team') as String,
              summary:
                  (x['treatment_plan'] ?? 'No summary available.') as String,
              diagnosis: (x['diagnosis'] ?? 'Pending review') as String,
              date: DateTime.tryParse(
                      (x['consultation_date'] ?? x['created_at'] ?? '')
                          .toString()) ??
                  DateTime.now()))
          .toList();
  @override
  Future<Appointment> nextAppointment() async {
    final rows = _rows((await _client.get<dynamic>(
            '/appointments?sort=scheduled_at&descending=false&page_size=1'))
        .data);
    if (rows.isEmpty)
      throw const ApiException('No upcoming appointments found.');
    final x = rows.first;
    return Appointment(
        id: x['id'] as String,
        doctor: Doctor(
            id: (x['doctor_id'] ?? '') as String,
            name: (x['doctor_name'] ?? 'Care team') as String,
            specialty: (x['specialty'] ?? 'General medicine') as String,
            hospital: (x['hospital_name'] ?? 'Hospital') as String),
        scheduledAt: DateTime.tryParse((x['scheduled_at'] ?? '').toString()) ??
            DateTime.now(),
        status: (x['status'] ?? 'scheduled') as String);
  }

  @override
  Future<HealthMetrics> healthMetrics() async => const HealthMetrics(
      heartRate: 0, steps: 0, spo2: 0, sleep: Duration.zero);
  @override
  Future<List<Prescription>> prescriptions() async =>
      _rows((await _client.get<dynamic>('/prescriptions')).data)
          .map((row) => Prescription(id: row['id'] as String, consultationId: (row['consultation_id'] ?? '') as String, medicines: const []))
          .toList();
  @override
  Future<List<Report>> reports() async =>
      _rows((await _client.get<dynamic>('/reports')).data)
          .map((row) => Report(id: row['id'] as String, title: (row['title'] ?? row['report_type'] ?? 'Medical report').toString(), category: (row['category'] ?? 'Clinical').toString(), url: (row['file_url'] ?? '').toString(), date: DateTime.tryParse((row['report_date'] ?? row['created_at'] ?? '').toString()) ?? DateTime.now()))
          .toList();
}
