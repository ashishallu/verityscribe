import 'dart:typed_data';

/// Serializes native PCM chunks and invalidates queued audio on interruption.
/// Normal turn completion only flushes the queue; it never stops the player.
class LiveAudioPlaybackQueue {
  Future<void> _tail = Future<void>.value();
  int _generation = 0;

  Future<void> enqueue(
    Uint8List chunk,
    Future<void> Function(Uint8List chunk) play,
  ) {
    final generation = _generation;
    final copy = Uint8List.fromList(chunk);
    final operation = _tail.catchError((Object _) {}).then((_) async {
      if (generation != _generation || copy.isEmpty) return;
      await play(copy);
    });
    _tail = operation;
    return operation;
  }

  /// Wait until chunks received so far have been submitted in order.
  Future<void> flush() => _tail;

  /// Invalidate queued chunks and stop the current player immediately.
  Future<void> interrupt(Future<void> Function() stop) async {
    _generation++;
    final abandonedQueue = _tail;
    // Detach any blocked feed future before awaiting the native player stop.
    // Some browser audio backends do not complete feedUint8FromStream after
    // stopPlayer(), which must never hold Hang Up or a new turn hostage.
    _tail = Future<void>.value();
    try {
      await stop();
    } finally {
      // Give an in-flight feed a short chance to settle without allowing a
      // browser/plugin bug to block cleanup indefinitely.
      try {
        await abandonedQueue.timeout(const Duration(milliseconds: 250));
      } catch (_) {}
    }
  }
}
