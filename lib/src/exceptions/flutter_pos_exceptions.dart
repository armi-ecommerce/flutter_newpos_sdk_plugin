part of flutter_newpos_sdk;

class FlutterPosException implements Exception {
  const FlutterPosException({
    required this.code,
    required this.message,
    this.details,
  });
  final String code;
  final String message;
  final Map<String, dynamic>? details;

  @override
  String toString() {
    return 'FlutterPosException(code: $code, message: $message, details: $details)';
  }
}

class BluetoothConnectionFailed extends FlutterPosException {
  BluetoothConnectionFailed({
    super.code = 'BLUETOOTH_CONNECTION_FAILED',
    super.message = 'Bluetooth connection failed',
  });
}
