import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:verity_scribe/core/services/api_service.dart';

void main() {
  test('retries a protected directory request once after token refresh',
      () async {
    var token = 'stale-token';
    var refreshes = 0;
    var requests = 0;
    final client = HttpApiClient(
      accessToken: () async => token,
      refreshAccessToken: () async {
        refreshes++;
        token = 'fresh-token';
        return token;
      },
      client: MockClient((request) async {
        requests++;
        if (request.headers['authorization'] == 'Bearer stale-token') {
          return http.Response('{"detail":"Invalid access token"}', 401);
        }
        expect(request.headers['authorization'], 'Bearer fresh-token');
        return http.Response(
          jsonEncode({
            'data': [
              {'id': 'hospital-1', 'name': 'Test Hospital'}
            ],
            'meta': {'page': 1, 'page_size': 25, 'total': 1},
          }),
          200,
        );
      }),
    );

    final response = await client.get<Map<String, dynamic>>('/hospitals');

    expect(response.data['data'], hasLength(1));
    expect(refreshes, 1);
    expect(requests, 2);
  });

  test('does not retry more than once when refreshed token is rejected',
      () async {
    var refreshes = 0;
    var requests = 0;
    final client = HttpApiClient(
      accessToken: () async => 'rejected-token',
      refreshAccessToken: () async {
        refreshes++;
        return 'also-rejected-token';
      },
      client: MockClient((_) async {
        requests++;
        return http.Response('{"detail":"Invalid access token"}', 401);
      }),
    );

    await expectLater(
      client.get<Map<String, dynamic>>('/hospitals'),
      throwsA(isA<ApiException>()
          .having((error) => error.statusCode, 'statusCode', 401)),
    );
    expect(refreshes, 1);
    expect(requests, 2);
  });
}
