import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class LiveConsultationsScreen extends ConsumerWidget {
  const LiveConsultationsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => _LiveList(
      title: 'Consultations',
      subtitle: 'Your clinician-recorded visits and care plans',
      provider: patientConsultationsProvider,
      empty: 'No consultations have been recorded yet.',
      icon: Icons.medical_services_outlined,
      builder: (row) {
        final doctor = row['doctor'] is Map
            ? Map<String, dynamic>.from(row['doctor'] as Map)
            : const <String, dynamic>{};
        return _DetailCard(
            icon: Icons.medical_services_outlined,
            title: (row['diagnosis'] ?? 'Consultation').toString(),
            subtitle:
                '${row['consultation_date'] ?? row['created_at'] ?? 'Date unavailable'} • ${[
              doctor['first_name'],
              doctor['last_name']
            ].where((x) => x != null && x.toString().isNotEmpty).join(' ')}',
            details: {
              'Symptoms': row['symptoms'],
              'Treatment plan': row['treatment_plan'],
              'Follow-up': row['follow_up_date']
            });
      });
}

class LivePrescriptionsScreen extends ConsumerWidget {
  const LivePrescriptionsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => _LiveList(
      title: 'Prescriptions',
      subtitle: 'Medicines shared through your clinical record',
      provider: patientPrescriptionsProvider,
      empty: 'No prescriptions have been shared yet.',
      icon: Icons.medication_outlined,
      builder: (row) {
        final items = List<dynamic>.from(row['items'] as List? ?? const []);
        return SoftCard(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            const Icon(Icons.medication_outlined, color: AppTheme.blue),
            const SizedBox(width: 12),
            Expanded(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                  Text('Prescription',
                      style: Theme.of(context)
                          .textTheme
                          .titleSmall
                          ?.copyWith(fontWeight: FontWeight.w800)),
                  Text(
                      (row['prescription_date'] ?? 'Date unavailable')
                          .toString(),
                      style:
                          const TextStyle(color: AppTheme.muted, fontSize: 12))
                ]))
          ]),
          if ((row['notes'] ?? '').toString().trim().isNotEmpty)
            Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(row['notes'].toString())),
          if (items.isEmpty)
            const Padding(
                padding: EdgeInsets.only(top: 12),
                child: Text('No medicine items were recorded.',
                    style: TextStyle(color: AppTheme.muted)))
          else
            ...items.map((item) {
              final value = item is Map
                  ? Map<String, dynamic>.from(item)
                  : const <String, dynamic>{};
              final medicine = value['medicine'] is Map
                  ? Map<String, dynamic>.from(value['medicine'] as Map)
                  : const <String, dynamic>{};
              return Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .primaryContainer
                              .withValues(alpha: .45),
                          borderRadius: BorderRadius.circular(14)),
                      child: Text(
                          '${medicine['name'] ?? 'Medicine'}\n${[
                            value['dosage'],
                            value['frequency'],
                            value['duration']
                          ].where((x) => x != null && x.toString().isNotEmpty).join(' • ')}${(value['instructions'] ?? '').toString().trim().isEmpty ? '' : '\n${value['instructions']}'}',
                          style: const TextStyle(fontSize: 12, height: 1.45))));
            })
        ]));
      });
}

class LiveReportsScreen extends ConsumerWidget {
  const LiveReportsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => _LiveList(
      title: 'Reports',
      subtitle: 'Clinical reports available to you',
      provider: patientReportsProvider,
      empty: 'No reports are available yet.',
      icon: Icons.description_outlined,
      builder: (row) => _DetailCard(
              icon: Icons.description_outlined,
              title: (row['report_type'] ?? 'Report').toString(),
              subtitle: (row['report_date'] ?? 'Date unavailable').toString(),
              details: {
                'Findings': row['findings'],
                'Recommendations': row['recommendations']
              }));
}

class LiveInsuranceScreen extends ConsumerWidget {
  const LiveInsuranceScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(patientInsuranceProvider);
    return Scaffold(
        appBar: AppBar(title: const Text('Insurance')),
        body: state.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => _PageRetry(
                label: 'Unable to load insurance.',
                onRetry: () => ref.invalidate(patientInsuranceProvider)),
            data: (data) {
              final policies = List<Map<String, dynamic>>.from(
                  data['policies'] as List? ?? const []);
              return RefreshIndicator(
                  onRefresh: () async =>
                      ref.invalidate(patientInsuranceProvider),
                  child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.only(bottom: 24),
                      children: [
                        const _PageHeading(
                            title: 'Insurance coverage',
                            subtitle:
                                'Policies and claims linked to your patient record'),
                        Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20),
                            child: policies.isEmpty
                                ? const _EmptyClinicalCard(
                                    icon: Icons.shield_outlined,
                                    label:
                                        'No insurance policies are available.')
                                : Column(
                                    children: policies
                                        .map((row) => Padding(
                                            padding: const EdgeInsets.only(
                                                bottom: 12),
                                            child: _DetailCard(
                                                icon: Icons.shield_outlined,
                                                title: (row['provider_name'] ??
                                                        row['policy_type'] ??
                                                        'Policy')
                                                    .toString(),
                                                subtitle: (row[
                                                            'policy_number'] ??
                                                        'Policy number unavailable')
                                                    .toString(),
                                                details: {
                                                  'Coverage':
                                                      row['sum_insured'],
                                                  'Premium':
                                                      row['premium_amount'],
                                                  'Valid from':
                                                      row['start_date'],
                                                  'Valid until': row['end_date']
                                                })))
                                        .toList()))
                      ]));
            }));
  }
}

class _LiveList extends ConsumerWidget {
  const _LiveList(
      {required this.title,
      required this.subtitle,
      required this.provider,
      required this.empty,
      required this.icon,
      required this.builder});
  final String title, subtitle, empty;
  final IconData icon;
  final FutureProvider<List<Map<String, dynamic>>> provider;
  final Widget Function(Map<String, dynamic>) builder;
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(provider);
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => _PageRetry(
          label: 'Unable to load $title.',
          onRetry: () => ref.invalidate(provider),
        ),
        data: (items) => RefreshIndicator(
          onRefresh: () => ref.refresh(provider.future),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              _PageHeading(title: title, subtitle: subtitle),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: items.isEmpty
                    ? _EmptyClinicalCard(icon: icon, label: empty)
                    : Column(
                        children: items
                            .map((row) => Padding(
                                  padding: const EdgeInsets.only(bottom: 12),
                                  child: builder(row),
                                ))
                            .toList(),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PageHeading extends StatelessWidget {
  const _PageHeading({required this.title, required this.subtitle});
  final String title, subtitle;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 22),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title,
            style: Theme.of(context)
                .textTheme
                .headlineSmall
                ?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 6),
        Text(subtitle, style: const TextStyle(color: AppTheme.muted))
      ]));
}

class _EmptyClinicalCard extends StatelessWidget {
  const _EmptyClinicalCard({required this.icon, required this.label});
  final IconData icon;
  final String label;
  @override
  Widget build(BuildContext context) => SoftCard(
          child: Row(children: [
        Icon(icon, color: AppTheme.blue),
        const SizedBox(width: 12),
        Expanded(child: Text(label))
      ]));
}

class _PageRetry extends StatelessWidget {
  const _PageRetry({required this.label, required this.onRetry});
  final String label;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Center(
      child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.cloud_off_outlined,
                color: AppTheme.muted, size: 42),
            const SizedBox(height: 12),
            Text(label),
            TextButton(onPressed: onRetry, child: const Text('Retry'))
          ])));
}

class _DetailCard extends StatelessWidget {
  const _DetailCard(
      {required this.icon,
      required this.title,
      required this.subtitle,
      required this.details});
  final IconData icon;
  final String title, subtitle;
  final Map<String, dynamic> details;
  @override
  Widget build(BuildContext context) {
    final present = details.entries
        .where((entry) =>
            entry.value != null && entry.value.toString().trim().isNotEmpty)
        .toList();
    return SoftCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(14)),
              child: Icon(icon, color: AppTheme.blue)),
          const SizedBox(width: 12),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(title,
                    style: const TextStyle(fontWeight: FontWeight.w800)),
                if (subtitle.trim().isNotEmpty)
                  Text(subtitle,
                      style:
                          const TextStyle(color: AppTheme.muted, fontSize: 12))
              ])),
        ]),
        if (present.isNotEmpty) ...[
          const Divider(height: 25),
          ...present.map((entry) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: RichText(
                    text: TextSpan(
                        style: DefaultTextStyle.of(context)
                            .style
                            .copyWith(fontSize: 12),
                        children: [
                      TextSpan(
                          text: '${entry.key}: ',
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                      TextSpan(text: entry.value.toString())
                    ])),
              )),
        ],
      ]),
    );
  }
}
