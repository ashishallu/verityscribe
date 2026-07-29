import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/services/prescription_pdf_service.dart';
import '../models/entities.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class RecordsScreen extends ConsumerWidget {
  const RecordsScreen({super.key});
  @override Widget build(BuildContext context, WidgetRef ref) {
    final records = ref.watch(recordsProvider);
    final medicinesState = ref.watch(medicinesProvider);
    return RefreshIndicator(onRefresh: () async { ref.invalidate(recordsProvider); await ref.read(recordsProvider.future); }, child: ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.only(bottom: 28), children: [
      Padding(padding: const EdgeInsets.all(20), child: Text('Medical history', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800))),
      const SectionTitle('Consultations'),
      records.when(data: (items) => medicinesState.when(data: (medicines) => Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: items.isEmpty ? const Center(child: Padding(padding: EdgeInsets.all(28), child: Text('No consultations available yet.'))) : Column(children: items.map((item) => Padding(padding: const EdgeInsets.only(bottom: 13), child: _ConsultationCard(item, medicines))).toList())), loading: () => const Center(child: Padding(padding: EdgeInsets.all(28), child: CircularProgressIndicator())), error: (error, _) => _Retry('$error', () => ref.invalidate(medicinesProvider))), loading: () => const Center(child: Padding(padding: EdgeInsets.all(40), child: CircularProgressIndicator())), error: (error, _) => _Retry('$error', () => ref.invalidate(recordsProvider))),
    ]));
  }
}
class _ConsultationCard extends StatefulWidget { final Consultation item; final List<Medicine> medicines; const _ConsultationCard(this.item,this.medicines); @override State<_ConsultationCard> createState() => _ConsultationCardState(); }
class _ConsultationCardState extends State<_ConsultationCard> {
  bool expanded = false; bool playing = false; double position = .28;
  @override Widget build(BuildContext context) { final item = widget.item; return SoftCard(padding: EdgeInsets.zero, child: Column(children: [
    InkWell(onTap: () => setState(() => expanded = !expanded), child: Padding(padding: const EdgeInsets.all(18), child: Row(children: [
      Container(width: 46, height: 46, decoration: BoxDecoration(color: Theme.of(context).colorScheme.primaryContainer, borderRadius: BorderRadius.circular(15)), child: const Icon(Icons.description_rounded, color: AppTheme.blue)), const SizedBox(width: 12),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(item.doctorName, style: const TextStyle(fontWeight: FontWeight.w800)), Text('${item.hospital} • 18 July 2026 • 10:30 AM', style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant)), const SizedBox(height: 7), Text(item.diagnosis, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)), const SizedBox(height: 7), const Wrap(spacing: 6, children: [StatusPill('COMPLETED', AppTheme.emerald), StatusPill('AI VERIFIED', AppTheme.blue)])])), Icon(expanded ? Icons.keyboard_arrow_up_rounded : Icons.keyboard_arrow_down_rounded),
    ]))),
    if (expanded) Padding(padding: const EdgeInsets.fromLTRB(18, 0, 18, 18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Divider(), const Text('Generated prescription', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)), const SizedBox(height: 8),
      Text('Patient: Ashish Allu\nDiagnosis: ${item.diagnosis}\nMetformin 500 mg • After breakfast\nAtorvastatin 10 mg • After dinner\nFollow-up: 21 August 2026', style: const TextStyle(height: 1.55, fontSize: 12)),
      const SizedBox(height: 16), const Text('Voice recording', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
      Row(children: [IconButton(onPressed: () => setState(() => playing = !playing), icon: Icon(playing ? Icons.pause_circle_filled_rounded : Icons.play_circle_fill_rounded, color: AppTheme.blue, size: 36)), Expanded(child: Slider(value: position, onChanged: (value) => setState(() => position = value))), const Text('03:18 / 11:42', style: TextStyle(fontSize: 10))]),
      FilledButton.icon(onPressed: () async { final messenger = ScaffoldMessenger.of(context); final file = await PrescriptionPdfService().create(item, widget.medicines); if (!mounted) return; messenger.showSnackBar(SnackBar(content: Text('Prescription saved to ${file.path}'))); }, icon: const Icon(Icons.download_rounded), label: const Text('Download prescription PDF')),
    ])),
  ])); }
}
class _Retry extends StatelessWidget { const _Retry(this.message,this.onRetry); final String message; final VoidCallback onRetry; @override Widget build(BuildContext context)=>Padding(padding:const EdgeInsets.all(20),child:Column(children:[Text(message,textAlign:TextAlign.center),OutlinedButton.icon(onPressed:onRetry,icon:const Icon(Icons.refresh),label:const Text('Retry'))])); }
