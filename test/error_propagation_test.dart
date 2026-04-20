import 'package:flutter/services.dart';
import 'package:flutter_newpos_sdk/flutter_newpos_sdk.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FlutterNewposSdk read-card error mapping', () {
    test('maps OnGetReadCardInfoError preserving code/message/details', () {
      final result = FlutterNewposSdk.mapReadCardEventForTest(
        const MethodCall(
          'OnGetReadCardInfoError',
          {
            'code': -1,
            'message': 'Card info entity is null',
          },
        ),
        defaultCode: 'GET_CARD_NUMBER_ERROR',
        defaultMessage: 'Error getting card information',
      );

      expect(result.card, isNull);
      expect(result.error, isNotNull);
      expect(result.error!.code, '-1');
      expect(result.error!.message, 'Card info entity is null');
      expect(result.error!.details?['code'], -1);
      expect(result.error!.details?['message'], 'Card info entity is null');
    });

    test('maps valid OnGetReadCardInfo payload as card result', () {
      final result = FlutterNewposSdk.mapReadCardEventForTest(
        const MethodCall(
          'OnGetReadCardInfo',
          {
            'cardNumber': '411111******1111',
            'readCardMethod': 1,
            'requiresPin': false,
            'ksn': 'KSN123',
          },
        ),
        defaultCode: 'COMPLETE_TRANSACTION_EXCEPTION',
        defaultMessage: 'Exception reading card',
      );

      expect(result.error, isNull);
      expect(result.card, isNotNull);
      expect(result.card!.cardNumber, '411111******1111');
      expect(result.card!.readCardMethod, 1);
      expect(result.card!.requiresPin, isFalse);
      expect(result.card!.ksn, 'KSN123');
    });

    test('uses default code/message when payload is invalid', () {
      final exception = FlutterNewposSdk.mapReadCardErrorForTest(
        'invalid-payload',
        defaultCode: 'GET_CARD_NUMBER_ERROR',
        defaultMessage: 'Error getting card information',
      );

      expect(exception.code, 'GET_CARD_NUMBER_ERROR');
      expect(exception.message, 'Error getting card information');
      expect(exception.details, isNull);
    });
  });
}
