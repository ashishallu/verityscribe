abstract class Endpoints {
  static const login = '/login',
      register = '/register',
      verifyOtp = '/verify-otp',
      profile = '/profile',
      appointments = '/appointments',
      medicines = '/medicines',
      records = '/records',
      chat = '/chat',
      consultation = '/consultation',
      notifications = '/notifications';
}

const apiBaseUrl = String.fromEnvironment('API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8000/api/v1');
