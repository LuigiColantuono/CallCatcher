package com.callcatcher.capture.wa

import android.content.Context
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

/**
 * THE FRAGILE PART, isolated. Everything that depends on WhatsApp's internal class names lives
 * here; when an update breaks detection, this is the only file to change.
 *
 * Strategy (same as WAEnhancer's CallRecording): the voice-service event callback is called from
 * native code, so its simple name survives obfuscation; only its package may move. Lookup order:
 *  1. class name cached for this WhatsApp version,
 *  2. known fully-qualified names,
 *  3. DexKit scan for a class whose name ends with [CALLBACK_SIMPLE_NAME] (slow, ~seconds; cached).
 */
internal object WaClassFinder {

    private const val CALLBACK_SIMPLE_NAME = "VoiceServiceEventCallback"

    private val KNOWN_NAMES = listOf(
        "com.whatsapp.voipcalling.VoiceServiceEventCallback",
        "com.whatsapp.calling.voipcalling.VoiceServiceEventCallback",
    )

    private const val CACHE_PREFS = "callcatcher_class_cache"

    fun voiceServiceEventCallback(context: Context, classLoader: ClassLoader, apkPath: String): Class<*>? {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val cacheKey = "callback@" + versionCodeOf(context)

        prefs.getString(cacheKey, null)?.let { cached ->
            load(classLoader, cached)?.let { return it }
        }

        val found = KNOWN_NAMES.firstNotNullOfOrNull { load(classLoader, it) }
            ?: findWithDexKit(classLoader, apkPath)

        if (found != null) {
            prefs.edit().putString(cacheKey, found.name).apply()
            XposedBridge.log("CallCatcher: WhatsApp call callback = ${found.name}")
        }
        return found
    }

    private fun findWithDexKit(classLoader: ClassLoader, apkPath: String): Class<*>? = try {
        System.loadLibrary("dexkit")
        DexKitBridge.create(apkPath).use { bridge ->
            bridge.findClass {
                matcher { className(CALLBACK_SIMPLE_NAME, StringMatchType.EndsWith) }
            }.firstOrNull()?.getInstance(classLoader)
        }
    } catch (t: Throwable) {
        XposedBridge.log("CallCatcher: DexKit lookup failed: $t")
        null
    }

    private fun load(classLoader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    private fun versionCodeOf(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)
}
