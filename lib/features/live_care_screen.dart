import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/app_providers.dart';
import '../models/entities.dart';

class AppointmentBookingScreen extends ConsumerStatefulWidget {
  const AppointmentBookingScreen({super.key});
  @override
  ConsumerState<AppointmentBookingScreen> createState() =>
      _AppointmentBookingState();
}

class _AppointmentBookingState extends ConsumerState<AppointmentBookingScreen> {
  Hospital? hospital;
  Department? department;
  DoctorDirectoryItem? doctor;
  DateTime? date;
  TimeOfDay? time;
  String type = 'in_person';
  final reason = TextEditingController(), notes = TextEditingController(), timeText = TextEditingController();
  bool submitting = false, showPayment = false, booked = false;
  String? error;
  @override
  void dispose() {
    reason.dispose();
    notes.dispose();
    timeText.dispose();
    super.dispose();
  }

  void reviewBooking() {
    if (hospital == null || doctor == null || date == null || time == null) {
      setState(() => error = 'Select a hospital, doctor, date and time.');
      return;
    }
    if (reason.text.trim().isEmpty) {
      setState(() => error = 'Enter a reason for the visit.');
      return;
    }
    setState(() => showPayment = true);
  }

  Future<void> submit() async {
    setState(() => submitting = true);
    try {
      final d = date!, t = time!;
      await ref.read(healthRepositoryProvider).bookAppointment(
          doctorId: doctor!.id,
          hospitalId: hospital!.id,
          date:
              '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}',
          time:
              '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}',
          consultationType: type,
          reason: reason.text,
          notes: notes.text);
      ref.invalidate(appointmentsLiveProvider);
      if (mounted) {
        setState(() => submitting = false);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content:
                Text('Payment approved. Appointment booked successfully.')));
        setState(() => booked = true);
      }
    } catch (e) {
      if (mounted)
        setState(() {
          submitting = false;
          error = 'Unable to book the appointment. Please try another time.';
        });
    }
  }

  @override
  Widget build(BuildContext context) {
    final hospitals = ref.watch(hospitalsProvider),
        doctors = ref.watch(doctorsDirectoryProvider);
    final departments = hospital == null
        ? const AsyncValue<List<Department>>.data(<Department>[])
        : ref.watch(departmentsProvider(hospital!.id));
    final filtered = doctors.valueOrNull
            ?.where((d) =>
                d.available &&
                (hospital == null || d.hospitalId == hospital!.id) &&
                (department == null || d.departmentId == department!.id))
            .toList() ??
        <DoctorDirectoryItem>[];
    if (booked)
      return Scaffold(
          backgroundColor: const Color(0xFFF5F7FC),
          body: Center(child: Padding(padding: const EdgeInsets.all(28), child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(width: 84, height: 84, decoration: const BoxDecoration(color: Color(0xFFDDF7EC), shape: BoxShape.circle), child: const Icon(Icons.check_rounded, size: 52, color: Color(0xFF11845B))),
            const SizedBox(height: 24),
            const Text('Appointment booked', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: Color(0xFF172B4D))),
            const SizedBox(height: 10),
            Text('Your appointment with ${doctor?.name ?? 'your doctor'} is confirmed for ${date!.toLocal().toString().split(' ').first} at ${time!.format(context)}.', textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFF667085), height: 1.5)),
            const SizedBox(height: 28),
            SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: () { ref.invalidate(appointmentsLiveProvider); context.go('/home'); }, icon: const Icon(Icons.home_outlined), label: const Text('Return to home'))),
          ]))));
    if (showPayment)
      return Scaffold(
          appBar: AppBar(title: const Text('Confirm and pay')),
          body: ListView(padding: const EdgeInsets.all(20), children: [
            Text('Appointment with ${doctor?.name ?? 'doctor'}',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text(
                '${date!.toLocal().toString().split(' ').first} at ${time!.format(context)}'),
            const SizedBox(height: 20),
            Card(
                child: ListTile(
                    title: const Text('Demo payment'),
                    subtitle: Text('Consultation fee: ₹${doctor?.fee ?? 0}'),
                    trailing: const Icon(Icons.verified, color: Colors.green))),
            const SizedBox(height: 16),
            const Text(
                'This is a demo payment step. No real money will be charged.'),
            if (error != null)
              Text(error!, style: const TextStyle(color: Colors.red)),
            const SizedBox(height: 16),
            FilledButton(
                onPressed: submitting ? null : submit,
                child: Text(
                    submitting ? 'Processing…' : 'Pay and book appointment')),
            TextButton(
                onPressed: submitting
                    ? null
                    : () => setState(() => showPayment = false),
                child: const Text('Back'))
          ]));
    return Scaffold(
        backgroundColor: const Color(0xFFF5F7FC),
        appBar: AppBar(title: const Text('Book an appointment'), backgroundColor: Colors.transparent),
        body: ListView(padding: const EdgeInsets.fromLTRB(20, 8, 20, 32), children: [
          const Text('Find your care team', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          const Text('Choose a hospital, specialist and convenient time.', style: TextStyle(color: Color(0xFF667085))),
          const SizedBox(height: 20),
          _BookingCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const _FieldTitle('Hospital'),
          hospitals.when(
              loading: () => const LinearProgressIndicator(),
              error: (e, _) => const Text('Unable to load hospitals'),
              data: (items) => DropdownButtonFormField<Hospital>(
                  initialValue: hospital,
                  items: items
                      .map((x) =>
                          DropdownMenuItem(value: x, child: Text(x.name)))
                      .toList(),
                  onChanged: (x) => setState(() {
                        hospital = x;
                        department = null;
                        doctor = null;
                      }),
                  decoration: const InputDecoration(hintText: 'Select hospital', prefixIcon: Icon(Icons.local_hospital_outlined)))) ,
          const SizedBox(height: 18),
          const _FieldTitle('Department'),
          departments.when(
              loading: () => const LinearProgressIndicator(),
              error: (e, _) => const Text('Unable to load departments'),
              data: (items) => DropdownButtonFormField<Department>(
                  initialValue: department,
                  items: items
                      .map((x) =>
                          DropdownMenuItem(value: x, child: Text(x.name)))
                      .toList(),
                  onChanged: hospital == null
                      ? null
                      : (x) => setState(() {
                            department = x;
                            doctor = null;
                          }),
                  decoration: const InputDecoration(hintText: 'Select department', prefixIcon: Icon(Icons.category_outlined)))) ,
          const SizedBox(height: 18),
          const _FieldTitle('Doctor'),
          DropdownButtonFormField<DoctorDirectoryItem>(
              initialValue: doctor,
              items: filtered
                  .map((x) => DropdownMenuItem(
                      value: x, child: Text('${x.name} • ${x.specialization}')))
                  .toList(),
              onChanged: (x) => setState(() => doctor = x),
              decoration: const InputDecoration(hintText: 'Select available doctor', prefixIcon: Icon(Icons.person_outline))),
          if (doctor != null)
            Text(
                '${doctor!.hospital} • ${doctor!.department} • ₹${doctor!.fee}'),
          ])),
          const SizedBox(height: 14),
          _BookingCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const _FieldTitle('When would you like to visit?'),
          Row(children: [Expanded(child: OutlinedButton.icon(
              onPressed: () => showDatePicker(
                          context: context,
                          firstDate: DateTime.now(),
                          lastDate:
                              DateTime.now().add(const Duration(days: 365)),
                          initialDate: date ?? DateTime.now())
                      .then((x) {
                    if (x != null) setState(() => date = x);
                  }),
              icon: const Icon(Icons.calendar_today),
              label: Text(date == null
                  ? 'Select date'
                  : '${date!.toLocal().toString().split(' ').first}')))), const SizedBox(width: 12), Expanded(child: InkWell(onTap: () async { var selected = time ?? TimeOfDay.now(); final picked = await showCupertinoModalPopup<TimeOfDay>(context: context, builder: (context) => Container(height: 310, color: Colors.white, child: Column(children: [Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const Text('Choose time', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), TextButton(onPressed: () => Navigator.pop(context, selected), child: const Text('Done'))])), Expanded(child: CupertinoDatePicker(mode: CupertinoDatePickerMode.time, use24hFormat: false, initialDateTime: DateTime(2020, 1, 1, selected.hour, selected.minute), onDateTimeChanged: (value) => selected = TimeOfDay.fromDateTime(value)))]))); if (picked != null) setState(() { time = picked; timeText.text = picked.format(context); }); }, child: InputDecorator(decoration: const InputDecoration(labelText: 'Time *', prefixIcon: Icon(Icons.schedule_outlined)), child: Text(time == null ? 'Select time' : time!.format(context)))))]),
          ])),
          const SizedBox(height: 14),
          _BookingCard(child: Column(children: [
          DropdownButtonFormField<String>(
              initialValue: type,
              items: const [
                DropdownMenuItem(value: 'in_person', child: Text('In person')),
                DropdownMenuItem(value: 'video', child: Text('Video')),
                DropdownMenuItem(value: 'phone', child: Text('Phone')),
                DropdownMenuItem(value: 'chat', child: Text('Chat'))
              ],
              onChanged: (x) => setState(() => type = x ?? 'in_person'),
              decoration: const InputDecoration(labelText: 'Consultation type', prefixIcon: Icon(Icons.video_call_outlined))),
          TextField(
              controller: reason,
              maxLength: 300,
              decoration: const InputDecoration(labelText: 'Reason for visit *', hintText: 'Tell us what you need help with', prefixIcon: Icon(Icons.edit_note_outlined))),
          TextField(
              controller: notes,
              maxLength: 500,
              decoration: const InputDecoration(labelText: 'Notes', hintText: 'Anything else the doctor should know?', prefixIcon: Icon(Icons.notes_outlined))),
          if (error != null)
            Text(error!, style: const TextStyle(color: Colors.red)),
          FilledButton(
              onPressed: submitting ? null : reviewBooking,
              child: const Text('Review and continue to payment'))
        ])),
        ]));
  }
}

class _FieldTitle extends StatelessWidget { const _FieldTitle(this.text); final String text; @override Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(text, style: const TextStyle(fontWeight: FontWeight.w700, color: Color(0xFF24324A)))); }
class _BookingCard extends StatelessWidget { const _BookingCard({required this.child}); final Widget child; @override Widget build(BuildContext context) => Container(padding: const EdgeInsets.all(18), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22), boxShadow: const [BoxShadow(color: Color(0x120D1B3E), blurRadius: 18, offset: Offset(0, 8))]), child: child); }

class DoctorsDirectoryScreen extends ConsumerWidget {
  const DoctorsDirectoryScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(doctorsDirectoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Find a doctor'), actions: [
        IconButton(
            onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(
                    builder: (_) => const AppointmentBookingScreen())),
            icon: const Icon(Icons.calendar_month))
      ]),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Error(
            message: error.toString(),
            retry: () => ref.invalidate(doctorsDirectoryProvider)),
        data: (doctors) => doctors.isEmpty
            ? const Center(child: Text('No doctors are available right now.'))
            : RefreshIndicator(
                onRefresh: () => ref.refresh(doctorsDirectoryProvider.future),
                child: ListView.builder(
                  itemCount: doctors.length,
                  itemBuilder: (context, index) {
                    final doctor = doctors[index];
                    return Card(
                      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                      child: ListTile(
                        leading: const CircleAvatar(child: Icon(Icons.person)),
                        title:
                            Text(doctor.name.isEmpty ? 'Doctor' : doctor.name),
                        subtitle: Text(
                            '${doctor.specialization} • ${doctor.department}\n${doctor.hospital} • ${doctor.experience} years'),
                        isThreeLine: true,
                        trailing: Text(
                            doctor.available ? 'Available' : 'Unavailable',
                            style: TextStyle(
                                color: doctor.available
                                    ? Colors.green
                                    : Colors.grey)),
                      ),
                    );
                  },
                ),
              ),
      ),
    );
  }
}

class AppointmentsLiveScreen extends ConsumerWidget {
  const AppointmentsLiveScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appointmentsLiveProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My appointments')),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Error(
            message: error.toString(),
            retry: () => ref.invalidate(appointmentsLiveProvider)),
        data: (items) => items.isEmpty
            ? const Center(child: Text('No appointments yet.'))
            : RefreshIndicator(
                onRefresh: () => ref.refresh(appointmentsLiveProvider.future),
                child: ListView(
                  children: items
                      .map((item) => Card(
                            margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                            child: ListTile(
                              title: Text(item.doctorName.isEmpty
                                  ? 'Doctor'
                                  : item.doctorName),
                              subtitle: Text(
                                  '${item.date} ${item.time}\n${item.hospital} • ${item.department}'),
                              isThreeLine: true,
                              trailing: Text(item.status),
                            ),
                          ))
                      .toList(),
                ),
              ),
      ),
    );
  }
}

class _Error extends StatelessWidget {
  const _Error({required this.message, required this.retry});
  final String message;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Center(
      child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('Unable to load live data',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(message, textAlign: TextAlign.center),
            TextButton(onPressed: retry, child: const Text('Retry'))
          ])));
}
