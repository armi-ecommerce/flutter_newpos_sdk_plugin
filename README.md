# flutter_newpos_sdk

<img alt="NEWPOS N98 unit" src="https://i.imgur.com/6HkNhh2.png" width=100%></img>

A Flutter plugin that adds support for the NEWPOS N98 mPOS unit. Only available for Android.

## Example

This library has an implementation [example](https://github.com/Avila-Tek/flutter_newpos_sdk_plugin/tree/main/example) you can use to test all the features available in this plugin. You will need an N98 mPOS unit to test all the features.

## Getting Started

First, you must add the dependency to your Flutter app. To do so, add the latest release to your `pubspec.yaml` dependencies as follows.

```YAML
dependencies:
    flutter_newpos_sdk: 
        git:
            url: https://github.com/Avila-Tek/flutter_newpos_sdk_plugin.git
            ref: 0.2.0-stable

```

The next step is adding Bluetooth permission to your app. In your `android/app/src/main/AndroidManifest.xml` add: 
```xml
<!-- Tell Google Play Store that your app uses Bluetooth LE
     Set android:required="true" if bluetooth is necessary -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />

<!-- New Bluetooth permissions in Android 12
https://developer.android.com/about/versions/12/features/bluetooth-permissions -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- legacy for Android 11 or lower -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30"/>

<!-- legacy for Android 9 or lower -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="28" />
```

> Note: You may set `ref` to `stable` if you want to use the latest stable version available, but it may break your code if a breaking change is made.
>

Lastly, you will need to add the mPOS SDK's **AAR** library to your project's `android` folder. Then, declare the dependency in the app-level gradle.

```gradle
// ...

dependencies {
    // Add the path of the AAR file relative to the build.gradle file (recommended, avoid absolute **paths**)
    implementation files('path/to/mposlib-release.aar') 
}

```

This step is important, else your app will not be able to use any of the functions of the POS device.

You can [download the AAR file here](https://github.com/Avila-Tek/flutter_newpos_sdk_plugin/releases/download/0.1.0-stable/mposlib-release.aar).

### Release build tips

- This plugin bundles required XML assets (`AIDS.xml`, `bines.xml`, `needing_pin.xml`) via `pubspec.yaml`. If you fork the plugin, keep them listed so release builds include them.
- R8/ProGuard: a `consumer-rules.pro` is shipped that keeps `com.newpos.mposlib.*`. If you disable consumer rules, add the keep rule manually to avoid stripping classes in release.
- Request Bluetooth permissions **before** calling the plugin. The native layer now returns a `BLUETOOTH_PERMISSION_DENIED` error if the app has not granted runtime permissions.

#### Permisos en tiempo de ejecución (Android 7+)

- Android 7–11: además de `BLUETOOTH`/`BLUETOOTH_ADMIN`, necesitas `ACCESS_FINE_LOCATION` o `ACCESS_COARSE_LOCATION` para escanear BLE. Pide la ubicación en runtime antes de `scanBluetoothDevices` o `connectToBluetoothDevice`.
- Android 12+: usa `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT` (el ejemplo de manifest ya los incluye). No se necesita ubicación para escanear, pero Google Play puede seguir pidiéndote declarar el uso si la app infiere ubicación.

Ejemplo rápido con el paquete `permission_handler`:
```dart
import 'dart:io' show Platform;
import 'package:device_info_plus/device_info_plus.dart';

final status = await Permission.bluetoothScan.request();
final connect = await Permission.bluetoothConnect.request();

var locationGranted = true;
if (Platform.isAndroid) {
  final sdkInt = (await DeviceInfoPlugin().androidInfo).version.sdkInt;
  if (sdkInt >= 24 && sdkInt <= 30) {
    final location = await Permission.locationWhenInUse.request();
    locationGranted = location.isGranted;
  }
}

if (status.isGranted && connect.isGranted && locationGranted) {
  await FlutterNewposSdk.scanBluetoothDevices(timeout: Duration(seconds: 10));
}
```

## Usage

First, start by creating an instance of `FlutterNewposSdk`. This class defines all the methods available by the N98 unit.

```dart
final pos = FlutterNewposSdk.instance;
```

The first step to communicate with the device is connecting to it via Bluetooth. This **must** be done with the `FlutterNewposSdk.instance.connectToBluetoothDevice` method, instead of manually connecting via the OS Bluetooth settings.

The `connectToBluetoothDevice` method requires the device's MAC address parameter. In order to get the MAC address of the POS, you must scan the Bluetooth devices in-app. You can use the integrated `FlutterNewposSdk.instance.scanBluetoothDevices` method, or other Bluetooth library to do so.

> 📍Note: You must call `FlutterNewposSdk.instance.connectToBluetoothDevice` in every app session to communicate with the POS, even if it is already connected to the smartphone. So, it is adviced to remember the MAC address to avoid having to scan for Bluetooth devices every time the app is opened.

Once the POS is connected using the library API, you will see a Bluetooth indicator at the top-center side of the screen of the N98 device. This means you are ready to use all the available features!

## Reading a card

The next thing you might want to do is read a card to process a payment.

To do so, call the method `FlutterNewposSdk.instance.completeTransaction` and pass the amount of the transaction as a parameter. The method **must be called before introducing the card in the reader**. The POS will fail if you do it the other way around.

When reading the card, the POS might ask for the card's secret PIN if it is required. Then, a `CompleteTransactionResult` object will be returned with the card's data that will be **required** by the payment service.

---

For more information, you can read the [library documentation](https://avila-tek.github.io/flutter_newpos_sdk_plugin/flutter_newpos_sdk/flutter_newpos_sdk-library.html).
