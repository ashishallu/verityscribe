import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

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
      child: ListView(physics: const AlwaysScrollableScrollPhysics(), padding: const EdgeInsets.only(bottom: 28), children: [
        Padding(padding: const EdgeInsets.fromLTRB(20, 18, 20, 12), child: Row(children: [const AppLogo(), const SizedBox(width: 12), Expanded(child: profile.when(loading: () => const Text('Loading your profile…'), error: (_, __) => const Text('Welcome back'), data: (p) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Welcome, ${p.name.isEmpty ? 'Patient' : p.name}', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)), const Text('Your care, clearly connected', style: TextStyle(color: AppTheme.muted, fontSize: 12))]))), IconButton(onPressed: () => context.push('/notifications'), icon: const Icon(Icons.notifications_none_rounded))])),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: InkWell(onTap: () => context.go('/record'), borderRadius: BorderRadius.circular(26), child: Container(height: 190, padding: const EdgeInsets.all(22), decoration: BoxDecoration(gradient: const LinearGradient(colors: [AppTheme.blue, Color(0xFF477BF0), AppTheme.cyan]), borderRadius: BorderRadius.circular(26), boxShadow: const [BoxShadow(color: Color(0x402459E0), blurRadius: 24, offset: Offset(0, 10))]), child: Stack(children: [Positioned(right: -16, bottom: -25, child: Icon(Icons.graphic_eq_rounded, size: 150, color: Colors.white.withValues(alpha: .15))), Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const StatusPill('SECURE CARE', Colors.white), const Spacer(), const Text('Start a new\nvoice consultation', style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w800, height: 1.15)), const SizedBox(height: 8), const Text('Record securely for doctor review', style: TextStyle(color: Colors.white70, fontSize: 12))])])))),
        const SectionTitle('Today at a glance'),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: appointments.when(loading: () => const LinearProgressIndicator(), error: (_, __) => const SoftCard(child: Text('Appointments are temporarily unavailable.')), data: (items) => items.isEmpty ? SoftCard(child: Row(children: [const Icon(Icons.calendar_today_outlined, color: AppTheme.blue), const SizedBox(width: 12), const Expanded(child: Text('No upcoming appointments')), TextButton(onPressed: () => context.go('/book-appointment'), child: const Text('Book'))])) : SoftCard(child: ListTile(contentPadding: EdgeInsets.zero, leading: const CircleAvatar(backgroundColor: Color(0xFFEAF9F5), child: Icon(Icons.calendar_month_rounded, color: AppTheme.emerald)), title: Text(items.first.doctorName), subtitle: Text('${items.first.date} ${items.first.time}\n${items.first.department}'), trailing: StatusPill(items.first.status, AppTheme.blue))))),
        const SectionTitle('Your care'),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Row(children: [Expanded(child: _SummaryCard(icon: Icons.medication_outlined, label: 'Medicines', value: medicines.when(data: (x) => '${x.length}', loading: () => '…', error: (_, __) => '—'), onTap: () => context.go('/medicines'))), const SizedBox(width: 12), Expanded(child: _SummaryCard(icon: Icons.description_outlined, label: 'Reports', value: reports.when(data: (x) => '${x.length}', loading: () => '…', error: (_, __) => '—'), onTap: () => context.go('/reports-live')))])),
        const SectionTitle('Quick actions'),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: Wrap(spacing: 10, runSpacing: 10, children: [_Action(icon: Icons.person_search_outlined, label: 'Find a doctor', onTap: () => context.go('/doctors')), _Action(icon: Icons.monitor_heart_outlined, label: 'Health record', onTap: () => context.go('/live-health-record')), _Action(icon: Icons.event_available_outlined, label: 'Appointments', onTap: () => context.go('/appointments'))])),
      ]),
    );
  }
}

class _SummaryCard extends StatelessWidget { const _SummaryCard({required this.icon, required this.label, required this.value, required this.onTap}); final IconData icon; final String label; final String value; final VoidCallback onTap; @override Widget build(BuildContext context) => InkWell(onTap: onTap, borderRadius: BorderRadius.circular(22), child: SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Icon(icon, color: AppTheme.blue), const SizedBox(height: 12), Text(value, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)), Text(label, style: const TextStyle(color: AppTheme.muted))]))); }
class _Action extends StatelessWidget { const _Action({required this.icon, required this.label, required this.onTap}); final IconData icon; final String label; final VoidCallback onTap; @override Widget build(BuildContext context) => ActionChip(avatar: Icon(icon, size: 18, color: AppTheme.blue), label: Text(label), onPressed: onTap); }
