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
    final consultations = ref.watch(patientConsultationsProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(patientProfileProvider);
        ref.invalidate(appointmentsLiveProvider);
        ref.invalidate(medicinesProvider);
        ref.invalidate(patientReportsProvider);
        ref.invalidate(patientConsultationsProvider);
      },
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 28),
        children: [
          _Header(profile: profile),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: _CareSummary(
              appointments: appointments,
              medicines: medicines,
              reports: reports,
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 22, 20, 0),
            child: _VoiceConsultationCard(onTap: () => context.go('/record')),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
            child: FilledButton.icon(
              onPressed: () => context.go('/book-appointment'),
              icon: const Icon(Icons.calendar_month_rounded),
              label: const Text('Book an appointment'),
            ),
          ),
          const SectionTitle('Today at a glance'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: appointments.when(
              loading: () => const SoftCard(child: LinearProgressIndicator()),
              error: (_, __) => _UnavailableCard(
                label: 'Appointments are temporarily unavailable.',
                onRetry: () => ref.invalidate(appointmentsLiveProvider),
              ),
              data: (items) => items.isEmpty
                  ? _EmptyCard(
                      icon: Icons.calendar_today_outlined,
                      title: 'No upcoming appointments',
                      action: 'Book',
                      onTap: () => context.go('/book-appointment'),
                    )
                  : _AppointmentCard(item: items.first),
            ),
          ),
          const SectionTitle("Today's medicines", action: 'View all'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: medicines.when(
              loading: () => const SoftCard(child: LinearProgressIndicator()),
              error: (_, __) => _UnavailableCard(
                label: 'Medicines are temporarily unavailable.',
                onRetry: () => ref.invalidate(medicinesProvider),
              ),
              data: (items) => items.isEmpty
                  ? _EmptyCard(
                      icon: Icons.medication_outlined,
                      title: 'No prescribed medicines',
                      action: 'View prescriptions',
                      onTap: () => context.go('/prescriptions-live'),
                    )
                  : SoftCard(
                      child: Column(
                        children: [
                          for (final medicine in items.take(2)) ...[
                            _MedicineRow(
                              name: medicine.name,
                              detail: [medicine.dosage, medicine.schedule]
                                  .where((x) => x.trim().isNotEmpty)
                                  .join(' • '),
                            ),
                            if (medicine != items.take(2).last)
                              const Divider(height: 25),
                          ],
                          if (items.length > 2) ...[
                            const Divider(height: 25),
                            TextButton(
                              onPressed: () => context.go('/medicines'),
                              child: Text('View all ${items.length} medicines'),
                            ),
                          ],
                        ],
                      ),
                    ),
            ),
          ),
          const SectionTitle('Health records'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: reports.when(
              loading: () => const SoftCard(child: LinearProgressIndicator()),
              error: (_, __) => _UnavailableCard(
                label: 'Reports are temporarily unavailable.',
                onRetry: () => ref.invalidate(patientReportsProvider),
              ),
              data: (items) => _EmptyCard(
                icon: Icons.folder_copy_outlined,
                title: items.isEmpty
                    ? 'No reports have been shared yet'
                    : '${items.length} report${items.length == 1 ? '' : 's'} available',
                action: 'Open records',
                onTap: () => context.go('/live-health-record'),
              ),
            ),
          ),
          const SectionTitle('Recent activity'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: consultations.when(
              loading: () => const SoftCard(child: LinearProgressIndicator()),
              error: (_, __) => _UnavailableCard(
                label: 'Clinical activity is temporarily unavailable.',
                onRetry: () => ref.invalidate(patientConsultationsProvider),
              ),
              data: (items) => items.isEmpty
                  ? _EmptyCard(
                      icon: Icons.history_outlined,
                      title: 'No recent clinical activity',
                      action: 'Open records',
                      onTap: () => context.go('/live-health-record'),
                    )
                  : SoftCard(
                      child: Column(
                        children: items.take(3).map((row) {
                          final diagnosis =
                              (row['diagnosis'] ?? 'Consultation').toString();
                          final date = (row['consultation_date'] ??
                                  row['created_at'] ?? '')
                              .toString();
                          return ListTile(
                            contentPadding: EdgeInsets.zero,
                            leading: const Icon(Icons.description_outlined,
                                color: AppTheme.blue),
                            title: Text(diagnosis),
                            subtitle: Text(date.isEmpty ? 'Date unavailable' : date),
                            onTap: () => context.go('/consultations-live'),
                          );
                        }).toList(),
                      ),
                    ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.profile});
  final AsyncValue<dynamic> profile;
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 12),
        child: Row(children: [
          const AppLogo(),
          const SizedBox(width: 12),
          Expanded(
            child: profile.when(
              loading: () => const Text('Loading your care space…'),
              error: (_, __) => const Text('Welcome back'),
              data: (patient) => Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    patient.name.toString().trim().isEmpty
                        ? 'Welcome back'
                        : 'Good day, ${patient.name}',
                    style: Theme.of(context)
                        .textTheme
                        .titleLarge
                        ?.copyWith(fontWeight: FontWeight.w800),
                  ),
                  const Text('Your care, clearly connected',
                      style: TextStyle(color: AppTheme.muted, fontSize: 12)),
                ],
              ),
            ),
          ),
          IconButton(
              onPressed: () => context.push('/notifications'),
              icon: const Icon(Icons.notifications_none_rounded)),
        ]),
      );
}

class _CareSummary extends StatelessWidget {
  const _CareSummary({required this.appointments, required this.medicines, required this.reports});
  final AsyncValue<dynamic> appointments, medicines, reports;
  @override
  Widget build(BuildContext context) {
    String count(AsyncValue<dynamic> value) => value.when(
        data: (items) => items is List ? '${items.length}' : '0',
        loading: () => '…', error: (_, __) => '—');
    return SoftCard(
      color: const Color(0xFFEAF2FF),
      child: Row(children: [
        const Icon(Icons.auto_awesome_rounded, color: AppTheme.blue),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Your care summary', style: TextStyle(fontWeight: FontWeight.w800)),
          const SizedBox(height: 5),
          Text('${count(appointments)} appointments • ${count(medicines)} medicines • ${count(reports)} reports', style: const TextStyle(color: AppTheme.muted, fontSize: 12)),
        ])),
      ]),
    );
  }
}

class _VoiceConsultationCard extends StatelessWidget {
  const _VoiceConsultationCard({required this.onTap});
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(26),
        child: Container(
          height: 226,
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [AppTheme.blue, Color(0xFF477BF0), AppTheme.cyan]),
            borderRadius: BorderRadius.circular(26),
            boxShadow: const [BoxShadow(color: Color(0x402459E0), blurRadius: 24, offset: Offset(0, 10))],
          ),
          child: Stack(children: [
            Positioned(right: -16, bottom: -25, child: Icon(Icons.graphic_eq_rounded, size: 165, color: Colors.white.withValues(alpha: .15))),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const StatusPill('SECURE VOICE CARE', Colors.white),
              const Spacer(),
              const Text('Start a new voice\nconsultation', style: TextStyle(color: Colors.white, fontSize: 25, fontWeight: FontWeight.w800, height: 1.15)),
              const SizedBox(height: 8),
              const Text('Secure clinical recording • doctor review required', style: TextStyle(color: Colors.white70, fontSize: 12)),
              const SizedBox(height: 12),
              Row(children: [Container(width: 48, height: 48, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)), child: const Icon(Icons.mic_rounded, color: AppTheme.blue)), const SizedBox(width: 12), const Text('Record securely', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700))]),
            ]),
          ]),
        ),
      );
}

class _AppointmentCard extends StatelessWidget {
  const _AppointmentCard({required this.item});
  final dynamic item;
  @override
  Widget build(BuildContext context) => SoftCard(child: InkWell(onTap: () => context.go('/record'), borderRadius: BorderRadius.circular(18), child: Row(children: [
        Container(width: 46, height: 46, decoration: BoxDecoration(color: const Color(0xFFEAF9F5), borderRadius: BorderRadius.circular(15)), child: const Icon(Icons.calendar_month_rounded, color: AppTheme.emerald)),
        const SizedBox(width: 13),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(item.doctorName.toString().isEmpty ? 'Doctor details unavailable' : item.doctorName, style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w800)), Text([item.department, item.hospital].where((x) => x.toString().trim().isNotEmpty).join(' • '), style: const TextStyle(fontSize: 12, color: AppTheme.muted))])),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [Text('${item.date} ${item.time}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppTheme.blue)), Text(item.status.toString(), style: const TextStyle(fontSize: 12, color: AppTheme.blue))]),
      ])));
}

class _MedicineRow extends StatelessWidget { const _MedicineRow({required this.name, required this.detail}); final String name, detail; @override Widget build(BuildContext context) => Row(children: [const CircleAvatar(backgroundColor: Color(0xFFEAF9F5), child: Icon(Icons.medication_rounded, color: AppTheme.emerald)), const SizedBox(width: 12), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(name, style: const TextStyle(fontWeight: FontWeight.w800)), Text(detail.isEmpty ? 'Schedule not available' : detail, style: const TextStyle(fontSize: 12, color: AppTheme.muted))]))]); }
class _EmptyCard extends StatelessWidget { const _EmptyCard({required this.icon, required this.title, required this.action, required this.onTap}); final IconData icon; final String title, action; final VoidCallback onTap; @override Widget build(BuildContext context) => SoftCard(child: Row(children: [Icon(icon, color: AppTheme.blue), const SizedBox(width: 12), Expanded(child: Text(title, style: const TextStyle(fontWeight: FontWeight.w700))), TextButton(onPressed: onTap, child: Text(action))])); }
class _UnavailableCard extends StatelessWidget { const _UnavailableCard({required this.label, required this.onRetry}); final String label; final VoidCallback onRetry; @override Widget build(BuildContext context) => SoftCard(child: Row(children: [const Icon(Icons.cloud_off_outlined, color: AppTheme.muted), const SizedBox(width: 12), Expanded(child: Text(label)), TextButton(onPressed: onRetry, child: const Text('Retry'))])); }
