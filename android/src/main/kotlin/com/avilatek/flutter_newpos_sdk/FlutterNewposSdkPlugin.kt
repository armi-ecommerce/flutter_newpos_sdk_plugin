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
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.IOException
import io.flutter.plugin.common.MethodChannel as MCLib
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

import java.io.InputStream

import java.util.*

/** FlutterNewposSdkPlugin */
class FlutterNewposSdkPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private lateinit var posManager: NpPosManager
  private var _activityBinding : ActivityPluginBinding? = null;
  private var activity: FlutterActivity? = null
  private var _pluginBinding : FlutterPlugin.FlutterPluginBinding? = null;
  private lateinit var _context : Context;

    // Constants
  private var defaultGetCardNumberTimeOut = 20
  private var defaultScanBlueDeviceTimeOut = 500
  private var defaultDisplayKeepShowTime = 3

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      Log.d("FlutterNewposSdkPlugin", "onReattachedToActivityForConfigChanges")
      activity = binding.getActivity() as FlutterActivity
      _activityBinding = binding;
    }
    
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
    
    override fun onDetachedFromActivityForConfigChanges() {
        _activityBinding = null;
    }
    override fun onDetachedFromActivity(){
        try {
            if (::posManager.isInitialized) {
                posManager.stopScan()
                posManager.disconnectDevice()
            }
        } catch (_: Exception) {
        }
        _activityBinding = null;
    }
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.d("FlutterNewposSdkPlugin", "onAttachedToActivity")
    _activityBinding = binding;
    activity = binding.activity as FlutterActivity
    channel = MethodChannel(_pluginBinding!!.binaryMessenger, "flutter_newpos_sdk/methods")
    Log.d("FlutterNewposSdkPlugin", "MethodChannel")
    channel.setMethodCallHandler(this)
    Log.d("FlutterNewposSdkPlugin", "setMethodCallHandler")

    Log.d("FlutterNewposSdkPlugin", "registrar")

    try {
        val assetManager = getApplicationContext()!!.assets
        /// Needing pin file
        val needingPin: InputStream = assetManager.open("needing_pin.xml")
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(needingPin)
        document.documentElement.normalize()

        Log.d("FlutterNewposSdkPlugin", "assetManager")


        /// AIDS file
        val aids: InputStream = assetManager.open("AIDS.xml")
        val documentBuilderFactoryAids = DocumentBuilderFactory.newInstance()
        val documentBuilderAids = documentBuilderFactoryAids.newDocumentBuilder()
        val documentAids = documentBuilderAids.parse(aids)
        documentAids.documentElement.normalize()
        Log.d("FlutterNewposSdkPlugin", "assetManagerAids")


        /// BINES file

        val bines: InputStream = assetManager.open("bines.xml")
        val documentBuilderFactoryBines = DocumentBuilderFactory.newInstance()
        val documentBuilderBines = documentBuilderFactoryBines.newDocumentBuilder()
        val documentBines = documentBuilderBines.parse(bines)
        documentBines.documentElement.normalize()

        Log.d("FlutterNewposSdkPlugin", "assetManagerBines")


        // El delegate del POS. Esta es la implementación del comportamiento que va a tomar el POS
        // al conectarse a la app
        val delegate = FlutterPosDelegate(channel, document, documentAids, documentBines)
        Log.d("FlutterNewposSdkPlugin", "delegate")
        posManager = NpPosManager.sharedInstance(_pluginBinding!!.applicationContext, delegate)
        Log.d("FlutterNewposSdkPlugin", "posManager")
    } catch (error: Throwable) {  // Catch any type of Throwable
        Log.e("FlutterNewposSdkPlugin", "Error occurred: $error")
        Log.e("FlutterNewposSdkPlugin", error.stackTraceToString())
        // Handle the error appropriately, e.g., display an error message or retry
    }
    

  }



  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.d("FlutterNewposSdkPlugin", "onAttachedToEngine")
      _pluginBinding = flutterPluginBinding;
    _context = flutterPluginBinding.getApplicationContext();
    // channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_newpos_sdk/methods")
    // Log.d("FlutterNewposSdkPlugin", "MethodChannel")
    
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
                    result.success(0);
                }
                "connectedCount" -> {
                    result.success(0);
                }
                "completeTransaction" -> {
                    try {
                        var amount = call.arguments as Int
                        posManager.getDeviceInfo()
                        var cardReadEntity = CardReadEntity();
                        cardReadEntity.setSupportFallback(true)
                        cardReadEntity.setTimeout(60)
                        cardReadEntity.setAmount(String.format(Locale.forLanguageTag("es-VE"), "%012d", amount))
                        cardReadEntity.setTradeType(0)
                        cardReadEntity.setSupportDukpt(true)
                        cardReadEntity.setTrackEncrypt(false)
                        posManager.readCard(cardReadEntity)
                        result.success(true)
                    } catch (error : Exception) {
                        result.error("COMPLETE_TRX_FAILED", error.message, "");
                    }

                }
                "scanBlueDevice" -> {
                    Log.d("MethodChannel/Kotlin", "scanBluetoothDevices method received")
                    result.success(true)

                    var timeout = call.arguments as? Int ?: defaultScanBlueDeviceTimeOut
                    posManager.scanBlueDevice(timeout)
                }
                "clearRids" -> {
                    try {
                        posManager.clearRids()
                        result.success(true)
                    } catch (error : Exception) {
                        result.error("CLEAR_RIDS_FAILED", error.message, "");
                    }
                }
                "clearAids" -> {
                    try {
                        posManager.clearAids()
                        result.success(true)
                    } catch (error : Exception) {
                        result.error("CLEAR_AIDS_FAILED", error.message, "");
                    }
                }
                "addRid" -> {
                    var rid = call.arguments as? String
                    Log.d("MethodChannel/Kotlin", "addRid method received: $rid")
                    if (rid == null) {
                        result.error("ADD_RID_NULL", "RID parameter is null", "")
                    }else {
                        posManager.addRid(rid)
                        result.success(true)
                    }
                }
                "addAid" -> {
                    var aid = call.arguments as? String
                    Log.d("MethodChannel/Kotlin", "addAid method received: $aid")
                    if (aid == null) {
                        result.error("ADD_AID_NULL", "AID parameter is null", "")
                    }else {
                        posManager.addAid(aid)
                        result.success(true)
                    }
                }
                "stopScan" -> {
                    Log.d("MethodChannel/Kotlin", "stopScan method received")
                    posManager.stopScan()
                    result.success(true)
                }
                "connectToBluetoothDevice" -> {
                    Log.d("MethodChannel/Kotlin", "connectToBluetoothDevice method received")
                    var macAddress = call.arguments as? String
                    if(macAddress == null) {
                        result.error("NullMacAddress", "Mac Address is null", "")
                    }else {
                        posManager.connectBluetoothDevice(macAddress)
                        result.success(true)
                    }
                }
                "getInputInfoFromKB" -> {
                    Log.d("MethodChannel/Kotlin", "getInputInfoFromKB method received")
                    val codeInputEntity = InputInfoEntity()
                    codeInputEntity.setInputType(1);
                    codeInputEntity.setTimeout(30);
                    codeInputEntity.setTitle("Inserte PIN");
                    codeInputEntity.setPan(call.arguments as? String)
                    posManager.getInputInfoFromKB(codeInputEntity);
                    result.success(true)
                }
                "getDeviceInfo" -> {
                    Log.d("MethodChannel/Kotlin", "getDeviceInfo method received")
                    try {
                        posManager.getDeviceInfo()
                        result.success(true)
                    } catch ( error : Exception) {
                        Log.e("MethodChannel/Kotlin", "getCardNumber failed $error")
                        result.error("GET_CARD_NUMBER_ERROR", "Error getting card number", error);
                    }
                }
                "getCardNumber" -> {
                    var timeout = call.arguments as? Int ?: defaultGetCardNumberTimeOut
                    Log.d("MethodChannel/Kotlin", "getCardNumber method received with timeout $timeout")
                    try {
                        posManager.getCardNumber(timeout)
                        result.success(true)
                    } catch ( error : Exception) {
                        Log.e("MethodChannel/Kotlin", "getCardNumber failed $error")
                        result.error("GET_CARD_NUMBER_ERROR", "Error getting card number", error);
                    }
                }
                "disconnectDevice" -> {
                    Log.d("MethodChannel/Kotlin", "disconnectDevice method received")
                    try {
                        posManager.disconnectDevice()
                        result.success(true)
                    } catch (e : Exception) {
                        result.error("DISCONNECT_DEVICE_ERROR", "Disconnect device failed", "")
                    }
                }
                "getCurrentBatteryStatus" -> {
                    posManager.getCurrentBatteryStatus()
                }
                "getEmvApduLog" -> {
                    Log.d("MethodChannel/Kotlin", "getEmvApduLog method received")
                    posManager.getEmvApduLog()
                }
                "updateMasterKey" -> {

                    var args: Map<String, Any> = call.arguments as? Map<String, Any> ?: emptyMap()
                    Log.d("MethodChannel/Kotlin", "updateMasterKey method received with args: $args, ${args["masterKey"]}, ${args["ifDukpt"]}")
                    var masterKey = args["masterKey"] as String?
                    var ifDukpt = args["ifDukpt"] as Boolean?
                    if(masterKey != null && ifDukpt != null) {
                        posManager.updateMasterKey(masterKey, ifDukpt)
                        result.success(true)
                    }else {
                        result.error("UPDATE_MASTER_KEY_ERROR", "One or more parameters to update master key is null", "")
                    }

                }
                "displayTextOnScreen" -> {
                    Log.d("MethodChannel/Kotlin", "displayTextOnScreen method received")

                    var args: Map<String, Any> = call.arguments as? Map<String, Any> ?: emptyMap()
                    var message = args["message"] as String
                    var keepShowTime = args["keepShowTime"] as? Int ?: defaultDisplayKeepShowTime

                    posManager.displayTextOnScreen(
                        keepShowTime,
                        message
                    )
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
    Log.d("FlutterNewposSdkPlugin", "Method called ${call.method} finished")
        
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
