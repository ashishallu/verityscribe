import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(patientNotificationsProvider);
    return Scaffold(appBar: AppBar(title: const Text('Notifications')), body: state.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (_, __) => Center(child: TextButton(onPressed: () => ref.invalidate(patientNotificationsProvider), child: const Text('Unable to load notifications. Retry'))),
      data: (data) {
        final items = data['data'] is List ? List<dynamic>.from(data['data']) : const <dynamic>[];
        if (items.isEmpty) return const Center(child: Text('No notifications available.'));
        return ListView(padding: const EdgeInsets.all(16), children: items.map((item) { final row = item is Map ? item : const {}; return Card(child: ListTile(title: Text('${row['title'] ?? row['type'] ?? 'Notification'}'), subtitle: Text('${row['body'] ?? row['message'] ?? ''}'))); }).toList();
      },
    ));
  }
}
