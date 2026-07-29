import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/routes/app_router.dart';
import 'theme/app_theme.dart';
import 'providers/theme_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Supabase.initialize(
    url: const String.fromEnvironment('SUPABASE_URL'),
    anonKey: const String.fromEnvironment('SUPABASE_PUBLISHABLE_KEY'),
  );
  runApp(const ProviderScope(child: VerityScribeApp()));
}

class VerityScribeApp extends ConsumerWidget {
  const VerityScribeApp({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => AnimatedTheme(
      data: ref.watch(themeProvider) == ThemeMode.dark
          ? AppTheme.dark
          : AppTheme.light,
      duration: const Duration(milliseconds: 350),
      curve: Curves.easeInOutCubic,
      child: MaterialApp.router(
        debugShowCheckedModeBanner: false,
        title: 'VerityScribe',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ref.watch(themeProvider),
        routerConfig: appRouter,
      ));
}
