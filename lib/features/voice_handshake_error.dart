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
  return 'Live voice could not connect. Check your connection and microphone permission, then retry.';
}
