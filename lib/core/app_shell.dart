import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppShell extends StatelessWidget {
  final Widget child;
  const AppShell({required this.child, super.key});
  static const paths = ['/home', '/ai', '/medicines', '/alarms', '/records', '/profile'];
  static const labels = ['Home', 'AI', 'Medicines', 'Alarms', 'Records', 'Profile'];
  static const icons = [Icons.home_rounded, Icons.auto_awesome_rounded, Icons.medication_rounded, Icons.alarm_rounded, Icons.folder_copy_rounded, Icons.person_rounded];
  @override
  Widget build(BuildContext context) {
    final index = paths.indexOf(GoRouterState.of(context).uri.path).clamp(0, 5);
    return Scaffold(body: SafeArea(child: child), bottomNavigationBar: SafeArea(top: false, child: NavigationBar(selectedIndex: index, onDestinationSelected: (value) => context.go(paths[value]), destinations: List.generate(6, (i) => NavigationDestination(icon: Icon(icons[i]), label: labels[i])))));
  }
}
