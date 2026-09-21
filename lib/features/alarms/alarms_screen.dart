import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../models/entities.dart';
import '../../providers/app_providers.dart';
import '../../theme/app_theme.dart';
import '../../widgets/ui.dart';

class AlarmsScreen extends ConsumerWidget {
  const AlarmsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final medicines = ref.watch(medicinesProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(medicinesProvider),
      child: medicines.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => _Retry(
          label: 'Unable to load prescribed medicines.',
          onRetry: () => ref.invalidate(medicinesProvider),
        ),
        data: (items) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 28),
          children: [
            Padding(
              padding: const EdgeInsets.all(20),
              child: Row(children: [
                Text('Medication planner',
                    style: Theme.of(context)
                        .textTheme
                        .headlineSmall
                        ?.copyWith(fontWeight: FontWeight.w800)),
                const Spacer(),
                IconButton(
                    onPressed: () => context.push('/scan'),
                    icon: const Icon(Icons.document_scanner_rounded)),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: _ProgressCard(total: items.length),
            ),
            const SectionTitle('Medication reminders'),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: items.isEmpty
                  ? const SoftCard(
                      child: Row(children: [
                        Icon(Icons.medication_outlined, color: AppTheme.blue),
                        SizedBox(width: 12),
                        Expanded(child: Text('No prescribed medicines are available for reminders.')),
                      ]),
                    )
                  : Column(
                      children: items
                          .map((medicine) => Padding(
                                padding: const EdgeInsets.only(bottom: 10),
                                child: _MedicineReminder(medicine: medicine),
                              ))
                          .toList(),
                    ),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 18, 20, 0),
              child: Text('Reminders use your current prescriptions. Alarm creation is available when a persisted reminder service is configured.', style: TextStyle(color: AppTheme.muted, fontSize: 12, height: 1.4)),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProgressCard extends StatelessWidget {
  const _ProgressCard({required this.total});
  final int total;
  @override
  Widget build(BuildContext context) => SoftCard(
        color: Theme.of(context).colorScheme.primaryContainer,
        child: Row(children: [
          SizedBox(width: 86, height: 86, child: Stack(alignment: Alignment.center, children: [const CircularProgressIndicator(strokeWidth: 9, backgroundColor: Colors.white54, color: AppTheme.emerald), Text('$total', style: const TextStyle(fontWeight: FontWeight.w800))])),
          const SizedBox(width: 17),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Your prescriptions', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)), Text(total == 0 ? 'No active prescribed medicines' : '$total medicine${total == 1 ? '' : 's'} available for reminders', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant, fontSize: 12)), const SizedBox(height: 9), const StatusPill('LIVE PRESCRIPTIONS', AppTheme.blue)])),
        ]),
      );
}

class _MedicineReminder extends StatelessWidget {
  const _MedicineReminder({required this.medicine});
  final Medicine medicine;
  @override
  Widget build(BuildContext context) => SoftCard(
        child: Row(children: [
          Container(width: 48, height: 48, decoration: BoxDecoration(color: AppTheme.blue.withValues(alpha: .12), borderRadius: BorderRadius.circular(15)), child: const Icon(Icons.medication_rounded, color: AppTheme.blue)),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(medicine.name, style: const TextStyle(fontWeight: FontWeight.w800)), Text([medicine.dosage, medicine.schedule].where((x) => x.trim().isNotEmpty).join(' • ').isEmpty ? 'Schedule not provided in prescription' : [medicine.dosage, medicine.schedule].where((x) => x.trim().isNotEmpty).join(' • '), style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant)), if (medicine.doctor.trim().isNotEmpty) Text(medicine.doctor, style: const TextStyle(fontSize: 11, color: AppTheme.blue, fontWeight: FontWeight.w700))])),
          const StatusPill('PRESCRIBED', AppTheme.emerald),
        ]),
      );
}

class _Retry extends StatelessWidget { const _Retry({required this.label, required this.onRetry}); final String label; final VoidCallback onRetry; @override Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [Text(label), TextButton(onPressed: onRetry, child: const Text('Retry'))]))); }

class AlarmReminderScreen extends StatelessWidget {
  const AlarmReminderScreen({super.key});
  @override
  Widget build(BuildContext context) => const Scaffold(
        body: Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text('Medication reminder actions are unavailable until a persisted reminder endpoint is configured.', textAlign: TextAlign.center),
          ),
        ),
      );
}
