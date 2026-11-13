import '../models/entities.dart';

/// Parses the appointment's local clinic date/time without applying the
/// device's timezone a second time. The API returns date and time separately.
DateTime? appointmentStartAt(LiveAppointment appointment) {
  final date = appointment.date.trim();
  final time = appointment.time.trim();
  final dateParts = date.split('-');
  if (dateParts.length != 3) return null;

  final year = int.tryParse(dateParts[0]);
  final month = int.tryParse(dateParts[1]);
  final day = int.tryParse(dateParts[2]);
  if (year == null || month == null || day == null) return null;

  final match = RegExp(
    r'^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?$',
    caseSensitive: false,
  ).firstMatch(time);
  if (match == null) return null;

  var hour = int.parse(match.group(1)!);
  final minute = int.parse(match.group(2)!);
  final second = int.tryParse(match.group(3) ?? '0') ?? 0;
  final meridiem = match.group(4)?.toUpperCase();
  if (meridiem != null) {
    if (hour < 1 || hour > 12) return null;
    hour = (hour % 12) + (meridiem == 'PM' ? 12 : 0);
  }

  if (hour > 23 || minute > 59 || second > 59) return null;
  try {
    final start = DateTime(year, month, day, hour, minute, second);
    if (start.year != year || start.month != month || start.day != day) {
      return null;
    }
    return start;
  } on ArgumentError {
    return null;
  }
}

bool isUpcomingAppointment(LiveAppointment appointment, DateTime now) {
  const inactive = {
    'cancelled',
    'canceled',
    'completed',
    'done',
    'finished',
    'ended',
    'expired',
    'no_show',
    'closed',
    'in_progress',
    'ongoing',
  };
  final status = appointment.status
      .trim()
      .toLowerCase()
      .replaceAll(RegExp(r'[\s-]+'), '_');
  if (inactive.contains(status)) return false;
  final start = appointmentStartAt(appointment);
  // Missing or malformed scheduling data fails closed rather than being
  // presented as an upcoming appointment.
  return start != null && start.isAfter(now);
}

List<LiveAppointment> upcomingAppointments(
  Iterable<LiveAppointment> appointments,
  DateTime now,
) {
  final result = appointments
      .where((appointment) => isUpcomingAppointment(appointment, now))
      .toList();
  result
      .sort((a, b) => appointmentStartAt(a)!.compareTo(appointmentStartAt(b)!));
  return result;
}

List<LiveAppointment> todaysUpcomingAppointments(
  Iterable<LiveAppointment> appointments,
  DateTime now,
) =>
    upcomingAppointments(appointments, now)
        .where((appointment) =>
            appointment.date.trim() ==
            '${now.year.toString().padLeft(4, '0')}-'
                '${now.month.toString().padLeft(2, '0')}-'
                '${now.day.toString().padLeft(2, '0')}')
        .toList();
