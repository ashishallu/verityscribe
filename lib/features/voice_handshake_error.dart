/// Browser WebSocket APIs often hide the HTTP response body for a rejected
/// upgrade, but their exception still contains the handshake status code.
bool isDefinitiveVoiceAuthorizationFailure(Object error) {
  final message = error.toString();
  return RegExp(
    r'(?:HTTP(?:/\d(?:\.\d)?)?\s+|HTTP status code:\s*|status(?: code)?[=: ]+)(401|403)\b',
    caseSensitive: false,
  ).hasMatch(message);
}

String voiceHandshakeFailureMessage(Object error) {
  if (isDefinitiveVoiceAuthorizationFailure(error)) {
    return 'The voice server rejected this session (401/403). Please sign in again, then retry. If it continues, the app origin may not be authorized.';
  }
  // Surface the real transport cause, but redact any credential that a
  // WebSocket implementation might include in a failed request URI.
  final detail = error
      .toString()
      .replaceAll(RegExp(r'access_token=[^&\s)]+', caseSensitive: false),
          'access_token=[redacted]')
      .replaceAll(RegExp(r'Bearer\s+[A-Za-z0-9._~-]+', caseSensitive: false),
          'Bearer [redacted]')
      .replaceAll(
          RegExp(r'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+'),
          '[credential redacted]')
      .trim();
  if (detail.isEmpty) {
    return 'Live voice could not connect. Check your connection and microphone permission, then retry.';
  }
  final concise = detail.length > 180 ? '${detail.substring(0, 180)}…' : detail;
  return 'Live voice connection failed: $concise';
}
