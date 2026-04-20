package com.avilatek.flutter_newpos_sdk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.newpos.mposlib.sdk.INpSwipeListener
import com.newpos.mposlib.sdk.*
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.w3c.dom.Document
import io.flutter.plugin.common.MethodChannel as MCLib
import javax.xml.parsers.DocumentBuilderFactory

import java.util.*

/** FlutterNewposSdkPlugin */
class FlutterNewposSdkPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private var channel: MethodChannel? = null
  private lateinit var posManager: NpPosManager
  private var _activityBinding: ActivityPluginBinding? = null
  private var _pluginBinding: FlutterPlugin.FlutterPluginBinding? = null

    // Constants
  private var defaultGetCardNumberTimeOut = 20
  private var defaultScanBlueDeviceTimeOut = 500
  private var defaultDisplayKeepShowTime = 3

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      Log.d("FlutterNewposSdkPlugin", "onReattachedToActivityForConfigChanges")
      _activityBinding = binding
      initPosManagerIfNeeded()
    }
    
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        safeStopAndDisconnect()
        channel?.setMethodCallHandler(null)
        channel = null
        _pluginBinding = null
    }
    
    override fun onDetachedFromActivityForConfigChanges() {
        _activityBinding = null
    }
    override fun onDetachedFromActivity(){
        safeStopAndDisconnect()
        _activityBinding = null
    }
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.d("FlutterNewposSdkPlugin", "onAttachedToActivity")
    _activityBinding = binding
    initPosManagerIfNeeded()
  }



  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.d("FlutterNewposSdkPlugin", "onAttachedToEngine")
    _pluginBinding = flutterPluginBinding
    if (channel == null) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_newpos_sdk/methods")
    }
    channel?.setMethodCallHandler(this)
    Log.d("FlutterNewposSdkPlugin", "MethodChannel ready")
  }

  override fun onMethodCall(call: MethodCall, result: MCLib.Result) {
    var activity = getActivity()
    Log.d("FlutterNewposSdkPlugin", "Activity $activity")

    if (activity == null) {
        Log.d("FlutterNewposSdkPlugin", "Activity is Null")
        result.error("NO_ACTIVITY", "Activity is null; ensure plugin is attached to an Activity", null)
        return
    }

    if (!hasBluetoothPermissions(activity)) {
        Log.d("FlutterNewposSdkPlugin", "Bluetooth permissions are not granted")
        result.error(
            "BLUETOOTH_PERMISSION_DENIED",
            "Bluetooth permissions are not granted. Request them before calling this method.",
            null
        )
        return
    }
    Log.d("FlutterNewposSdkPlugin", "Method called ${call.method}")


    when (call.method) {
        "flutterHotRestart" -> {
            result.success(0)
        }
        "connectedCount" -> {
            result.success(0)
        }
        else -> withInitializedPosManager(result) { manager ->
            when (call.method) {
                "completeTransaction" -> {
                    try {
                        var amount = call.arguments as Int
                        manager.getDeviceInfo()
                        var cardReadEntity = CardReadEntity()
                        cardReadEntity.setSupportFallback(true)
                        cardReadEntity.setTimeout(60)
                        cardReadEntity.setAmount(String.format(Locale.forLanguageTag("es-VE"), "%012d", amount))
                        cardReadEntity.setTradeType(0)
                        cardReadEntity.setSupportDukpt(true)
                        cardReadEntity.setTrackEncrypt(false)
                        manager.readCard(cardReadEntity)
                        result.success(true)
                    } catch (error: Exception) {
                        result.error("COMPLETE_TRX_FAILED", error.message, "")
                    }

                }
                "scanBlueDevice" -> {
                    Log.d("MethodChannel/Kotlin", "scanBluetoothDevices method received")
                    result.success(true)

                    var timeout = call.arguments as? Int ?: defaultScanBlueDeviceTimeOut
                    manager.scanBlueDevice(timeout)
                }
                "clearRids" -> {
                    try {
                        manager.clearRids()
                        result.success(true)
                    } catch (error: Exception) {
                        result.error("CLEAR_RIDS_FAILED", error.message, "")
                    }
                }
                "clearAids" -> {
                    try {
                        manager.clearAids()
                        result.success(true)
                    } catch (error: Exception) {
                        result.error("CLEAR_AIDS_FAILED", error.message, "")
                    }
                }
                "addRid" -> {
                    var rid = call.arguments as? String
                    Log.d("MethodChannel/Kotlin", "addRid method received: $rid")
                    if (rid == null) {
                        result.error("ADD_RID_NULL", "RID parameter is null", "")
                    } else {
                        manager.addRid(rid)
                        result.success(true)
                    }
                }
                "addAid" -> {
                    var aid = call.arguments as? String
                    Log.d("MethodChannel/Kotlin", "addAid method received: $aid")
                    if (aid == null) {
                        result.error("ADD_AID_NULL", "AID parameter is null", "")
                    } else {
                        manager.addAid(aid)
                        result.success(true)
                    }
                }
                "stopScan" -> {
                    Log.d("MethodChannel/Kotlin", "stopScan method received")
                    manager.stopScan()
                    result.success(true)
                }
                "connectToBluetoothDevice" -> {
                    Log.d("MethodChannel/Kotlin", "connectToBluetoothDevice method received")
                    var macAddress = call.arguments as? String
                    if (macAddress == null) {
                        result.error("NullMacAddress", "Mac Address is null", "")
                    } else {
                        manager.connectBluetoothDevice(macAddress)
                        result.success(true)
                    }
                }
                "getInputInfoFromKB" -> {
                    Log.d("MethodChannel/Kotlin", "getInputInfoFromKB method received")
                    val codeInputEntity = InputInfoEntity()
                    codeInputEntity.setInputType(1)
                    codeInputEntity.setTimeout(30)
                    codeInputEntity.setTitle("Inserte PIN")
                    codeInputEntity.setPan(call.arguments as? String)
                    manager.getInputInfoFromKB(codeInputEntity)
                    result.success(true)
                }
                "getDeviceInfo" -> {
                    Log.d("MethodChannel/Kotlin", "getDeviceInfo method received")
                    try {
                        manager.getDeviceInfo()
                        result.success(true)
                    } catch (error: Exception) {
                        Log.e("MethodChannel/Kotlin", "getCardNumber failed $error")
                        result.error("GET_CARD_NUMBER_ERROR", "Error getting card number", error)
                    }
                }
                "getCardNumber" -> {
                    var timeout = call.arguments as? Int ?: defaultGetCardNumberTimeOut
                    Log.d("MethodChannel/Kotlin", "getCardNumber method received with timeout $timeout")
                    try {
                        manager.getCardNumber(timeout)
                        result.success(true)
                    } catch (error: Exception) {
                        Log.e("MethodChannel/Kotlin", "getCardNumber failed $error")
                        result.error("GET_CARD_NUMBER_ERROR", "Error getting card number", error)
                    }
                }
                "disconnectDevice" -> {
                    Log.d("MethodChannel/Kotlin", "disconnectDevice method received")
                    try {
                        manager.disconnectDevice()
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("DISCONNECT_DEVICE_ERROR", "Disconnect device failed", "")
                    }
                }
                "getCurrentBatteryStatus" -> {
                    manager.getCurrentBatteryStatus()
                }
                "getEmvApduLog" -> {
                    Log.d("MethodChannel/Kotlin", "getEmvApduLog method received")
                    manager.getEmvApduLog()
                }
                "updateMasterKey" -> {

                    var args: Map<String, Any> = call.arguments as? Map<String, Any> ?: emptyMap()
                    Log.d("MethodChannel/Kotlin", "updateMasterKey method received with args: $args, ${args["masterKey"]}, ${args["ifDukpt"]}")
                    var masterKey = args["masterKey"] as String?
                    var ifDukpt = args["ifDukpt"] as Boolean?
                    if (masterKey != null && ifDukpt != null) {
                        manager.updateMasterKey(masterKey, ifDukpt)
                        result.success(true)
                    } else {
                        result.error("UPDATE_MASTER_KEY_ERROR", "One or more parameters to update master key is null", "")
                    }

                }
                "displayTextOnScreen" -> {
                    Log.d("MethodChannel/Kotlin", "displayTextOnScreen method received")

                    var args: Map<String, Any> = call.arguments as? Map<String, Any> ?: emptyMap()
                    var message = args["message"] as String
                    var keepShowTime = args["keepShowTime"] as? Int ?: defaultDisplayKeepShowTime

                    manager.displayTextOnScreen(
                        keepShowTime,
                        message
                    )
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    Log.d("FlutterNewposSdkPlugin", "Method called ${call.method} finished")
        
  }

  private fun initPosManagerIfNeeded() {
    if (::posManager.isInitialized) {
        return
    }

    val pluginBinding = _pluginBinding
    val methodChannel = channel
    if (pluginBinding == null || methodChannel == null) {
        Log.w("FlutterNewposSdkPlugin", "Skipping POS manager init. Engine binding or channel is not available yet.")
        return
    }

    try {
        val document = parseXmlAsset("needing_pin.xml")
        val documentAids = parseXmlAsset("AIDS.xml")
        val documentBines = parseXmlAsset("bines.xml")

        // Delegate that routes POS callbacks to Flutter.
        val delegate = FlutterPosDelegate(methodChannel, document, documentAids, documentBines)
        posManager = NpPosManager.sharedInstance(pluginBinding.applicationContext, delegate)
        Log.d("FlutterNewposSdkPlugin", "posManager initialized")
    } catch (error: Throwable) {
        Log.e("FlutterNewposSdkPlugin", "Error initializing POS manager: $error")
        Log.e("FlutterNewposSdkPlugin", error.stackTraceToString())
    }
  }

  private fun parseXmlAsset(assetName: String): Document {
    val applicationContext = getApplicationContext()
        ?: throw IllegalStateException("Application context is not available")

    applicationContext.assets.open(assetName).use { input ->
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(input)
        document.documentElement.normalize()
        return document
    }
  }

  private fun withInitializedPosManager(
    result: MCLib.Result,
    action: (NpPosManager) -> Unit
  ) {
    if (!::posManager.isInitialized) {
        result.error(
            "POS_NOT_INITIALIZED",
            "POS manager is not initialized. Ensure plugin is attached to an Activity.",
            null
        )
        return
    }

    action(posManager)
  }

  private fun safeStopAndDisconnect() {
    try {
        if (::posManager.isInitialized) {
            posManager.stopScan()
            posManager.disconnectDevice()
        }
    } catch (_: Exception) {
    }
  }

  fun getApplicationContext(): Context? {
    return _pluginBinding?.applicationContext ?: null
    }

    fun getActivity(): Activity? {
        return _activityBinding?.activity ?: null
    }

    private fun hasBluetoothPermissions(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED &&
                (
                    ActivityCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(
                            activity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
        }
    }

 
}
