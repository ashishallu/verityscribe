import 'package:go_router/go_router.dart';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import '../../features/ai_screen.dart' deferred as ai_screen;
import '../../features/alarms/alarms_screen.dart' deferred as alarms_screen;
import '../../features/authentication/auth_screens.dart';
import '../../features/home_screen.dart' deferred as home_screen;
import '../../features/medicines_screen.dart' deferred as medicines_screen;
import '../../features/notifications/notifications_screen.dart'
    deferred as notifications_screen;
import '../../features/profile_screen.dart' deferred as profile_screen;
import '../../features/profile_details.dart' deferred as profile_details;
import '../../features/medical_history_screen.dart' deferred as medical_history;
import '../../features/recording/recording_screens.dart'
    deferred as recording_screens;
import '../../core/services/voice_draft_service.dart';
import '../../features/records_screen.dart' deferred as records_screen;
import '../../features/scan/scan_screens.dart' deferred as scan_screens;
import '../../models/entities.dart';
import '../app_shell.dart';
import '../../features/live_care_screen.dart' deferred as live_care_screen;
import '../../features/live_clinical_screen.dart'
    deferred as live_clinical_screen;
import '../../features/live_health_record_screen.dart'
    deferred as live_health_record_screen;
import '../../features/ai_consultation_screen.dart'
    deferred as ai_consultation_screen;
import '../widgets/deferred_route_screen.dart';

/// Notifies GoRouter when the restored or active Supabase identity changes.
final authRouterRefresh = ValueNotifier<int>(0);

const _publicPaths = <String>{
  '/',
  '/onboarding',
  '/welcome',
  '/login',
  '/register',
  '/otp',
  '/biometric',
  '/health-profile',
  '/permissions',
};

final appRouter = GoRouter(
    initialLocation: '/',
    refreshListenable: authRouterRefresh,
    redirect: (context, state) {
      final path = state.uri.path;
      final hasSession = Supabase.instance.client.auth.currentSession != null;
      if (hasSession && _publicPaths.contains(path) && path != '/') {
        return '/home';
      }
      if (!hasSession && !_publicPaths.contains(path)) {
        final destination = Uri.encodeComponent(state.uri.toString());
        return '/login?next=$destination';
      }
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (_, __) => const SplashScreen()),
      GoRoute(
          path: '/onboarding',
          builder: (_, __) => const IntroScreen(
              title: 'Healthcare that listens.',
              body:
                  'Your consultations, records and day-to-day care are thoughtfully connected in one private space.',
              next: '/welcome')),
      GoRoute(
          path: '/welcome',
          builder: (_, __) => const IntroScreen(
              title: 'Meet your care companion.',
              body:
                  'Verity helps you stay informed and prepared for every health decision.',
              next: '/login')),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, __) => const RegisterScreen()),
      GoRoute(
          path: '/doctors',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_care_screen.loadLibrary,
                builder: (_) => live_care_screen.DoctorsDirectoryScreen(),
              )),
      GoRoute(
          path: '/appointments',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_care_screen.loadLibrary,
                builder: (_) => live_care_screen.AppointmentsLiveScreen(),
              )),
      GoRoute(
          path: '/book-appointment',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_care_screen.loadLibrary,
                builder: (_) => live_care_screen.AppointmentBookingScreen(),
              )),
      GoRoute(
          path: '/ai-appointment/:id',
          builder: (_, state) => DeferredRouteScreen(
                loadLibrary: ai_consultation_screen.loadLibrary,
                builder: (_) => ai_consultation_screen.AIAppointmentEntryScreen(
                    appointment: state.extra as LiveAppointment),
              )),
      GoRoute(
          path: '/ai-consultation/:id',
          builder: (_, state) => DeferredRouteScreen(
                loadLibrary: ai_consultation_screen.loadLibrary,
                builder: (_) => ai_consultation_screen.AIConsultationScreen(
                    appointment: state.extra as LiveAppointment),
              )),
      GoRoute(
          path: '/consultations-live',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_clinical_screen.loadLibrary,
                builder: (_) => live_clinical_screen.LiveConsultationsScreen(),
              )),
      GoRoute(
          path: '/prescriptions-live',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_clinical_screen.loadLibrary,
                builder: (_) => live_clinical_screen.LivePrescriptionsScreen(),
              )),
      GoRoute(
          path: '/reports-live',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_clinical_screen.loadLibrary,
                builder: (_) => live_clinical_screen.LiveReportsScreen(),
              )),
      GoRoute(
          path: '/live-health-record',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_health_record_screen.loadLibrary,
                builder: (_) =>
                    live_health_record_screen.LiveHealthRecordScreen(),
              )),
      GoRoute(path: '/otp', builder: (_, __) => const OtpScreen()),
      GoRoute(
          path: '/biometric',
          builder: (_, __) => const SetupScreen(
              title: 'Protect your health data',
              body: 'Set up biometric access for a faster, secure sign in.',
              next: '/health-profile')),
      GoRoute(
          path: '/health-profile',
          builder: (_, __) => const SetupScreen(
              title: 'A little about your health',
              body: 'This helps Verity personalise your care experience.',
              next: '/permissions')),
      GoRoute(
          path: '/permissions',
          builder: (_, __) => const SetupScreen(
              title: 'Choose what to share',
              body:
                  'You remain in control of each permission and connected device.',
              next: '/home')),
      GoRoute(
          path: '/notifications',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: notifications_screen.loadLibrary,
                builder: (_) => notifications_screen.NotificationsScreen(),
              )),
      GoRoute(
          path: '/insurance',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: live_clinical_screen.loadLibrary,
                builder: (_) => live_clinical_screen.LiveInsuranceScreen(),
              )),
      GoRoute(
          path: '/medical-history',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: medical_history.loadLibrary,
                builder: (_) => medical_history.MedicalHistoryScreen(),
              )),
      GoRoute(
          path: '/emergency-contacts',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: profile_details.loadLibrary,
                builder: (_) => profile_details.DetailsPage(
                    title: 'Emergency contacts',
                    items: profile_details.emergencyDetails),
              )),
      GoRoute(
          path: '/hospitals',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: profile_details.loadLibrary,
                builder: (_) => profile_details.DetailsPage(
                    title: 'Linked hospitals',
                    items: profile_details.hospitalDetails),
              )),
      GoRoute(
          path: '/devices',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: profile_details.loadLibrary,
                builder: (_) => profile_details.DetailsPage(
                    title: 'Connected devices',
                    items: profile_details.deviceDetails),
              )),
      GoRoute(
          path: '/record',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: recording_screens.loadLibrary,
                builder: (_) => recording_screens.RecordingScreen(),
              )),
      GoRoute(
          path: '/session-review',
          builder: (_, state) => DeferredRouteScreen(
                loadLibrary: recording_screens.loadLibrary,
                builder: (_) => recording_screens.SessionReviewScreen(
                    result: state.extra as VoiceDraftResult?),
              )),
      GoRoute(
          path: '/scan',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: scan_screens.loadLibrary,
                builder: (_) => scan_screens.ScanScreen(),
              )),
      GoRoute(
          path: '/medicine-result',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: scan_screens.loadLibrary,
                builder: (_) => scan_screens.MedicineResultScreen(),
              )),
      GoRoute(
          path: '/alarm',
          builder: (_, __) => DeferredRouteScreen(
                loadLibrary: alarms_screen.loadLibrary,
                builder: (_) => alarms_screen.AlarmReminderScreen(),
              )),
      GoRoute(
          path: '/medicine/:id',
          builder: (_, state) => DeferredRouteScreen(
                loadLibrary: medicines_screen.loadLibrary,
                builder: (_) => medicines_screen.MedicineDetailsScreen(
                    medicine: state.extra! as Medicine),
              )),
      ShellRoute(builder: (_, __, child) => AppShell(child: child), routes: [
        GoRoute(
            path: '/home',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: home_screen.loadLibrary,
                  builder: (_) => home_screen.HomeScreen(),
                )),
        GoRoute(
            path: '/ai',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: ai_screen.loadLibrary,
                  builder: (_) => ai_screen.AiScreen(),
                )),
        GoRoute(
            path: '/medicines',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: medicines_screen.loadLibrary,
                  builder: (_) => medicines_screen.MedicinesScreen(),
                )),
        GoRoute(
            path: '/alarms',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: alarms_screen.loadLibrary,
                  builder: (_) => alarms_screen.AlarmsScreen(),
                )),
        GoRoute(
            path: '/records',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: records_screen.loadLibrary,
                  builder: (_) => records_screen.RecordsScreen(),
                )),
        GoRoute(
            path: '/profile',
            builder: (_, __) => DeferredRouteScreen(
                  loadLibrary: profile_screen.loadLibrary,
                  builder: (_) => profile_screen.ProfileScreen(),
                ))
      ])
    ]);
