import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class LiveHealthRecordScreen extends ConsumerWidget {
  const LiveHealthRecordScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final record = ref.watch(patientMedicalRecordProvider);
    final insurance = ref.watch(patientInsuranceProvider);
    final notifications = ref.watch(patientNotificationsProvider);
    final schedule = ref.watch(patientMedicationScheduleProvider);
    final orders = ref.watch(patientPharmacyOrdersProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Live health record')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(patientMedicalRecordProvider);
          ref.invalidate(patientInsuranceProvider);
          ref.invalidate(patientNotificationsProvider);
          ref.invalidate(patientMedicationScheduleProvider);
          ref.invalidate(patientPharmacyOrdersProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _section(context, 'Clinical record', record, (data) => [
              _listSection('Medical history', data['medical_history']),
              _listSection('Allergies', data['allergies']),
              _listSection('Chronic conditions', data['chronic_conditions']),
              _listSection('Surgeries', data['surgeries']),
              _listSection('Vaccinations', data['vaccinations']),
              _listSection('Family history', data['family_history']),
              _listSection('Lifestyle', data['lifestyle']),
              _listSection('Vitals', data['vitals']),
              _listSection('Measurements', data['measurements']),
              _listSection('Consultations', data['consultations']),
              _listSection('Prescriptions', data['prescriptions']),
              _listSection('Reports', data['reports']),
            ]),
            _section(context, 'Insurance & claims', insurance, (data) => [
              _listSection('Policies', data['policies']),
              _listSection('Claims', data['claims']),
            ]),
            _section(context, 'Notifications', notifications, (data) => [
              _listSection('Notifications', data['data'] ?? data),
              if (data['unread'] != null) Text('${data['unread']} unread'),
            ]),
            _section(context, 'Medication schedule', schedule, (data) => [_listSection('Schedules', data)]),
            _section(context, 'Pharmacy orders', orders, (data) => [_listSection('Orders', data)]),
          ],
        ),
      ),
    );
  }

  Widget _section(BuildContext context, String title, AsyncValue<dynamic> value, List<Widget> Function(dynamic) content) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: value.when(
          loading: () => const Center(child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator())),
          error: (error, _) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('$title unavailable'), TextButton(onPressed: () {}, child: const Text('Retry'))]),
          data: (data) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(title, style: Theme.of(context).textTheme.titleLarge), ...content(data)]),
        ),
      ),
    );
  }

  Widget _listSection(String title, dynamic value) {
    final items = value is List ? value : const [];
    return ExpansionTile(title: Text('$title (${items.length})'), children: [if (items.isEmpty) const ListTile(title: Text('No records available')) else ...items.take(10).map((item) => ListTile(title: Text(item is Map ? (item['name'] ?? item['title'] ?? item['diagnosis'] ?? item['status'] ?? 'Record').toString() : item.toString()))) ]);
  }
}
