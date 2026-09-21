import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/entities.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class RecordsScreen extends ConsumerWidget {
  const RecordsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final records = ref.watch(recordsProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(recordsProvider),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 28),
        children: [
          Padding(padding: const EdgeInsets.all(20), child: Text('Medical history', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800))),
          const SectionTitle('Consultations'),
          records.when(
            loading: () => const Center(child: Padding(padding: EdgeInsets.all(28), child: CircularProgressIndicator())),
            error: (_, __) => _Retry(onRetry: () => ref.invalidate(recordsProvider)),
            data: (items) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: items.isEmpty
                  ? const SoftCard(child: Text('No consultations available yet.'))
                  : Column(children: items.map((item) => Padding(padding: const EdgeInsets.only(bottom: 13), child: _ConsultationCard(item))).toList()),
            ),
          ),
          const SectionTitle('Complete health record'),
          Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: SoftCard(child: ListTile(contentPadding: EdgeInsets.zero, leading: const Icon(Icons.folder_copy_outlined, color: AppTheme.blue), title: const Text('View your clinical record', style: TextStyle(fontWeight: FontWeight.w800)), subtitle: const Text('Medical history, reports, insurance and more'), trailing: const Icon(Icons.chevron_right_rounded), onTap: () => context.go('/live-health-record')))),
        ],
      ),
    );
  }
}

class _ConsultationCard extends StatefulWidget { const _ConsultationCard(this.item); final Consultation item; @override State<_ConsultationCard> createState() => _ConsultationCardState(); }
class _ConsultationCardState extends State<_ConsultationCard> {
  bool expanded = false;
  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    return SoftCard(padding: EdgeInsets.zero, child: Column(children: [
      InkWell(onTap: () => setState(() => expanded = !expanded), child: Padding(padding: const EdgeInsets.all(18), child: Row(children: [
        Container(width: 46, height: 46, decoration: BoxDecoration(color: Theme.of(context).colorScheme.primaryContainer, borderRadius: BorderRadius.circular(15)), child: const Icon(Icons.description_rounded, color: AppTheme.blue)),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(item.doctorName.isEmpty ? 'Doctor details unavailable' : item.doctorName, style: const TextStyle(fontWeight: FontWeight.w800)), Text([item.hospital, _date(item.date)].where((x) => x.isNotEmpty).join(' • '), style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant)), const SizedBox(height: 7), Text(item.diagnosis.isEmpty ? 'Diagnosis not recorded' : item.diagnosis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)), const SizedBox(height: 7), StatusPill(item.status.isEmpty ? 'RECORDED' : item.status.toUpperCase(), AppTheme.emerald)])),
        Icon(expanded ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded),
      ]))),
      if (expanded) Padding(padding: const EdgeInsets.fromLTRB(18, 0, 18, 18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Divider(), const Text('Consultation summary', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)), const SizedBox(height: 8), Text(item.summary.isEmpty ? 'No treatment summary was recorded.' : item.summary, style: const TextStyle(height: 1.55, fontSize: 12)), const SizedBox(height: 16), FilledButton.icon(onPressed: () => context.go('/prescriptions-live'), icon: const Icon(Icons.medication_outlined), label: const Text('View linked prescriptions'))])),
    ]));
  }
  String _date(DateTime value) => '${value.day.toString().padLeft(2, '0')}/${value.month.toString().padLeft(2, '0')}/${value.year}';
}
class _Retry extends StatelessWidget { const _Retry({required this.onRetry}); final VoidCallback onRetry; @override Widget build(BuildContext context) => Padding(padding: const EdgeInsets.all(20), child: Column(children: [const Text('Unable to load consultations.'), TextButton(onPressed: onRetry, child: const Text('Retry'))])); }
