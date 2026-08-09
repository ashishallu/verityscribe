import 'package:go_router/go_router.dart';
import '../../features/ai_screen.dart';
import '../../features/alarms/alarms_screen.dart';
import '../../features/authentication/auth_screens.dart';
import '../../features/home_screen.dart';
import '../../features/medicines_screen.dart';
import '../../features/notifications/notifications_screen.dart';
import '../../features/profile_screen.dart';
import '../../features/profile_details.dart';
import '../../features/medical_history_screen.dart';
import '../../features/records_screen.dart';
import '../../features/recording/recording_screens.dart';
import '../../features/scan/scan_screens.dart';
import '../../models/entities.dart';
import '../app_shell.dart';
import '../../features/live_care_screen.dart';

final appRouter = GoRouter(initialLocation: '/', routes: [
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
  GoRoute(path: '/doctors', builder: (_, __) => const DoctorsDirectoryScreen()),
  GoRoute(path: '/appointments', builder: (_, __) => const AppointmentsLiveScreen()),
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
      path: '/notifications', builder: (_, __) => const NotificationsScreen()),
  GoRoute(
      path: '/insurance',
      builder: (_, __) => const DetailsPage(
          title: 'Insurance dashboard', items: insuranceDetails)),
  GoRoute(
      path: '/medical-history',
      builder: (_, __) => const MedicalHistoryScreen()),
  GoRoute(
      path: '/emergency-contacts',
      builder: (_, __) => const DetailsPage(
          title: 'Emergency contacts', items: emergencyDetails)),
  GoRoute(
      path: '/hospitals',
      builder: (_, __) =>
          const DetailsPage(title: 'Linked hospitals', items: hospitalDetails)),
  GoRoute(
      path: '/devices',
      builder: (_, __) =>
          const DetailsPage(title: 'Connected devices', items: deviceDetails)),
  GoRoute(path: '/record', builder: (_, __) => const RecordingScreen()),
  GoRoute(
      path: '/session-review', builder: (_, __) => const SessionReviewScreen()),
  GoRoute(path: '/scan', builder: (_, __) => const ScanScreen()),
  GoRoute(
      path: '/medicine-result',
      builder: (_, __) => const MedicineResultScreen()),
  GoRoute(path: '/alarm', builder: (_, __) => const AlarmReminderScreen()),
  GoRoute(
      path: '/medicine/:id',
      builder: (_, state) =>
          MedicineDetailsScreen(medicine: state.extra! as Medicine)),
  ShellRoute(builder: (_, __, child) => AppShell(child: child), routes: [
    GoRoute(path: '/home', builder: (_, __) => const HomeScreen()),
    GoRoute(path: '/ai', builder: (_, __) => const AiScreen()),
    GoRoute(path: '/medicines', builder: (_, __) => const MedicinesScreen()),
    GoRoute(path: '/alarms', builder: (_, __) => const AlarmsScreen()),
    GoRoute(path: '/records', builder: (_, __) => const RecordsScreen()),
    GoRoute(path: '/profile', builder: (_, __) => const ProfileScreen())
  ])
]);
