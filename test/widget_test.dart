import 'package:flutter_test/flutter_test.dart';
import 'package:mic/main.dart';

void main() {
  testWidgets('Mic app smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const MicApp());
    expect(find.text('Mic'), findsOneWidget);
    expect(find.text('MIC OFF'), findsOneWidget);
  });
}
