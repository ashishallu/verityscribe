import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class MedicalHistoryScreen extends ConsumerWidget {
  const MedicalHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final record = ref.watch(patientMedicalRecordProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Medical history')),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(patientMedicalRecordProvider);
          await ref.read(patientMedicalRecordProvider.future);
        },
        child: record.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (_, __) => ListView(children: [
            const Padding(padding: EdgeInsets.all(24), child: Text('Unable to load medical records.')),
            Center(child: FilledButton(onPressed: () => ref.invalidate(patientMedicalRecordProvider), child: const Text('Retry'))),
          ]),
          data: (data) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              _section('Medical history', data['medical_history']),
              _section('Allergies', data['allergies']),
              _section('Chronic conditions', data['chronic_conditions']),
              _section('Surgeries', data['surgeries']),
              _section('Vaccinations', data['vaccinations']),
              _section('Family history', data['family_history']),
              _section('Lifestyle', data['lifestyle']),
              _section('Vitals', data['vitals']),
              _section('Measurements', data['measurements']),
            ],
          ),
        ),
      ),
    );
  }

  Widget _section(String title, dynamic value) {
    final rows = value is List ? value : const [];
    return Card(
      child: ExpansionTile(
        title: Text(title),
        children: rows.isEmpty
            ? const [ListTile(title: Text('No records available.'))]
            : rows.map((row) => ListTile(
                  title: Text(_title(row)),
                  subtitle: Text(_details(row)),
                )).toList(),
      ),
    );
  }

  String _title(dynamic row) {
    if (row is! Map) return row.toString();
    const keys = ['name', 'condition_name', 'allergen', 'surgery_name', 'vaccine_name', 'relationship', 'record_type'];
    for (final key in keys) {
      final value = row[key];
      if (value != null && value.toString().isNotEmpty) return value.toString();
    }
    return 'Clinical record';
  }

  String _details(dynamic row) {
    if (row is! Map) return '';
    return row.entries.where((entry) => entry.value != null && entry.value.toString().isNotEmpty && entry.key != 'id' && entry.key != 'patient_id').map((entry) => '${entry.key}: ${entry.value}').join(' • ');
  }
}
