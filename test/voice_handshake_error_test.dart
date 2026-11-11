import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/features/voice_handshake_error.dart';

void main() {
  test('recognizes definitive WebSocket 401/403 handshake rejections', () {
    expect(
      isDefinitiveVoiceAuthorizationFailure(
        Exception('WebSocket HTTP status code: 403'),
      ),
      isTrue,
    );
    expect(
      isDefinitiveVoiceAuthorizationFailure(
        Exception('HTTP/1.1 401 Unauthorized'),
      ),
      isTrue,
    );
    expect(
      voiceHandshakeFailureMessage(Exception('HTTP status code: 403')),
      contains('server rejected'),
    );
  });

  test('does not classify transient transport errors as authorization', () {
    expect(
      isDefinitiveVoiceAuthorizationFailure(
        Exception('Connection reset by peer'),
      ),
      isFalse,
    );
  });
}
