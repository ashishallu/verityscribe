import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/core/services/live_gemini_frame.dart';

void main() {
  test('decodes a JSON Gemini envelope carried in a binary WebSocket frame', () {
    const envelope = '{"setupComplete":{}}';

    expect(decodeLiveGeminiJsonFrame(utf8.encode(envelope)), envelope);
  });

  test('does not classify raw PCM as a JSON protocol envelope', () {
    expect(decodeLiveGeminiJsonFrame(const [0, 1, 2, 3]), isNull);
  });

  test('does not classify a JSON array as a Gemini protocol envelope', () {
    expect(decodeLiveGeminiJsonFrame(utf8.encode('[]')), isNull);
  });
}
