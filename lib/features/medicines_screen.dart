import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/entities.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class MedicinesScreen extends ConsumerWidget {
  const MedicinesScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final items = ref.watch(medicinesProvider);
    return RefreshIndicator(
      onRefresh: () async { ref.invalidate(medicinesProvider); await ref.read(medicinesProvider.future); },
      child: ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.only(bottom: 25), children: [
        Padding(padding: const EdgeInsets.all(20), child: Row(children: [Text('Medicines', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)), const Spacer(), IconButton(onPressed: () => context.push('/scan'), icon: const Icon(Icons.document_scanner_rounded))])),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: TextField(decoration: const InputDecoration(prefixIcon: Icon(Icons.search_rounded), hintText: 'Search medicines'))),
        const SectionTitle('Medication dashboard'),
        items.when(data: (medicines) => medicines.isEmpty ? const Padding(padding: EdgeInsets.all(20), child: Text('No prescribed medicines available.')) : Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Column(children: medicines.map((m) => Padding(padding: const EdgeInsets.only(bottom: 13), child: _MedicineCard(m))).toList())), loading: () => const Center(child: Padding(padding: EdgeInsets.all(35), child: CircularProgressIndicator())), error: (e, _) => Padding(padding: const EdgeInsets.all(20), child: Text('Unable to load medicines: $e'))),
      ]),
    );
  }
}

class _MedicineCard extends StatelessWidget {
  final Medicine medicine;
  const _MedicineCard(this.medicine);
  @override
  Widget build(BuildContext context) {
    final hasQuantity = medicine.initialQuantity > 0;
    final fraction = hasQuantity ? (medicine.remaining / medicine.initialQuantity).clamp(0.0, 1.0) : null;
    final hasSchedule = medicine.dailyUsage > 0;
    final finish = hasSchedule ? (medicine.remaining / medicine.dailyUsage).ceil() : 0;
    final low = hasSchedule && finish <= 7;
    return InkWell(onTap: () => context.push('/medicine/${medicine.id}', extra: medicine), borderRadius: BorderRadius.circular(22), child: SoftCard(child: Column(children: [
      Row(children: [Hero(tag: medicine.id, child: Container(width: 52, height: 52, decoration: BoxDecoration(color: Theme.of(context).colorScheme.primaryContainer, borderRadius: BorderRadius.circular(16)), child: const Icon(Icons.medication_rounded, color: AppTheme.blue))), const SizedBox(width: 13), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(medicine.name, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)), Text('${medicine.dosage} • ${medicine.purpose}', style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant)), Text(medicine.schedule.isEmpty ? 'Schedule not available' : medicine.schedule, style: const TextStyle(fontSize: 12, color: AppTheme.blue, fontWeight: FontWeight.w700))])), if (low) const StatusPill('REORDER', Color(0xFFE79A22))]),
      const SizedBox(height: 14), if (fraction != null) LinearProgressIndicator(value: fraction, minHeight: 7, borderRadius: BorderRadius.circular(10), color: low ? const Color(0xFFE79A22) : AppTheme.emerald), const SizedBox(height: 9),
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(hasQuantity ? '${medicine.remaining} of ${medicine.initialQuantity} tablets remaining' : 'Quantity not available', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)), Text(medicine.doctor, style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant))]), const Divider(height: 25),
      Row(children: [_Stat('Daily', hasSchedule ? '${medicine.dailyUsage} tablet${medicine.dailyUsage > 1 ? 's' : ''}' : 'Not available'), _Stat('Finishes in', hasSchedule ? '$finish days' : 'Not available'), _Stat('Reorder', hasSchedule ? (low ? 'Now' : 'Not due') : 'Not available')]),
    ])));
  }
}

class _Stat extends StatelessWidget { final String label, value; const _Stat(this.label, this.value); @override Widget build(BuildContext context) => Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(label, style: TextStyle(fontSize: 10, color: Theme.of(context).colorScheme.onSurfaceVariant)), Text(value, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w800))])); }

class MedicineDetailsScreen extends StatelessWidget {
  final Medicine medicine;
  const MedicineDetailsScreen({required this.medicine, super.key});
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: Text(medicine.name)), body: ListView(padding: const EdgeInsets.all(20), children: [Center(child: Hero(tag: medicine.id, child: Container(width: 100, height: 100, decoration: BoxDecoration(color: Theme.of(context).colorScheme.primaryContainer, borderRadius: BorderRadius.circular(28)), child: const Icon(Icons.medication_rounded, size: 48, color: AppTheme.blue)))), const SizedBox(height: 18), Center(child: Text('${medicine.name} ${medicine.dosage}', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))), const SizedBox(height: 22), SoftCard(child: Column(children: [_detail('Purpose', medicine.purpose), _detail('How to take', medicine.howToTake), _detail('Food instructions', medicine.foodInstructions), _detail('Possible side effects', medicine.sideEffects), _detail('Doctor notes', medicine.notes)])), const SectionTitle('Quantity'), SoftCard(child: Row(children: [_Stat('Prescribed', medicine.initialQuantity > 0 ? '${medicine.initialQuantity} tablets' : 'Not available'), _Stat('Remaining', medicine.initialQuantity > 0 ? '${medicine.remaining} tablets' : 'Not available'), _Stat('Daily usage', medicine.dailyUsage > 0 ? '${medicine.dailyUsage}' : 'Not available')])), const SectionTitle('Additional information'), const SoftCard(child: Text('Price comparisons and pharmacy availability are not provided by the clinical API.'))]));
  Widget _detail(String label, String value) => Padding(padding: const EdgeInsets.only(bottom: 14), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(label, style: const TextStyle(fontWeight: FontWeight.w800)), Text(value.isEmpty ? 'Not available' : value, style: const TextStyle(fontSize: 12, color: AppTheme.muted))]));
}
