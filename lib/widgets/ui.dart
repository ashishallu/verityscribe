import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class SectionTitle extends StatelessWidget {
  final String title; final String? action;
  const SectionTitle(this.title, {this.action, super.key});
  @override Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 25, 20, 12),
    child: Row(children: [Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))), if (action != null) Text(action!, style: const TextStyle(color: AppTheme.blue, fontWeight: FontWeight.w700))]),
  );
}

class SoftCard extends StatelessWidget {
  final Widget child; final EdgeInsetsGeometry padding; final Color? color;
  const SoftCard({required this.child, this.padding = const EdgeInsets.all(18), this.color, super.key});
  @override Widget build(BuildContext context) => Container(
    padding: padding, decoration: BoxDecoration(color: color ?? Theme.of(context).colorScheme.surface, borderRadius: BorderRadius.circular(22), boxShadow: [BoxShadow(color: Theme.of(context).colorScheme.shadow.withValues(alpha: .08), blurRadius: 18, offset: const Offset(0, 7))]), child: child);
}

class StatusPill extends StatelessWidget { final String label; final Color color; const StatusPill(this.label, this.color, {super.key});
 @override Widget build(BuildContext context) => Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6), decoration: BoxDecoration(color: color.withValues(alpha: .12), borderRadius: BorderRadius.circular(30)), child: Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w800, fontSize: 11))); }

class AppLogo extends StatelessWidget { const AppLogo({super.key});
 @override Widget build(BuildContext context) => Container(width: 42,height: 42,decoration: BoxDecoration(gradient: const LinearGradient(colors:[AppTheme.blue,AppTheme.cyan]),borderRadius: BorderRadius.circular(14)),child: const Icon(Icons.graphic_eq_rounded,color:Colors.white)); }
