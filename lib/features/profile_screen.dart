import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../providers/theme_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dark = ref.watch(themeProvider) == ThemeMode.dark;
    final profile = ref.watch(patientProfileProvider);
    return profile.when(loading: () => const Center(child: CircularProgressIndicator()), error: (error, _) => Center(child: Text('Unable to load profile: $error')), data: (patient) => ListView(padding: const EdgeInsets.only(bottom: 28), children: [
      const SizedBox(height: 22),
      Center(
          child: CircleAvatar(
              radius: 43,
              backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: Text(patient.name.isEmpty ? 'P' : patient.name.substring(0, 1).toUpperCase(),
                  style: TextStyle(
                      color: AppTheme.blue,
                      fontSize: 30,
                      fontWeight: FontWeight.w800)))),
      const SizedBox(height: 11),
      const Center(
          child: Text(patient.name.isEmpty ? 'Patient' : patient.name,
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800))),
      Center(
          child: Text('Verity ID • ${patient.medicalId}',
              style: TextStyle(
                  fontSize: 12,
                  color: Theme.of(context).colorScheme.onSurfaceVariant))),
      const SectionTitle('Your care network'),
      Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: SoftCard(
              child: Column(children: [
            _row(
                context,
                Icons.medical_information_outlined,
                'Medical history',
                'Conditions, allergies, documents and emergency details',
                '/medical-history'),
            const Divider(),
            _row(context, Icons.contact_phone_rounded, 'Emergency contacts',
                '2 trusted contacts', '/emergency-contacts'),
            const Divider(),
            _row(context, Icons.shield_outlined, 'Insurance',
                'Aster Health Platinum', '/insurance'),
            const Divider(),
            _row(context, Icons.local_hospital_rounded, 'Linked hospitals',
                'NovaCare and 2 more', '/hospitals'),
          ]))),
      const SectionTitle('Preferences'),
      Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: SoftCard(
              child: Column(children: [
            _row(context, Icons.watch_rounded, 'Connected devices',
                'Apple Watch • Synced', '/devices'),
            const Divider(),
            Row(children: [
              const Icon(Icons.dark_mode_outlined, color: AppTheme.blue),
              const SizedBox(width: 13),
              const Expanded(
                  child: Text('Dark mode',
                      style: TextStyle(fontWeight: FontWeight.w700))),
              Switch(
                  value: dark,
                  onChanged: ref.read(themeProvider.notifier).toggle)
            ]),
            const Divider(),
            _row(context, Icons.lock_outline_rounded, 'Privacy & security',
                'Control your health data', '/profile'),
          ]))),
    ]));
  }

  Widget _row(BuildContext context, IconData icon, String title,
          String subtitle, String route) =>
      InkWell(
          onTap: () => context.push(route),
          borderRadius: BorderRadius.circular(16),
          child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 9),
              child: Row(children: [
                Icon(icon, color: AppTheme.blue),
                const SizedBox(width: 13),
                Expanded(
                    child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                      Text(title,
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                      Text(subtitle,
                          style: TextStyle(
                              fontSize: 11,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant))
                    ])),
                Icon(Icons.chevron_right_rounded,
                    color: Theme.of(context).colorScheme.onSurfaceVariant)
              ])));
}
