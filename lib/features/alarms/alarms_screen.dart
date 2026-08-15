import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/app_providers.dart';

class AlarmsScreen extends ConsumerWidget {
  const AlarmsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(medicinesProvider);
    return Scaffold(appBar: AppBar(title: const Text('Medication reminders')), body: state.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (_, __) => Center(child: TextButton(onPressed: () => ref.invalidate(medicinesProvider), child: const Text('Unable to load prescriptions. Retry'))),
      data: (items) => items.isEmpty ? const Center(child: Text('No medication reminders available.')) : ListView(padding: const EdgeInsets.all(16), children: items.map((medicine) => Card(child: ListTile(title: Text(medicine.name), subtitle: Text('${medicine.dosage}\n${medicine.schedule}')))).toList()),
    ));
  }
}

class AlarmReminderScreen extends StatelessWidget {
  const AlarmReminderScreen({super.key});
  @override
  Widget build(BuildContext context) => const Scaffold(body: Center(child: Padding(padding: EdgeInsets.all(24), child: Text('Medication reminder actions are unavailable until a persisted reminder endpoint is configured.', textAlign: TextAlign.center))));
}
