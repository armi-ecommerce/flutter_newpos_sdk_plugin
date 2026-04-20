package com.avilatek.flutter_newpos_sdk

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import com.newpos.mposlib.sdk.CardInfoEntity
import com.newpos.mposlib.sdk.DeviceInfoEntity
import com.newpos.mposlib.sdk.INpSwipeListener
import io.flutter.Log
import io.flutter.plugin.common.MethodChannel
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException

/// newPOS SDK Delegate. Declares the behavior and functionality to be executed when instantiating
/// the [NpPosManager] class.
class FlutterPosDelegate(private val channel: MethodChannel, private val documentNeedPin: Document, private val documentAids: Document, private val documentBines: Document) : INpSwipeListener {


    fun needsPin(code: String): Boolean {
        try {
            documentNeedPin.documentElement.normalize()
    
            val rowNodes = documentNeedPin.getElementsByTagName("ROW")
    
            for (index in 0 until rowNodes.length) {
                val rowNode = rowNodes.item(index)
    
                if (rowNode.nodeType == Node.ELEMENT_NODE) {
                    val rowElement = rowNode as Element
    
                    if (code == rowElement.getElementsByTagName("tag_res").item(0).textContent) {
                        return rowElement.getElementsByTagName("need_pin").item(0).textContent == "Y"
                    }
                }
            }
    
            return false
        } catch (e: ParserConfigurationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: SAXException) {
            throw e
        }
    }

    fun getCardType(tag4F: String, card: String): String? {
        var cardType : String = ""
        try {
            /// Validate with 'AIDS.xml'
            documentAids.documentElement.normalize()
            val rowNodes = documentNeedPin.getElementsByTagName("ROW")
            for (index in 0 until rowNodes.length) {
                val rowNode = rowNodes.item(index)
    
                if (rowNode.nodeType == Node.ELEMENT_NODE) {
                    val rowElement = rowNode as Element
    
                    if (tag4F == rowElement.getElementsByTagName("AID").item(0)?.textContent ?: "") {
                        cardType = rowElement.getElementsByTagName("card_type").item(0).textContent;
                    }
                }
            }
            /// How many card types
            /// if contains more than one card type should validate with bines.xml
            val cardTypes = cardType.split("/");
            val binCard = card.substring(0, 6).toBigInteger()
            Log.d("BinCard", binCard.toString())

            if (cardTypes.size > 1 || cardType.isEmpty()) {
                documentBines.documentElement.normalize()
                val rowNodesBines = documentBines.getElementsByTagName("ROW")
                for (index in 0 until rowNodesBines.length) {
                    val rowNode = rowNodesBines.item(index)
        
                    if (rowNode.nodeType == Node.ELEMENT_NODE) {
                        val rowElement = rowNode as Element
                        
                        val rangoInicio = rowElement.getElementsByTagName("low").item(0).textContent.toBigInteger()
                        val rangoFin = rowElement.getElementsByTagName("high").item(0).textContent.toBigInteger()
                        Log.d("BinCard Inicio", rangoInicio.toString())
                        Log.d("BinCard value", binCard.toString())
                        Log.d("BinCard Fin", rangoFin.toString())

                        if (binCard in rangoInicio..rangoFin) {
                            cardType =  rowElement.getElementsByTagName("card_type").item(0).textContent
                        }
                
                    }
                }
                // val bines = bin.split(",");
                // val cardType = when {
                //     bines.length > 1 -> "Credito"
                //     else -> bin
                // }
                
            }

    
            return cardType
        } catch (e: ParserConfigurationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: SAXException) {
            throw e
        }
    }

    override fun onDeviceDisConnected() {
        Log.d("onDeviceDisConnected","🛜❌ The POS has been disconnected!")
        Handler(Looper.getMainLooper()).post {
            /// Update to Flutter engine device connection status
            channel.invokeMethod("OnDeviceConnection", false)
            /// Return to Flutter method result successfully
            channel.invokeMethod("OnDeviceDisConnected", true)
        }

    }

    @SuppressLint("MissingPermission")
    override fun onScannerResult(devInfo: BluetoothDevice?) {
        Log.d("onScannerResult","--- 🛜NEW SCAN RESULT ---")
        Log.d("onScannerResult","--- 🛜 LESANPI ---")
        val data = hashMapOf<String, Any>()
        var address = devInfo?.address ?: ""
        var name = devInfo?.name ?: ""
        var type = devInfo?.type ?: 0
        data["address"] = address
        data["name"] = name
        data["type"] = type
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnScanResponse", data)
        }

        Log.d("onScannerResult","ADDRESS -> ${devInfo?.address}")
        Log.d("onScannerResult","NAME -> ${devInfo?.name}")
        Log.d("onScannerResult","-------------------------")
    }

    override fun onDeviceConnected() {
        Log.d("onDeviceConnected","🛜✅ The POS has been connected, device connection changed")
        Handler(Looper.getMainLooper()).post {
            /// Update to Flutter engine device connection status
            channel.invokeMethod("OnDeviceConnection", true)
            /// Return to Flutter method result successfully
            channel.invokeMethod("OnDeviceConnected", true)
        }
    }

    override fun onGetDeviceInfo(info: DeviceInfoEntity?) {
        var firmwareVersion = info?.firmwareVer
        var deviceType = info?.deviceTypeStr
        var ksn = info?.ksn
        var currentElePer = info?.currentElePer
        Log.d("onGetDeviceInfo", "--- POS DEVICE INFO ---")
        Log.d("onGetDeviceInfo","FIRMWARE VERSION -> $firmwareVersion")
        Log.d("onGetDeviceInfo","DEVICE TYPE -> $deviceType")
        Log.d("onGetDeviceInfo","KSN -> $ksn")
        Log.d("onGetDeviceInfo","CURRENT ELE PER -> $currentElePer")
        Log.d("onGetDeviceInfo","-----------------------")

        val data = hashMapOf<String, Any?>()
        data["firmwareVersion"] = firmwareVersion
        data["deviceType"] = deviceType
        data["ksn"] = ksn
        data["currentElePer"] = currentElePer
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnGetDeviceInfo", data)
            Log.d("onGetDeviceInfo","OnGetDeviceInfo method invoked")
        }

    }

    override fun onGetTransportSessionKey(encryTransportKey: String?) {
        Log.d("onGetTransportSessionKey", "🔐 Transport Session Key $encryTransportKey")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnGetTransportSessionKey", encryTransportKey)
        }
    }

    override fun onUpdateMasterKeySuccess() {
        Log.d("onUpdateMasterKeySuccess","✅ Master key updated!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnUpdateMasterKeySuccess", true)
        }
    }

    override fun onUpdateWorkingKeySuccess() {
        Log.d("onUpdateWorkingKeySuccess","✅ Working key updated!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnUpdateWorkingKeySuccess", true)
        }
    }

    override fun onAddAidSuccess() {
        Log.d("onAddAidSuccess","✅ AID added successfully!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnAddAidSuccess", true)
        }
    }

    override fun onAddRidSuccess() {
        Log.d("onAddRidSuccess","✅ RID added successfully!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnAddRidSuccess", true)
        }
    }

    override fun onClearAids() {
        Log.d("AIDS cleared!", "AIDS cleared!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnClearAids", true)
        }
    }

    override fun onClearRids() {
        Log.d("RIDS cleared!", "RIDS cleared!")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnClearRids", true)
        }
    }

    override fun onGetCardNumber(cardNum: String?) {
        Log.d("onGetCardNumber","CARD NUMBER -> $cardNum")
    }

    override fun onGetDeviceBattery(result: Boolean) {
        Log.d("🪫 POS BATTERY", "Battery Status $result")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnGetDeviceBattery", result)
        }
    }

    override fun onDetachedIC() {
        Log.d("IC detached!", "IC Detached!")
    }

    override fun onGetReadCardInfo(cardInfoEntity: CardInfoEntity?) {
        Log.d("onGetReadCardInfo","----- CARD INFO -----")
        Log.d("onGetReadCardInfo","CARD NUMBER -> ${cardInfoEntity?.cardNumber}")
        Log.d("onGetReadCardInfo","CARD TYPE -> ${cardInfoEntity?.cardType}")
        Log.d("onGetReadCardInfo","CARDHOLDER NAME -> ${cardInfoEntity?.cardholderName}")
        Log.d("onGetReadCardInfo","CSN -> ${cardInfoEntity?.csn}")
        Log.d("onGetReadCardInfo","ENCRYPT PIN -> ${cardInfoEntity?.encryptPin}")
        Log.d("onGetReadCardInfo","ENCRYPTED SN -> ${cardInfoEntity?.encryptedSN}")
        Log.d("onGetReadCardInfo","EXPIRATION DATE -> ${cardInfoEntity?.expDate}")
        Log.d("onGetReadCardInfo","IC55 DATA -> ${cardInfoEntity?.ic55Data}")
        Log.d("onGetReadCardInfo","TRACK 1 -> ${cardInfoEntity?.track1}")
        Log.d("onGetReadCardInfo","TRACK 2 -> ${cardInfoEntity?.track2}")
        Log.d("onGetReadCardInfo","TRACK 3 -> ${cardInfoEntity?.track3}")
        Log.d("onGetReadCardInfo","TUSN -> ${cardInfoEntity?.tusn}")
        Log.d("onGetReadCardInfo","TUSN -> ${cardInfoEntity?.getKsn()}")
        Log.d("onGetReadCardInfo","---------------------")

        if (cardInfoEntity == null) {
            val errorData = hashMapOf<String, Any?>(
                "code" to -1,
                "message" to "Card info entity is null"
            )
            Handler(Looper.getMainLooper()).post {
                channel.invokeMethod("OnReceiveErrorCode", errorData)
                // Completes read-card flows so Dart callers do not wait until timeout.
                channel.invokeMethod("OnGetReadCardInfoError", errorData)
            }
            return
        }

        val cardNumber = cardInfoEntity?.cardNumber
        val cardType = cardInfoEntity?.cardType
        val cardholderName = cardInfoEntity?.cardholderName
        val csn = cardInfoEntity?.csn
        val encryptPin = cardInfoEntity?.encryptPin
        val encryptedSN = cardInfoEntity?.encryptedSN
        val expDate = cardInfoEntity?.expDate
        val ic55Data = cardInfoEntity?.ic55Data
        val track1 = cardInfoEntity?.track1
        val track2 = cardInfoEntity?.track2
        val track3 = cardInfoEntity?.track3
        val tusn = cardInfoEntity?.tusn
        val ksn = cardInfoEntity?.getKsn()

        val data = hashMapOf<String, Any?>()
        data["cardNumber"] = cardNumber
        data["readCardMethod"] = cardType
        data["cardholderName"] = cardholderName
        data["csn"] = csn
        data["encryptPin"] = encryptPin
        data["encryptedSN"] = encryptedSN
        data["expDate"] = expDate
        data["ic55Data"] = ic55Data
        data["track1"] = track1
        data["track2"] = track2
        data["track3"] = track3
        data["tusn"] = tusn
        data["ksn"] = ksn
        Handler(Looper.getMainLooper()).post {
            Log.d("Ger Read Card Info","Read Requires pin")

            if (ic55Data.isNullOrEmpty()) {
                data["requiresPin"] = false
                channel.invokeMethod("OnGetReadCardInfo", data)
                return@post
            }

            val tag9F34Parts = ic55Data.split("9F34").toTypedArray()
            val tag9F34 = if (tag9F34Parts.size > 1) tag9F34Parts[1] else ""
            var tag4F: String? = null
            val array4F = ic55Data.split("4F").toTypedArray()
            if (array4F.size > 2) {
                val candidate = array4F[2]
                if (candidate.length >= 2) tag4F = candidate.takeLast(2)
            } else if (array4F.size > 1) {
                val candidate = array4F[1]
                if (candidate.length >= 2) tag4F = candidate.takeLast(2)
            }
            Log.d("Tag4F",tag4F ?: "")

            val tagRes = if (tag9F34.length >= 4) tag9F34.substring(2, 4) else ""
            val requiresPin: Boolean = if (tagRes.isEmpty()) false else needsPin(tagRes)
            data["requiresPin"] = requiresPin
            val cardTypeRead: String? = getCardType(tag4F ?: "", cardNumber ?: "");
            data["cardType"] = cardTypeRead

            Log.d("Ger Read Card Info","Read Requires pin finished")
            channel.invokeMethod("OnGetReadCardInfo", data)


        }

    }

    override fun onGetReadInputInfo(inputInfo: String?) {
        Log.d("onGetReadInputInfo","INPUT INFO -> $inputInfo")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnGetReadInputInfo", inputInfo)
        }
    }

    override fun onGetICCardWriteback(result: Boolean) {
        Log.d("onGetICCardWriteback","IC CARD WRITEBACK -> $result")
    }

    override fun onCancelReadCard() {
        Log.d("onCancelReadCard","❌ Cancelled card reading")
    }

    override fun onGetCalcMacResult(encryMacData: String?) {
        print("EncryMacData -> $encryMacData")
    }

    override fun onUpdateFirmwareProcess(percent: Float) {
        TODO("Not yet implemented")
    }

    override fun onUpdateFirmwareSuccess() {
        TODO("Not yet implemented")
    }

    override fun onGenerateQRCodeSuccess() {
        print("✅ GENERATE QR CODE SUCCEEDED")
    }

    override fun onSetTransactionInfoSuccess() {
        Log.d("onSetTransactionInfoSuccess","✅ SET TRANSACTION INFO SUCCEEDED")
    }

    override fun onGetTransactionInfoSuccess(transactionInfo: String?) {
        Log.d("onGetTransactionInfoSuccess","✅ TRANSACTION INFO SUCCESS -> $transactionInfo")
    }

    override fun onDisplayTextOnScreenSuccess() {
        Log.d("onDisplayTextOnScreenSuccess","✅ DISPLAY TEXT ON SCREEN SUCCEEDED")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnDisplayTextOnScreenSuccess", true)
        }

    }

    override fun onReceiveErrorCode(error: Int, message: String?) {
        Log.d("onReceiveErrorCode","‼️ ERROR CODE -> code: $error; message: $message ")
        Handler(Looper.getMainLooper()).post {
            val data = hashMapOf<String, Any?>()
            data["code"] = error
            data["message"] = message
            channel.invokeMethod("OnReceiveErrorCode", data)
        }

    }

    override fun onSetAidSuccess() {
        Log.d("onSetAidSuccess","✅ SET AID SUCCEEDED")
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("OnSetAidSuccess", true)
        }
    }

}
