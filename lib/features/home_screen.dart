import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/app_providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(patientProfileProvider);
    final appointments = ref.watch(appointmentsLiveProvider);
    final medicines = ref.watch(medicinesProvider);
    final reports = ref.watch(patientReportsProvider);
    return RefreshIndicator(
      onRefresh: () async { ref.invalidate(patientProfileProvider); ref.invalidate(appointmentsLiveProvider); ref.invalidate(medicinesProvider); ref.invalidate(patientReportsProvider); },
      child: ListView(padding: const EdgeInsets.all(20), children: [
        profile.when(loading: () => const LinearProgressIndicator(), error: (_, __) => const Text('Unable to load profile.'), data: (patient) => Text('Welcome, ${patient.name.isEmpty ? 'Patient' : patient.name}', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800))),
        const SizedBox(height: 20),
        Card(child: ListTile(title: const Text('Secure voice consultation'), subtitle: const Text('Doctor review is required for clinical drafts.'), trailing: const Icon(Icons.mic), onTap: () => context.go('/record'))),
        const SizedBox(height: 16),
        appointments.when(loading: () => const LinearProgressIndicator(), error: (_, __) => const ListTile(title: Text('Appointments unavailable')), data: (items) => Card(child: ListTile(title: Text(items.isEmpty ? 'No upcoming appointments' : items.first.doctorName), subtitle: Text(items.isEmpty ? 'No records available.' : '${items.first.date} ${items.first.time}\n${items.first.department} • ${items.first.status}')))),
        const SizedBox(height: 12),
        medicines.when(loading: () => const LinearProgressIndicator(), error: (_, __) => const ListTile(title: Text('Medicines unavailable')), data: (items) => Card(child: ListTile(title: Text('${items.length} prescribed medicine${items.length == 1 ? '' : 's'}'), subtitle: const Text('From your live prescriptions'), onTap: () => context.go('/medicines')))),
        const SizedBox(height: 12),
        reports.when(loading: () => const LinearProgressIndicator(), error: (_, __) => const ListTile(title: Text('Reports unavailable')), data: (items) => Card(child: ListTile(title: Text('${items.length} report${items.length == 1 ? '' : 's'}'), subtitle: const Text('From your live clinical records'), onTap: () => context.push('/reports-live')))),
      ]),
    );
  }
}
