package com.callcatcher.capture.wa

import com.callcatcher.core.CallCatcherIds
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Runs in system_server. Since Android 11, an app can only reach another app's components if it
 * can "see" that package; WhatsApp has no reason to see CallCatcher, so its contentResolver.call()
 * to our TriggerProvider would fail with "Unknown authority".
 *
 * Android already has a switch for "visible to every app": the force-queryable override that
 * `pm install --force-queryable` sets. This hook reports it as set for CallCatcher only. It touches
 * one getter that is read when the visibility cache is built, so the runtime cost is negligible.
 *
 * Only active if the user enables "System Framework" in the module's scope.
 */
internal object PackageVisibilityHook {

    fun install(classLoader: ClassLoader) {
        try {
            val packageSetting = XposedHelpers.findClass("com.android.server.pm.PackageSetting", classLoader)
            val hooks = XposedBridge.hookAllMethods(
                packageSetting,
                "isForceQueryableOverride",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val pkg = runCatching {
                            XposedHelpers.callMethod(param.thisObject, "getPackageName") as? String
                        }.getOrNull()
                        if (pkg == CallCatcherIds.PACKAGE) param.result = true
                    }
                },
            )
            XposedBridge.log("CallCatcher: package visibility hook installed (${hooks.size} method(s))")
        } catch (t: Throwable) {
            XposedBridge.log("CallCatcher: package visibility hook failed: $t")
        }
    }
}
