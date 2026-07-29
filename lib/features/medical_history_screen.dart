import 'package:flutter/material.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class MedicalHistoryScreen extends StatefulWidget {
  const MedicalHistoryScreen({super.key});
  @override
  State<MedicalHistoryScreen> createState() => _MedicalHistoryScreenState();
}

class _MedicalHistoryScreenState extends State<MedicalHistoryScreen> {
  final conditions = <String>['Type 2 Diabetes', 'Hypertension'];
  final surgeries = <String>['Appendectomy • 2012', 'Fracture repair • 2018'];
  final documents = <String>['Blood panel • 18 Jul 2026', 'ECG • 12 Jun 2026'];
  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(title: const Text('Medical history')),
      body: ListView(padding: const EdgeInsets.all(20), children: [
        SoftCard(
            color: Theme.of(context).colorScheme.primaryContainer,
            child: const Row(children: [
              Icon(Icons.verified_user_rounded, color: AppTheme.blue),
              SizedBox(width: 10),
              Expanded(
                  child: Text(
                      'Your history is securely synced with your care team.',
                      style: TextStyle(fontWeight: FontWeight.w700)))
            ])),
        _editable('Past medical history', Icons.medical_information_outlined,
            conditions),
        _editable(
            'Past surgical history', Icons.content_cut_outlined, surgeries),
        _section('Allergies', Icons.warning_amber_rounded, [
          'Penicillin • Drug allergy • Severe rash',
          'Peanuts • Food allergy • Hives',
          'Pollen • Environmental • Mild'
        ]),
        _section('Current chronic conditions', Icons.monitor_heart_outlined,
            ['Type 2 Diabetes • Stable', 'Hypertension • Monitored']),
        _section('Family medical history', Icons.family_restroom_outlined, [
          'Father • Hypertension',
          'Mother • Type 2 Diabetes',
          'Maternal grandfather • Heart disease'
        ]),
        _section('Lifestyle information', Icons.self_improvement_outlined, [
          'Smoking • Never',
          'Alcohol • Occasional',
          'Exercise • Walking 4× weekly',
          'Diet • Low sodium',
          'Sleep • 7 hours average'
        ]),
        _section('Blood information', Icons.bloodtype_outlined, [
          'Blood group • O+',
          'Organ donor • Registered',
          'Weight • 72 kg',
          'Height • 174 cm',
          'BMI • 23.8'
        ]),
        ExpansionTile(
            leading:
                const Icon(Icons.folder_copy_outlined, color: AppTheme.blue),
            title: const Text('Medical documents',
                style: TextStyle(fontWeight: FontWeight.w800)),
            children: [
              SoftCard(
                  child: Column(children: [
                LinearProgressIndicator(value: .72),
                const Text('Uploading MRI Brain scan… 72%'),
                ...documents.map((d) => ListTile(
                    leading: const Icon(Icons.description_outlined),
                    title: Text(d),
                    subtitle: const Text('Preview • Download • Share'),
                    trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => setState(() => documents.remove(d))))),
                TextButton.icon(
                    onPressed: () => setState(
                        () => documents.add('MRI Brain scan • Just now')),
                    icon: const Icon(Icons.cloud_upload_outlined),
                    label: const Text('Upload dummy document'))
              ]))
            ]),
        _section('Vaccination history', Icons.vaccines_outlined, [
          'COVID-19 booster • 14 Oct 2025',
          'Influenza • 03 Sep 2025',
          'Tetanus • 08 Mar 2022'
        ]),
        _section('Emergency medical information', Icons.emergency_outlined, [
          'Emergency contact • Ravi Allu • +91 98765 12345',
          'Preferred hospital • NovaCare Hospital',
          'Primary doctor • Dr. Meera Kapoor',
          'Insurance • Aster Health Platinum',
          'Medical notes • Avoid Penicillin'
        ])
      ]));
  Widget _editable(String title, IconData icon, List<String> data) =>
      ExpansionTile(
          leading: Icon(icon, color: AppTheme.blue),
          title:
              Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          children: [
            SoftCard(
                child: Column(children: [
              ...data.asMap().entries.map((e) => ListTile(
                  title: Text(e.value),
                  trailing: Wrap(children: [
                    IconButton(
                        icon: const Icon(Icons.edit_outlined),
                        onPressed: () => _edit(data, e.key)),
                    IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => setState(() => data.removeAt(e.key)))
                  ]))),
              TextButton.icon(
                  onPressed: () => _edit(data, null),
                  icon: const Icon(Icons.add),
                  label: Text('Add $title'))
            ]))
          ]);
  Widget _section(String title, IconData icon, List<String> data) =>
      ExpansionTile(
          leading: Icon(icon, color: AppTheme.blue),
          title:
              Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
          children: [
            SoftCard(
                child: Column(
                    children:
                        data.map((e) => ListTile(title: Text(e))).toList()))
          ]);
  void _edit(List<String> data, int? index) {
    final c = TextEditingController(text: index == null ? '' : data[index]);
    showDialog(
        context: context,
        builder: (d) => AlertDialog(
                title: Text(index == null
                    ? 'Add medical record'
                    : 'Edit medical record'),
                content: TextField(controller: c),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(d),
                      child: const Text('Cancel')),
                  FilledButton(
                      onPressed: () {
                        setState(() {
                          if (index == null) {
                            data.add(c.text);
                          } else {
                            data[index] = c.text;
                          }
                        });
                        Navigator.pop(d);
                        ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                                content:
                                    Text('Medical history securely synced ✓')));
                      },
                      child: const Text('Save'))
                ]));
  }
}
