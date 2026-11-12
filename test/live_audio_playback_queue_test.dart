import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:verity_scribe/core/services/live_audio_playback_queue.dart';

void main() {
  test('PCM chunks are fed in order and flush preserves the final chunk',
      () async {
    final queue = LiveAudioPlaybackQueue();
    final firstFeed = Completer<void>();
    final played = <int>[];
    var stopCalls = 0;

    final first = queue.enqueue(Uint8List.fromList([1]), (chunk) async {
      played.add(chunk.single);
      await firstFeed.future;
    });
    final finalChunk = queue.enqueue(
      Uint8List.fromList([2]),
      (chunk) async => played.add(chunk.single),
    );
    final flushed = queue.flush();

    await Future<void>.delayed(Duration.zero);
    expect(played, [1]);
    expect(stopCalls, 0);

    firstFeed.complete();
    await Future.wait([first, finalChunk, flushed]);
    expect(played, [1, 2]);
    expect(stopCalls, 0);
  });

  test('explicit interruption stops player and discards queued stale PCM',
      () async {
    final queue = LiveAudioPlaybackQueue();
    final activeFeed = Completer<void>();
    final activeFeedStarted = Completer<void>();
    final played = <int>[];
    var stopped = false;

    final active = queue.enqueue(Uint8List.fromList([1]), (chunk) async {
      played.add(chunk.single);
      activeFeedStarted.complete();
      await activeFeed.future;
    });
    final stale = queue.enqueue(
      Uint8List.fromList([2]),
      (chunk) async => played.add(chunk.single),
    );
    await activeFeedStarted.future;
    final interrupted = queue.interrupt(() async {
      stopped = true;
    });

    await Future<void>.delayed(Duration.zero);
    expect(stopped, isTrue);
    activeFeed.complete();
    await Future.wait([active, stale, interrupted]);
    expect(played, [1]);
  });

  test('interruption does not wait forever for a stalled browser feed',
      () async {
    final queue = LiveAudioPlaybackQueue();
    final stalledFeed = Completer<void>();
    var stopped = false;

    unawaited(
        queue.enqueue(Uint8List.fromList([1]), (_) => stalledFeed.future));
    await Future<void>.delayed(Duration.zero);
    await queue
        .interrupt(() async => stopped = true)
        .timeout(const Duration(seconds: 1));

    expect(stopped, isTrue);
    var nextChunkPlayed = false;
    await queue.enqueue(Uint8List.fromList([2]), (_) async {
      nextChunkPlayed = true;
    });
    expect(nextChunkPlayed, isTrue);
    stalledFeed.complete();
  });
}
