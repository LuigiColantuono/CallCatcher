package com.callcatcher.capture.wa

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.callcatcher.core.CallCatcherIds
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs inside WhatsApp. Detects when a call connects and ends, and tells the CallCatcher app.
 * It never touches audio: WhatsApp's process can't read the call stream, the app can.
 *
 * Signals (as in WAEnhancer):
 *  - soundPortCreated → audio path is up, the call is connected → "start"
 *  - fieldstatsReady  → end-of-call stats are ready, the call is over → "stop"
 * A missed "stop" is covered app-side by an audio-mode watchdog in RecorderService.
 *
 * All diagnostics go to XposedBridge.log, i.e. the Logs tab of Vector/LSPosed.
 */
internal object WhatsAppCallHooks {

    private val installed = AtomicBoolean(false)

    /** One thread: IPC never runs on WhatsApp's call threads, and start/stop stay ordered. */
    private val ipc = Executors.newSingleThreadExecutor { r -> Thread(r, "CallCatcher-IPC").apply { isDaemon = true } }

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!installed.compareAndSet(false, true)) return
                    val context = param.args[0] as Context
                    // Class lookup can take seconds the first time (DexKit); keep it off WhatsApp's startup path.
                    ipc.execute { installCallHooks(context, lpparam) }
                }
            },
        )
    }

    private fun installCallHooks(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        val callback = WaClassFinder.voiceServiceEventCallback(context, lpparam.classLoader, lpparam.appInfo.sourceDir)
        if (callback == null) {
            XposedBridge.log("CallCatcher: WhatsApp call callback class not found; WhatsApp calls won't be detected")
            return
        }

        val started = XposedBridge.hookAllMethods(callback, "soundPortCreated", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val address = peerAddress(param.thisObject)
                signal(context, CallCatcherIds.METHOD_START, CallMetadata(source = CallSource.WHATSAPP, address = address).toBundle())
            }
        })
        val ended = XposedBridge.hookAllMethods(callback, "fieldstatsReady", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                signal(context, CallCatcherIds.METHOD_STOP, null)
            }
        })
        XposedBridge.log("CallCatcher: WhatsApp hooks installed (start=${started.size}, end=${ended.size})")
    }

    private fun signal(context: Context, method: String, extras: Bundle?) {
        ipc.execute {
            try {
                val result = context.contentResolver.call(
                    Uri.parse("content://${CallCatcherIds.TRIGGER_AUTHORITY}"), method, null, extras,
                )
                XposedBridge.log("CallCatcher: $method -> ${result?.getString(CallCatcherIds.RESULT_KEY)}")
            } catch (t: Throwable) {
                XposedBridge.log(
                    "CallCatcher: $method failed: $t. If this says 'Unknown authority', enable " +
                        "'System Framework' in the CallCatcher module scope and reboot."
                )
            }
        }
    }

    /**
     * Remote party from the callback's call info: getCallInfo().getPeerJid(), else the first key
     * of its participants map (as WAEnhancer does). Phone-number JIDs become "+<digits>" so the app
     * can match a contact; other identifiers (e.g. @lid) are passed through as-is.
     */
    private fun peerAddress(callback: Any?): String? = runCatching {
        val info = XposedHelpers.callMethod(callback, "getCallInfo") ?: return null
        val jid = runCatching { XposedHelpers.callMethod(info, "getPeerJid") }.getOrNull()
            ?: (XposedHelpers.getObjectField(info, "participants") as? Map<*, *>)?.keys?.firstOrNull { it != null }
            ?: return null
        val raw = runCatching { XposedHelpers.callMethod(jid, "getRawString") as? String }.getOrNull() ?: jid.toString()
        val user = raw.substringBefore('@')
        val server = raw.substringAfter('@', "")
        if (server == "s.whatsapp.net" && user.isNotEmpty() && user.all(Char::isDigit)) "+$user" else raw
    }.getOrNull()
}
