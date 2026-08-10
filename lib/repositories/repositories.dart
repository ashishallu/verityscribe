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
  Future<List<Map<String,dynamic>>> clinicalConsultations();
  Future<List<Map<String,dynamic>>> prescriptionsLive();
  Future<List<Map<String,dynamic>>> reportsLive();
  Future<Patient> patientProfile();
  Future<List<Hospital>> hospitals();
  Future<List<Department>> departments(String hospitalId);
  Future<List<DoctorDirectoryItem>> doctors();
  Future<List<LiveAppointment>> appointments();
  Future<LiveAppointment> bookAppointment({required String doctorId, required String hospitalId, required String date, required String time, required String consultationType, String? reason, String? notes});
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
  @override Future<List<Map<String,dynamic>>> clinicalConsultations() async => _rows((await _client.get<dynamic>('/me/consultations')).data);
  @override Future<List<Map<String,dynamic>>> prescriptionsLive() async => _rows((await _client.get<dynamic>('/me/prescriptions')).data);
  @override Future<List<Map<String,dynamic>>> reportsLive() async => _rows((await _client.get<dynamic>('/me/reports')).data);
  @override Future<Patient> patientProfile() async { final profile=_row((await _client.get<dynamic>('/me')).data)['data']; final x=_row((await _client.get<dynamic>('/me/patient')).data)['data']; return Patient(id:x['id'].toString(),name:'${profile['first_name']??''} ${profile['last_name']??''}'.trim(),email:(profile['email']??'').toString(),medicalId:(x['mrn']??x['id']).toString(),dateOfBirth:DateTime.tryParse((x['date_of_birth']??profile['date_of_birth']??'2000-01-01').toString())??DateTime(2000)); }
  @override Future<List<Hospital>> hospitals() async => _rows((await _client.get<dynamic>('/hospitals')).data).map(Hospital.fromJson).toList();
  @override Future<List<Department>> departments(String hospitalId) async => _rows((await _client.get<dynamic>('/hospitals/$hospitalId/departments')).data).map(Department.fromJson).toList();
  @override Future<List<DoctorDirectoryItem>> doctors() async => _rows((await _client.get<dynamic>('/doctors')).data).map(DoctorDirectoryItem.fromJson).toList();
  @override Future<List<LiveAppointment>> appointments() async { final root=_row((await _client.get<dynamic>('/me/appointments')).data); return (root['data'] as List? ?? const []).map((x)=>LiveAppointment.fromJson(_row(x))).toList(); }
  @override Future<LiveAppointment> bookAppointment({required String doctorId,required String hospitalId,required String date,required String time,required String consultationType,String? reason,String? notes}) async { final root=_row((await _client.post<dynamic>('/appointments',{'doctor_id':doctorId,'hospital_id':hospitalId,'appointment_date':date,'appointment_time':time,'consultation_type':consultationType,if(reason?.trim().isNotEmpty==true)'reason_for_visit':reason,if(notes?.trim().isNotEmpty==true)'notes':notes})).data); return LiveAppointment.fromJson(_row(root['data'])); }

  @override
  Future<List<Medicine>> medicines() async {
    final prescriptions = _rows((await _client.get<dynamic>('/me/prescriptions')).data);
    return prescriptions.expand((prescription) => (prescription['items'] as List? ?? const []).map((item) {
      final row = _row(item);
      final medicine = _row(row['medicine'] ?? const {});
      final quantity = (row['quantity'] as num? ?? 0).toInt();
      return Medicine(id: row['medicine_id'].toString(), name: (medicine['name'] ?? '').toString(), dosage: (row['dosage'] ?? '').toString(), purpose: (medicine['description'] ?? '').toString(), schedule: (row['frequency'] ?? '').toString(), remaining: quantity, initialQuantity: quantity, dailyUsage: 1);
    })).toList();
  }
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
