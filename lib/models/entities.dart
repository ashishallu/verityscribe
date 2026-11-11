class Patient {
  final String id, name, email, medicalId;
  final DateTime dateOfBirth;
  const Patient(
      {required this.id,
      required this.name,
      required this.email,
      required this.medicalId,
      required this.dateOfBirth});
  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'email': email,
        'medicalId': medicalId,
        'dateOfBirth': dateOfBirth.toIso8601String()
      };
  factory Patient.fromJson(Map<String, dynamic> j) => Patient(
      id: j['id'],
      name: j['name'],
      email: j['email'],
      medicalId: j['medicalId'],
      dateOfBirth: DateTime.parse(j['dateOfBirth']));
}

class Doctor {
  final String id, name, specialty, hospital;
  const Doctor(
      {required this.id,
      required this.name,
      required this.specialty,
      required this.hospital});
  Map<String, dynamic> toJson() =>
      {'id': id, 'name': name, 'specialty': specialty, 'hospital': hospital};
  factory Doctor.fromJson(Map<String, dynamic> j) => Doctor(
      id: j['id'],
      name: j['name'],
      specialty: j['specialty'],
      hospital: j['hospital']);
}

class Appointment {
  final String id;
  final Doctor doctor;
  final DateTime scheduledAt;
  final String status;
  const Appointment(
      {required this.id,
      required this.doctor,
      required this.scheduledAt,
      required this.status});
  Map<String, dynamic> toJson() => {
        'id': id,
        'doctor': doctor.toJson(),
        'scheduledAt': scheduledAt.toIso8601String(),
        'status': status
      };
  factory Appointment.fromJson(Map<String, dynamic> j) => Appointment(
      id: j['id'],
      doctor: Doctor.fromJson(j['doctor']),
      scheduledAt: DateTime.parse(j['scheduledAt']),
      status: j['status']);
}

class Medicine {
  final String id,
      name,
      dosage,
      purpose,
      schedule,
      doctor,
      howToTake,
      foodInstructions,
      sideEffects,
      notes;
  final int remaining, initialQuantity, dailyUsage;
  final bool taken;
  const Medicine(
      {required this.id,
      required this.name,
      required this.dosage,
      required this.purpose,
      required this.schedule,
      required this.remaining,
      this.doctor = 'Not available',
      this.howToTake = 'Not available',
      this.foodInstructions = 'Not available',
      this.sideEffects = 'Not available',
      this.notes = 'Not available',
      this.initialQuantity = 0,
      this.dailyUsage = 0,
      this.taken = false});
  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'dosage': dosage,
        'purpose': purpose,
        'schedule': schedule,
        'remaining': remaining,
        'initialQuantity': initialQuantity,
        'dailyUsage': dailyUsage,
        'taken': taken
      };
  factory Medicine.fromJson(Map<String, dynamic> j) => Medicine(
      id: j['id'],
      name: j['name'],
      dosage: j['dosage'],
      purpose: j['purpose'],
      schedule: j['schedule'],
      remaining: (j['remaining'] as num?)?.toInt() ?? 0,
      initialQuantity: (j['initialQuantity'] as num?)?.toInt() ?? 0,
      dailyUsage: (j['dailyUsage'] as num?)?.toInt() ?? 0,
      taken: j['taken'] == true);
}

class Prescription {
  final String id, consultationId;
  final List<Medicine> medicines;
  const Prescription(
      {required this.id,
      required this.consultationId,
      required this.medicines});
  Map<String, dynamic> toJson() => {
        'id': id,
        'consultationId': consultationId,
        'medicines': medicines.map((e) => e.toJson()).toList()
      };
}

class Consultation {
  final String id, doctorName, summary, hospital, diagnosis, status;
  final DateTime date;
  const Consultation(
      {required this.id,
      required this.doctorName,
      required this.summary,
      required this.date,
      this.hospital = 'Not available',
      this.diagnosis = 'Not available',
      this.status = 'Not available'});
  Map<String, dynamic> toJson() => {
        'id': id,
        'doctorName': doctorName,
        'summary': summary,
        'hospital': hospital,
        'diagnosis': diagnosis,
        'status': status,
        'date': date.toIso8601String()
      };
}

class AppNotification {
  final String id, title, body, type;
  final DateTime createdAt;
  final bool isRead;
  const AppNotification(
      {required this.id,
      required this.title,
      required this.body,
      required this.type,
      required this.createdAt,
      this.isRead = false});
  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'body': body,
        'type': type,
        'createdAt': createdAt.toIso8601String(),
        'isRead': isRead
      };
}

class HealthMetrics {
  final int heartRate, steps;
  final double spo2;
  final Duration sleep;
  const HealthMetrics(
      {required this.heartRate,
      required this.steps,
      required this.spo2,
      required this.sleep});
  Map<String, dynamic> toJson() => {
        'heartRate': heartRate,
        'steps': steps,
        'spo2': spo2,
        'sleep': sleep.inMinutes
      };
}

class Report {
  final String id, title, category, url;
  final DateTime date;
  const Report(
      {required this.id,
      required this.title,
      required this.category,
      required this.url,
      required this.date});
  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'category': category,
        'url': url,
        'date': date.toIso8601String()
      };
}

typedef UserModel = Patient;

class Hospital {
  final String id, name;
  const Hospital({required this.id, required this.name});
  factory Hospital.fromJson(Map<String, dynamic> j) => Hospital(
      id: j['id'].toString(), name: (j['name'] ?? 'Hospital').toString());
}

class Department {
  final String id, hospitalId, name;
  const Department(
      {required this.id, required this.hospitalId, required this.name});
  factory Department.fromJson(Map<String, dynamic> j) => Department(
      id: j['id'].toString(),
      hospitalId: (j['hospital_id'] ?? '').toString(),
      name: (j['name'] ?? 'Department').toString());
}

class DoctorDirectoryItem {
  final String id,
      firstName,
      lastName,
      specialization,
      qualification,
      hospital,
      department,
      hospitalId,
      departmentId;
  final int experience;
  final num fee, rating;
  final bool available;
  const DoctorDirectoryItem(
      {required this.id,
      required this.firstName,
      required this.lastName,
      required this.specialization,
      required this.qualification,
      required this.experience,
      required this.fee,
      required this.rating,
      required this.hospital,
      required this.department,
      required this.hospitalId,
      required this.departmentId,
      required this.available});
  String get name => '${firstName.trim()} ${lastName.trim()}'.trim();
  factory DoctorDirectoryItem.fromJson(Map<String, dynamic> j) {
    final h = Map<String, dynamic>.from(j['hospital'] as Map? ?? {}),
        d = Map<String, dynamic>.from(j['department'] as Map? ?? {});
    return DoctorDirectoryItem(
        id: j['id'].toString(),
        firstName: (j['first_name'] ?? '').toString(),
        lastName: (j['last_name'] ?? '').toString(),
        specialization: (j['specialization'] ?? 'General medicine').toString(),
        qualification: (j['qualification'] ?? '').toString(),
        experience: (j['experience_years'] as num? ?? 0).toInt(),
        fee: j['consultation_fee_inr'] as num? ?? 0,
        rating: j['rating'] as num? ?? 0,
        hospital: (h['name'] ?? 'Hospital').toString(),
        department: (d['name'] ?? 'Department').toString(),
        hospitalId: (j['hospital_id'] ?? h['id'] ?? '').toString(),
        departmentId: (j['department_id'] ?? d['id'] ?? '').toString(),
        available: j['is_available'] == true);
  }
}

class LiveAppointment {
  final String id,
      doctorName,
      hospital,
      department,
      status,
      consultationType,
      date,
      time;
  final DateTime? createdAt, serverExpiresAt;

  const LiveAppointment({
    required this.id,
    required this.doctorName,
    required this.hospital,
    required this.department,
    required this.status,
    required this.consultationType,
    required this.date,
    required this.time,
    this.createdAt,
    this.serverExpiresAt,
  });

  /// Supplied by the backend from the appointment's scheduled start time.
  /// Missing server expiry fails closed; the client must not re-derive it from
  /// the booking creation timestamp or its local timezone.
  DateTime? get expiresAt => serverExpiresAt?.toUtc() ??
      _scheduledLocalDateTime()?.add(const Duration(minutes: 30)).toUtc();

  /// The backend defines expiry as scheduled start + 30 minutes. Deriving the
  /// start from its UTC expiry keeps home filtering independent of device TZ.
  DateTime? get scheduledStartAt => serverExpiresAt != null
      ? serverExpiresAt!.toUtc().subtract(const Duration(minutes: 30))
      : _scheduledLocalDateTime()?.toUtc();

  DateTime? _scheduledLocalDateTime() {
    final dateParts = date.trim().split('-');
    final time = RegExp(r'^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?$',
            caseSensitive: false)
        .firstMatch(this.time.trim());
    if (dateParts.length != 3 || time == null) return null;
    final year = int.tryParse(dateParts[0]);
    final month = int.tryParse(dateParts[1]);
    final day = int.tryParse(dateParts[2]);
    var hour = int.tryParse(time.group(1)!);
    final minute = int.tryParse(time.group(2)!);
    final second = int.tryParse(time.group(3) ?? '0');
    if (year == null || month == null || day == null || hour == null ||
        minute == null || second == null) return null;
    final meridiem = time.group(4)?.toUpperCase();
    if (meridiem != null) hour = (hour % 12) + (meridiem == 'PM' ? 12 : 0);
    if (hour > 23 || minute > 59 || second > 59) return null;
    final result = DateTime(year, month, day, hour, minute, second);
    return result.year == year && result.month == month && result.day == day
        ? result
        : null;
  }

  bool isUpcomingAt(DateTime now) {
    final start = scheduledStartAt;
    final current = now.toUtc();
    return start != null &&
        current.isBefore(start) &&
        current.isBefore(expiresAt!) &&
        !{
          'cancelled',
          'canceled',
          'completed',
          'complete',
          'ended',
          'finished',
          'in_progress',
          'in-progress',
          'started',
          'no_show',
          'no-show',
          'expired',
          'rejected',
          'declined',
        }.contains(status.trim().toLowerCase());
  }

  bool get isUpcoming => isUpcomingAt(DateTime.now());

  bool get isExpired {
    final expiry = expiresAt;
    return expiry == null || !DateTime.now().toUtc().isBefore(expiry);
  }

  Duration? get timeRemaining {
    final expiry = expiresAt;
    if (expiry == null) return null;
    final remaining = expiry.difference(DateTime.now().toUtc());
    return remaining.isNegative ? Duration.zero : remaining;
  }

  factory LiveAppointment.fromJson(Map<String, dynamic> j) {
    final a = Map<String, dynamic>.from(j['appointment'] as Map? ?? j);
    final d = Map<String, dynamic>.from(j['doctor'] as Map? ?? {});
    final p = Map<String, dynamic>.from(d['profiles'] as Map? ?? {});
    final h = Map<String, dynamic>.from(d['hospitals'] as Map? ?? {});
    final dep = Map<String, dynamic>.from(d['departments'] as Map? ?? {});
    return LiveAppointment(
      id: a['id'].toString(),
      doctorName: '${p['first_name'] ?? ''} ${p['last_name'] ?? ''}'.trim(),
      hospital: (h['name'] ?? '').toString(),
      department: (dep['name'] ?? '').toString(),
      status: (a['status'] ?? '').toString(),
      consultationType: (a['consultation_type'] ?? '').toString(),
      date: (a['appointment_date'] ?? '').toString(),
      time: (a['appointment_time'] ?? '').toString(),
      createdAt: DateTime.tryParse(a['created_at']?.toString() ?? '')?.toUtc(),
      serverExpiresAt:
          DateTime.tryParse(a['expires_at']?.toString() ?? '')?.toUtc(),
    );
  }
}

List<LiveAppointment> filterUpcomingAppointments(
  Iterable<LiveAppointment> appointments, {
  DateTime? now,
}) =>
    appointments
        .where((appointment) => appointment.isUpcomingAt(
              now ?? DateTime.now(),
            ))
        .toList()
      ..sort((a, b) => a.scheduledStartAt!.compareTo(b.scheduledStartAt!));
