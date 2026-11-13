import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/models/entities.dart';
import 'package:verity_scribe/utils/appointment_visibility.dart';

LiveAppointment appointment(String date, String time,
        {String status = 'scheduled'}) =>
    LiveAppointment(
      id: '$date-$time-$status',
      doctorName: 'Doctor',
      hospital: 'Hospital',
      department: 'Department',
      status: status,
      consultationType: 'in_person',
      date: date,
      time: time,
    );

void main() {
  group('home appointment visibility', () {
    final now = DateTime(2026, 11, 11, 10, 0);

    test('today section includes only future, non-ended appointments', () {
      final result = todaysUpcomingAppointments([
        appointment('2026-11-11', '09:00:00'),
        appointment('2026-11-11', '10:00:00'),
        appointment('2026-11-11', '11:00:00'),
        appointment('2026-11-11', '12:00:00', status: 'completed'),
        appointment('2026-11-11', '13:00:00', status: 'done'),
        appointment('2026-11-12', '09:00:00'),
      ], now);

      expect(result.map((item) => item.time), ['11:00:00']);
    });

    test('upcoming count excludes past, terminal and malformed entries', () {
      final result = upcomingAppointments([
        appointment('2026-11-10', '11:00:00'),
        appointment('2026-11-12', '11:00:00'),
        appointment('2026-11-12', '12:00:00', status: 'cancelled'),
        appointment('not-a-date', '12:00:00'),
      ], now);

      expect(result.map((item) => item.time), ['11:00:00']);
    });

    test('parses 12-hour appointment times correctly', () {
      expect(appointmentStartAt(appointment('2026-11-11', '12:30 AM')),
          DateTime(2026, 11, 11, 0, 30));
      expect(appointmentStartAt(appointment('2026-11-11', '12:30 PM')),
          DateTime(2026, 11, 11, 12, 30));
    });
  });
}
