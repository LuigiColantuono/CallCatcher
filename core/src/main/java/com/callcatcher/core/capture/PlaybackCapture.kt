package com.callcatcher.core.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.atomic.AtomicBoolean

/** A live capture of other apps' playback. [close] unregisters the audio policy; always call it. */
class PlaybackCaptureHandle internal constructor(
    val record: AudioRecord,
    private val onClose: () -> Unit,
) {
    private val closed = AtomicBoolean(false)

    fun close() {
        if (closed.compareAndSet(false, true)) runCatching(onClose)
    }
}

/**
 * Captures audio *played* with USAGE_VOICE_COMMUNICATION, which is where the other party of a
 * WhatsApp call lives.
 *
 * This is the same mechanism Android's own playback capture (screen recording with audio) uses: a
 * dynamic AudioPolicy with a loop-back-and-render mix, so the user still hears the call while we
 * get a copy. Public apps can only do it through a MediaProjection consent dialog; a privileged app
 * holding MODIFY_AUDIO_ROUTING can register the policy directly, and CAPTURE_VOICE_COMMUNICATION_OUTPUT
 * is what allows voice-communication audio in it. The policy classes are @SystemApi, hence
 * reflection (with the hidden-API restriction lifted for this process).
 *
 * Whether WhatsApp's audio is capturable this way also depends on WhatsApp's own capture policy;
 * that is what the next device test answers.
 */
object PlaybackCapture {

    private const val TAG = "PlaybackCapture"

    /**
     * Android caps privileged (voice-communication) playback capture at 16 kHz, mono, 16-bit, and
     * rejects the mix otherwise ("Privileged audio capture sample rate … can not be over 16000").
     * WhatsApp's voice is wideband anyway, so nothing is lost; the uplink leg is opened at the same
     * rate so the two can be mixed sample for sample.
     */
    const val MAX_SAMPLE_RATE_HZ = 16_000

    private const val RULE_MATCH_ATTRIBUTE_USAGE = 0x1
    private const val ROUTE_FLAG_RENDER = 0x1
    private const val ROUTE_FLAG_LOOP_BACK = 0x2
    private const val MIX_ROLE_PLAYERS = 0

    private val exempted = AtomicBoolean(false)

    /** Opens the capture or throws an exception whose message says why the platform refused. */
    fun open(context: Context, sampleRateHz: Int): PlaybackCaptureHandle {
        exemptHiddenApis()
        val audioManager = context.getSystemService(AudioManager::class.java)

        val ruleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
        val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
        val mixClass = Class.forName("android.media.audiopolicy.AudioMix")
        val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
        val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
        val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")

        val voiceAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .build()

        val ruleBuilder = ruleBuilderClass.getConstructor().newInstance()
        runCatching {
            ruleBuilderClass.getMethod("setTargetMixRole", Int::class.javaPrimitiveType)
                .invoke(ruleBuilder, MIX_ROLE_PLAYERS)
        }
        ruleBuilderClass.getMethod("addMixRule", Int::class.javaPrimitiveType, Any::class.java)
            .invoke(ruleBuilder, RULE_MATCH_ATTRIBUTE_USAGE, voiceAttributes)
        // Opt in to capturing voice-communication audio (Android 11+; needs the matching permission).
        runCatching {
            ruleBuilderClass.getMethod("allowPrivilegedPlaybackCapture", Boolean::class.javaPrimitiveType)
                .invoke(ruleBuilder, true)
        }.onFailure { Log.w(TAG, "allowPrivilegedPlaybackCapture unavailable", it) }
        val rule = ruleBuilderClass.getMethod("build").invoke(ruleBuilder)

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val mixBuilder = mixBuilderClass.getConstructor(ruleClass).newInstance(rule)
        mixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, format)
        mixBuilderClass.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
            .invoke(mixBuilder, ROUTE_FLAG_LOOP_BACK or ROUTE_FLAG_RENDER)
        val mix = mixBuilderClass.getMethod("build").invoke(mixBuilder)

        val policyBuilder = policyBuilderClass.getConstructor(Context::class.java).newInstance(context)
        policyBuilderClass.getMethod("addMix", mixClass).invoke(policyBuilder, mix)
        val policy = policyBuilderClass.getMethod("build").invoke(policyBuilder)

        val status = AudioManager::class.java.getMethod("registerAudioPolicy", policyClass)
            .invoke(audioManager, policy) as Int
        check(status == 0) { "registerAudioPolicy returned $status (missing MODIFY_AUDIO_ROUTING?)" }

        val unregister: () -> Unit = {
            runCatching {
                AudioManager::class.java.getMethod("unregisterAudioPolicy", policyClass).invoke(audioManager, policy)
            }.onFailure {
                runCatching {
                    AudioManager::class.java.getMethod("unregisterAudioPolicyAsync", policyClass).invoke(audioManager, policy)
                }
            }
        }

        val record = try {
            policyClass.getMethod("createAudioRecordSink", mixClass).invoke(policy, mix) as AudioRecord
        } catch (t: Throwable) {
            unregister()
            throw t
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            unregister()
            error("Playback capture record did not initialise")
        }

        Log.i(TAG, "Playback capture of voice-communication audio opened @ $sampleRateHz Hz")
        return PlaybackCaptureHandle(record) {
            runCatching { record.stop() }
            record.release()
            unregister()
        }
    }

    private fun exemptHiddenApis() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || exempted.get()) return
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onSuccess { exempted.set(true) }
            .onFailure { Log.w(TAG, "Hidden API exemption failed; reflection may be blocked", it) }
    }
}
