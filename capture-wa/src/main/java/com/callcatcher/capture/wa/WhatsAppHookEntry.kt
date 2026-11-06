package com.callcatcher.capture.wa

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import com.callcatcher.core.CallCatcherCapture
import com.callcatcher.core.capture.AudioSource
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * WhatsApp trigger entry point. Loaded by LSPosed/Vector into the WhatsApp process.
 *
 * Structure mirrors the phone trigger: decide *when* a call is live, fill [CallMetadata], drive the
 * shared [AudioSource]. All the WhatsApp-specific fragility is confined to [WaSignatures]; this
 * class only reacts to the two events that seam exposes.
 *
 * NOTE: this runs inside WhatsApp's process, so the engine uses WhatsApp's context. Capture of the
 * far-end leg still depends on the per-device playback-capture test (see TwoLegCaptureStrategy);
 * until that passes, a single VOICE_COMMUNICATION source yields the same one-sided result WAE has.
 * That is a capture-path question, not a wiring one — the wiring here is final.
 */
class WhatsAppHookEntry : de.robv.android.xposed.IXposedHookLoadPackage {

    private var signatures: WaSignatures? = null
    private var engine: AudioSource? = null

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.whatsapp" && lpparam.packageName != "com.whatsapp.w4b") return

        // Resolve the obfuscated anchors once, when the host Application is ready.
        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as Context
                    onHostReady(context, lpparam)
                }
            },
        )
    }

    private fun onHostReady(context: Context, lpparam: XC_LoadPackage.LoadPackageParam) {
        val resolved = DexKitWaResolver(lpparam.classLoader, lpparam.appInfo.sourceDir).resolve()
        if (!resolved.isValid) {
            Log.w(TAG, "WhatsApp signatures unresolved on this build; recording inactive")
            return
        }
        signatures = resolved
        engine = CallCatcherCapture.engine(context)
        installHooks(resolved)
        Log.i(TAG, "WhatsApp call trigger installed")
    }

    private fun installHooks(sig: WaSignatures) {
        // Connected → start.
        XposedBridge.hookMethod(sig.soundPortCreatedMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val jid = sig.extractPeerJid(param.thisObject)
                engine?.onStreamStart(
                    CallMetadata(
                        source = CallSource.WHATSAPP,
                        direction = CallDirection.UNKNOWN, // WA does not expose this cleanly
                        address = jid,
                    )
                )
            }
        })

        // Field stats ready → ended.
        XposedBridge.hookMethod(sig.fieldStatsReadyMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                engine?.onStreamEnd()
            }
        })

        // Backstop: if the VoIP activity is destroyed, make sure we stopped.
        XposedHelpers.findAndHookMethod(
            sig.voipActivityClass, "onDestroy",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    engine?.onStreamEnd()
                }
            },
        )
    }

    companion object {
        private const val TAG = "WaTrigger"
    }
}
