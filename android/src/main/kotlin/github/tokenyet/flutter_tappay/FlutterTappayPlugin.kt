package github.tokenyet.flutter_tappay

import android.app.Activity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.BinaryMessenger
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.util.Log
import android.content.IntentFilter
import android.media.session.MediaSession
import android.os.Handler
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import tech.cherri.tpdirect.api.TPDCard
import tech.cherri.tpdirect.api.TPDCardInfo
import tech.cherri.tpdirect.api.TPDServerType
import tech.cherri.tpdirect.api.TPDSetup
import tech.cherri.tpdirect.callback.TPDTokenFailureCallback
import tech.cherri.tpdirect.callback.TPDTokenSuccessCallback
import java.util.*
import kotlin.collections.HashMap


/** FlutterTappayPlugin */
public class FlutterTappayPlugin : FlutterPlugin, StreamHandler, MethodCallHandler, ActivityResultListener, ActivityAware {
    private var methodChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null

    private var applicationContext: Context? = null
    private var activityPluginBinding: ActivityPluginBinding? = null // get activity

    private var eventSink: EventChannel.EventSink? = null
    private var token: String? = null // lastest token
    private var requestCode: Int = 8787

    // Direct way to use tappay
    //private var

    companion object {
        val instance = FlutterTappayPlugin()
        val METHOD_CHANNEL_NAME = "tokenyet.github.io/flutter_tappay"
        val EVENT_CHANNEL_NAME = "tokenyet.github.io/flutter_tappay_callback"
        val DEFUALT_TITLE = "Tappay Example Title"
        val DEFAULT_BTN_NAME = "Pay"
        val DEFAULT_PENDING_BTN_NAME = "Paying..."
        val SYSTEM_DEFAULT_APP_ID = 11334
        val SYSTEM_DEFAULT_APP_KEY = "app_whdEWBH8e8Lzy4N6BysVRRMILYORF6UxXbiOFsICkz0J9j1C0JUlCHv1tVJC"
        val SYSTEM_DEFAULT_SERVER_TYPE = "sandbox"
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(binding.applicationContext, binding.binaryMessenger)
    }

    private fun onAttachedToEngine(applicationContext: Context, messenger: BinaryMessenger) {
        this.applicationContext = applicationContext
        methodChannel = MethodChannel(messenger, METHOD_CHANNEL_NAME)
        methodChannel?.setMethodCallHandler(this)
        eventChannel = EventChannel(messenger, EVENT_CHANNEL_NAME)
        eventChannel?.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = null
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        eventChannel = null
    }

    private var _appkey = null;
    private var _appId = null;
    private var _serverType = null;

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}") // Preserve, It might need to check versio in the future?
        } else if (call.method == "showPayment") {
            var intent = Intent(applicationContext, TappayActivity::class.java)
            var title = call.argument<String>("title")
            var btnName = call.argument<String>("btnName")
            var pendingBtnName = call.argument<String>("pendingBtnName")
            var appKey = call.argument<String>("appKey")
            var appId = call.argument<String>("appId")
            var serverType = call.argument<String>("serverType")
            var reqCode = call.argument<String>("androidRequestCode")
            intent.putExtra(TappayActivity.INTENT_TITLE, if (title != null) title else DEFUALT_TITLE)
            intent.putExtra(TappayActivity.INTENT_BTN_NAME, if (btnName != null) btnName else DEFAULT_BTN_NAME)
            intent.putExtra(TappayActivity.INTENT_PENDING_BTN_NAME, if (pendingBtnName != null) pendingBtnName else DEFAULT_PENDING_BTN_NAME)
            intent.putExtra(TappayActivity.INTENT_APP_ID, if (appId != null) appId.toInt() else SYSTEM_DEFAULT_APP_ID)
            intent.putExtra(TappayActivity.INTENT_APP_KEY, if (appId != null) appKey else SYSTEM_DEFAULT_APP_KEY)
            intent.putExtra(TappayActivity.INTENT_SERVER_TYPE, if (appId != null) serverType else SYSTEM_DEFAULT_SERVER_TYPE)

            if(reqCode != null)
                requestCode = reqCode.toInt()

            if(activityPluginBinding == null)
                return result.error("UNAVAILABLE", "activityPluginBinding not available.", null)
            if(activityPluginBinding?.activity == null)
                return result.error("UNAVAILABLE", "activityPluginBinding.activity not available.", null)

            activityPluginBinding!!.activity!!.startActivityForResult(intent, requestCode)
            result.success("SUCCESS")
        } else if (call.method == "getToken") {
            if (this.token != null)
                result.success(token)
            else
                result.error("No token hooked", null, null)
        } else if(call.method == "init"){ // another way to use directpay
            var appKey = call.argument<String>("appKey")
            var appId = call.argument<String>("appId")
            var serverType = call.argument<String>("serverType")

            try {
                TPDSetup.initInstance(
                    this.applicationContext,
                    appId!!.toInt(),
                    appKey,
                    if (serverType!!.toLowerCase() == "sandbox") TPDServerType.Sandbox else TPDServerType.Production
                )
                return result.success("SUCCESS")
            } catch (err: Exception) {
                return result.error("Tappay", "error", err)
            }

        } else if(call.method == "validate") { // return object// boolean
            var cardNumber = call.argument<String>("cardNumber")
            var dueMonth = call.argument<String>("dueMonth")
            var dueYear = call.argument<String>("dueYear")
            var ccv = call.argument<String>("ccv")
            var validResult = TPDCard.validate(
                StringBuffer().append(cardNumber),
                StringBuffer().append(dueMonth),
                StringBuffer().append(dueYear),
                StringBuffer().append(ccv)
            )

            var map = HashMap<String, String>()
            map.put("isCardNumberValid", if(validResult.isCardNumberValid) "1" else "0")
            map.put("isExpiryDateValid", if(validResult.isExpiryDateValid) "1" else "0")
            map.put("isCCVValid", if(validResult.isCCVValid) "1" else "0")
            map.put("cardType", validResult.cardType.name)
            return result.success(map);
//            var card = TPDCard(
//                this.applicationContext,
//                "cardNumber",
//                "dueMonth",
//                "dueYear",
//                "CCV"
//            );
        } else if(call.method == "sendToken"){ // return a Map with 3 value
            var cardNumber = call.argument<String>("cardNumber")
            var dueMonth = call.argument<String>("dueMonth")
            var dueYear = call.argument<String>("dueYear")
            var ccv = call.argument<String>("ccv")
            var card = TPDCard(this.applicationContext,
                StringBuffer().append(cardNumber),
                StringBuffer().append(dueMonth),
                StringBuffer().append(dueYear),
                StringBuffer().append(ccv)
            )
                .onSuccessCallback { prime: String, cardInfo: TPDCardInfo, cardIdentifier: String -> run {
                        var cardInfoMap = HashMap<String, String>()
                        cardInfoMap.put("bincode", cardInfo.bincode)
                        cardInfoMap.put("lastFour", cardInfo.lastFour)
                        cardInfoMap.put("issuer", cardInfo.issuer)
                        cardInfoMap.put("funding", cardInfo.funding.toString())
                        cardInfoMap.put("cardType", cardInfo.cardType.toString())
                        cardInfoMap.put("level", cardInfo.level)
                        cardInfoMap.put("country", cardInfo.country)
                        cardInfoMap.put("countryCode", cardInfo.countryCode)
                        var pack = HashMap<String, Any>()
                        pack.put("prime", prime)
                        pack.put("cardInfoMap", cardInfoMap)
                        pack.put("cardIdentifier", cardIdentifier)
                        result.success(pack)
                    }
                }
                .onFailureCallback { status: Int, reportMsg: String -> run {
                        result.error("$status", "$reportMsg", null)
                    }
                };
            card.createToken("UNKNOWN");
        } else {
            result.notImplemented()
        }
    }


    override fun onListen(arguments: Any?, events: EventSink?) {
      eventSink = events;
    }

    override fun onCancel(arguments: Any?) {
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if(requestCode == this.requestCode) {
            if(data?.hasExtra("token") == true) {
                eventSink?.success(data?.getStringExtra("token"))
            } else if(data?.hasExtra("error") == true){
                eventSink?.error(data?.getStringExtra("error"), null, null);
            } else {
                eventSink?.error("Unexpected Error", null, null);
            }
            return true
        }
        return false
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityPluginBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activityPluginBinding = null
    }
}
