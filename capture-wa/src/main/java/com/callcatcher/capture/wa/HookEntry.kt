package com.callcatcher.capture.wa

import com.callcatcher.core.CallCatcherIds
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed/Vector entry point (listed in assets/xposed_init). Two scopes:
 *  - WhatsApp: detect call start/end and signal the app ([WhatsAppCallHooks]).
 *  - System Framework ("android"): make the app visible to WhatsApp so that signal can be
 *    delivered at all ([PackageVisibilityHook]).
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when {
            lpparam.packageName == "android" -> PackageVisibilityHook.install(lpparam.classLoader)

            lpparam.packageName in CallCatcherIds.WHATSAPP_PACKAGES &&
                lpparam.processName == lpparam.packageName -> WhatsAppCallHooks.install(lpparam)
        }
    }
}
