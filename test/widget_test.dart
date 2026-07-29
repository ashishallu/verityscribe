import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:verity_scribe/main.dart';

void main() {
  testWidgets('VerityScribe launches', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: VerityScribeApp()));
    expect(find.text('VerityScribe'), findsOneWidget);
  });
}
