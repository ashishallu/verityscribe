import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/routes/app_router.dart';
import 'theme/app_theme.dart';
import 'providers/theme_provider.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'providers/app_providers.dart';
import 'features/authentication/auth_screens.dart';

final _startupClock = Stopwatch()..start();
bool _firstFlutterFrameRecorded = false;
bool _applicationInteractiveRecorded = false;

void _recordStartupMetric(String stage) {
  debugPrint(
      'VerityScribe startup stage=$stage elapsed_ms=${_startupClock.elapsedMilliseconds}');
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Paint a lightweight branded shell immediately; Supabase's local auth
  // restoration proceeds after the first frame, and authenticated screens are
  // only mounted after initialization completes.
  runApp(const SupabaseStartupGate());
  WidgetsBinding.instance.addPostFrameCallback((_) {
    if (_firstFlutterFrameRecorded) return;
    _firstFlutterFrameRecorded = true;
    _recordStartupMetric('first_flutter_frame');
  });
}

class SupabaseStartupGate extends StatefulWidget {
  const SupabaseStartupGate({super.key});

  @override
  State<SupabaseStartupGate> createState() => _SupabaseStartupGateState();
}

class _SupabaseStartupGateState extends State<SupabaseStartupGate> {
  late final Future<void> _initialization;

  @override
  void initState() {
    super.initState();
    final supabaseStartedAt = _startupClock.elapsedMilliseconds;
    _initialization = Supabase.initialize(
      url: const String.fromEnvironment('SUPABASE_URL'),
      publishableKey: const String.fromEnvironment('SUPABASE_PUBLISHABLE_KEY'),
    ).then((_) {
      debugPrint(
          'VerityScribe startup stage=supabase_initialized elapsed_ms=${_startupClock.elapsedMilliseconds - supabaseStartedAt}');
    });
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<void>(
        future: _initialization,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return MaterialApp(
              debugShowCheckedModeBanner: false,
              home: Scaffold(
                body: Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(
                      'VerityScribe could not initialize secure sign-in. Check the app configuration and reload.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ),
                ),
              ),
            );
          }
          if (snapshot.connectionState != ConnectionState.done) {
            return const MaterialApp(
              debugShowCheckedModeBanner: false,
              home: _StartupSplash(),
            );
          }
          if (!_applicationInteractiveRecorded) {
            _applicationInteractiveRecorded = true;
            WidgetsBinding.instance.addPostFrameCallback((_) {
              _recordStartupMetric('application_shell_interactive');
            });
          }
          return const ProviderScope(child: VerityScribeApp());
        },
      );
}

class _StartupSplash extends StatelessWidget {
  const _StartupSplash();

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF5F7FC),
        body: const Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.graphic_eq_rounded,
                  size: 42, color: Color(0xFF2458E6)),
              SizedBox(height: 14),
              Text('VerityScribe',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
              SizedBox(height: 12),
              SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ],
          ),
        ),
      );
}

class VerityScribeApp extends ConsumerWidget {
  const VerityScribeApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authStateProvider);
    final themeMode = ref.watch(themeProvider);
    ref.listen(authStateProvider, (previous, next) {
      final previousUserId = previous?.valueOrNull?.id;
      final nextUserId = next.valueOrNull?.id;
      if (previousUserId == nextUserId) return;

      authRouterRefresh.value++;
      // These providers contain patient-scoped responses. Never reuse their
      // cached values after sign-out or switching to a different account.
      ref.invalidate(patientProfileProvider);
      ref.invalidate(patientProfileDataProvider);
      ref.invalidate(appointmentsLiveProvider);
      ref.invalidate(patientConsultationsProvider);
      ref.invalidate(patientPrescriptionsProvider);
      ref.invalidate(patientReportsProvider);
      ref.invalidate(patientMedicalRecordProvider);
      ref.invalidate(patientTimelineProvider);
      ref.invalidate(patientInsuranceProvider);
      ref.invalidate(patientNotificationsProvider);
      ref.invalidate(patientMedicationScheduleProvider);
      ref.invalidate(patientPharmacyOrdersProvider);
      ref.invalidate(medicinesProvider);
      ref.invalidate(recordsProvider);
      ref.invalidate(prescriptionsProvider);
      ref.invalidate(reportsProvider);
    });

    final theme = themeMode == ThemeMode.dark ? AppTheme.dark : AppTheme.light;
    final authNotifier = ref.read(authStateProvider.notifier);
    if (auth.isLoading && authNotifier.isBootstrapping) {
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme,
        darkTheme: AppTheme.dark,
        themeMode: themeMode,
        home: const AuthBootstrapScreen(),
      );
    }
    if (auth.hasError && authNotifier.sessionRestoreFailed) {
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme,
        darkTheme: AppTheme.dark,
        themeMode: themeMode,
        home: AuthRestoreErrorScreen(
          onRetry: authNotifier.restoreSession,
        ),
      );
    }

    return AnimatedTheme(
      data: theme,
      duration: const Duration(milliseconds: 350),
      curve: Curves.easeInOutCubic,
      child: MaterialApp.router(
        debugShowCheckedModeBanner: false,
        title: 'VerityScribe',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: themeMode,
        routerConfig: appRouter,
      ),
    );
  }
}
