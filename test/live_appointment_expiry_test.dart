import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/models/entities.dart';

void main() {
  test('appointment expiry uses server schedule-based expiry, not created_at',
      () {
    final now = DateTime.now().toUtc();
    final expiry = now.add(const Duration(minutes: 30));
    final appointment = LiveAppointment.fromJson({
      'id': 'appointment-1',
      'status': 'scheduled',
      'consultation_type': 'ai_virtual',
      'created_at': now.toIso8601String(),
      'appointment_date': '2035-01-01',
      'appointment_time': '12:00',
      'expires_at': expiry.toIso8601String(),
    });

    expect(appointment.expiresAt, expiry);
    expect(appointment.isExpired, isFalse);
    expect(appointment.timeRemaining!.inSeconds, inInclusiveRange(1, 1800));
  });

  test('server expiry timestamp is preferred and normalized from its offset',
      () {
    final appointment = LiveAppointment.fromJson({
      'id': 'appointment-server-expiry',
      'status': 'scheduled',
      'consultation_type': 'ai_virtual',
      'created_at': '2026-11-10T10:00:00Z',
      'expires_at': '2026-11-10T15:45:00+05:30',
    });

    expect(appointment.expiresAt, DateTime.utc(2026, 11, 10, 10, 15));
  });

  test(
      'appointment without server expiry fails closed regardless of creation time',
      () {
    final created =
        DateTime.now().toUtc().subtract(const Duration(minutes: 31));
    final appointment = LiveAppointment.fromJson({
      'id': 'appointment-old',
      'status': 'scheduled',
      'consultation_type': 'ai_chat',
      'created_at': created.toIso8601String(),
      'appointment_date': '2035-01-01',
      'appointment_time': '12:00',
    });

    expect(appointment.expiresAt, isNull);
    expect(appointment.isExpired, isTrue);
    expect(appointment.timeRemaining, isNull);
  });

  test('missing server-calculated expiry fails closed', () {
    final appointment = LiveAppointment.fromJson({
      'id': 'appointment-missing-created-at',
      'status': 'scheduled',
      'consultation_type': 'ai_virtual',
      'appointment_date': '2035-01-01',
      'appointment_time': '12:00',
    });

    expect(appointment.expiresAt, isNull);
    expect(appointment.isExpired, isTrue);
  });

  test('home appointment list includes only future, unexpired appointments',
      () {
    final now = DateTime.utc(2026, 11, 11, 12);
    LiveAppointment appointment(String id, DateTime start,
            {String status = 'scheduled'}) =>
        LiveAppointment.fromJson({
          'id': id,
          'status': status,
          'consultation_type': 'ai_virtual',
          'appointment_date': '2026-11-11',
          'appointment_time': '12:00',
          'expires_at':
              start.add(const Duration(minutes: 30)).toIso8601String(),
        });

    final upcoming = appointment('upcoming', now.add(const Duration(hours: 2)));
    final started =
        appointment('started', now.subtract(const Duration(minutes: 5)));
    final expired =
        appointment('expired', now.subtract(const Duration(hours: 2)));
    final canceled = appointment('cancelled', now.add(const Duration(hours: 1)),
        status: 'cancelled');
    final ended = appointment('ended', now.add(const Duration(hours: 1)),
        status: 'ended');
    final inProgress = appointment(
      'in-progress',
      now.add(const Duration(hours: 1)),
      status: 'in_progress',
    );
    final missingExpiry = LiveAppointment.fromJson({
      'id': 'missing-expiry',
      'status': 'scheduled',
      'consultation_type': 'ai_chat',
    });

    expect(
      filterUpcomingAppointments(
        [
          started,
          expired,
          canceled,
          ended,
          inProgress,
          missingExpiry,
          upcoming
        ],
        now: now,
      ).map((item) => item.id),
      ['upcoming'],
    );
  });
}
