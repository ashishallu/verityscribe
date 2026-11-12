import 'dart:convert';

/// Gemini's direct Live WebSocket can deliver JSON protocol envelopes in a
/// binary WebSocket frame. Decode those before treating binary frames as PCM.
/// Raw audio frames (or malformed frames) return null for the caller to handle.
String? decodeLiveGeminiJsonFrame(List<int> bytes) {
  try {
    final text = utf8.decode(bytes, allowMalformed: false);
    if (!text.trimLeft().startsWith('{')) return null;
    final decoded = jsonDecode(text);
    return decoded is Map<String, dynamic> ? text : null;
  } on FormatException {
    return null;
  }
}
