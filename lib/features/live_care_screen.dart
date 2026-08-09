import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class DoctorsDirectoryScreen extends ConsumerWidget {
  const DoctorsDirectoryScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(doctorsDirectoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Find a doctor')),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Error(message: error.toString(), retry: () => ref.invalidate(doctorsDirectoryProvider)),
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
                        title: Text(doctor.name.isEmpty ? 'Doctor' : doctor.name),
                        subtitle: Text('${doctor.specialization} • ${doctor.department}\n${doctor.hospital} • ${doctor.experience} years'),
                        isThreeLine: true,
                        trailing: Text(doctor.available ? 'Available' : 'Unavailable', style: TextStyle(color: doctor.available ? Colors.green : Colors.grey)),
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
        error: (error, _) => _Error(message: error.toString(), retry: () => ref.invalidate(appointmentsLiveProvider)),
        data: (items) => items.isEmpty
            ? const Center(child: Text('No appointments yet.'))
            : RefreshIndicator(
                onRefresh: () => ref.refresh(appointmentsLiveProvider.future),
                child: ListView(
                  children: items.map((item) => Card(
                    margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                    child: ListTile(
                      title: Text(item.doctorName.isEmpty ? 'Doctor' : item.doctorName),
                      subtitle: Text('${item.date} ${item.time}\n${item.hospital} • ${item.department}'),
                      isThreeLine: true,
                      trailing: Text(item.status),
                    ),
                  )).toList(),
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
  Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [Text('Unable to load live data', style: Theme.of(context).textTheme.titleMedium), const SizedBox(height: 8), Text(message, textAlign: TextAlign.center), TextButton(onPressed: retry, child: const Text('Retry'))])));
}
