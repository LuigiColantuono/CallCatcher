# CallCatcher

Xposed suite + integrated player for recording, organizing and playing phone and WhatsApp calls.
Total-black single-window Android app, root-assisted capture, Opus encoding.

## Why one engine

Analysis of WAEnhancer's `CallRecording` showed WhatsApp needs no WebRTC interception: WAE just
drives a recorder from a system audio source and acts as a *trigger* that knows when a call is
live. So the capture problem is shared — only *who gives the go signal* differs. CallCatcher makes
that explicit with a single [`AudioSource`](core/src/main/java/com/callcatcher/core/capture/AudioSource.kt)
seam between triggers and one shared engine.

## Pre-flight result (target device: Redmi Note 12 / HyperOS, root)

From BCR logs on the device:

- **`VOICE_CALL` (source 4) works for carrier calls** — opens, no fallback, ~19 s silence over an
  8-min call (natural pauses, not a dead stream). Encoded fine to Opus/Ogg.
- **The ceiling is the network, not the encoder.** Carrier audio arrives at **16 kHz mono**, so
  forcing 48 kHz or fat AAC buys nothing. Default is therefore Opus at the source's native rate,
  32 kbps — transparent for voice, small files.
- **WhatsApp is a different capture path.** Its audio never reaches the modem, so `VOICE_CALL`
  returns silence and WAE falls back to MIC — hence the one-sided "you loud, them faint" result the
  user confirmed. Proper two-sided WA audio needs the two-leg strategy (uplink via
  `VOICE_COMMUNICATION` + downlink via playback capture), gated on a per-device test. See
  [`CaptureSource`](core/src/main/java/com/callcatcher/core/capture/CaptureSource.kt).

The capture *wiring* is final regardless; only the WA capture *source* is still to be validated
on-device.

## Modules

| Module | Role |
|---|---|
| `:core` | Capture engine, storage, Room DB, Opus encoding. Source-agnostic. |
| `:capture-phone` | Phone trigger — `InCallService`, BCR-style call-state observation. |
| `:capture-wa` | WhatsApp trigger — LSPosed/Vector module. Fragile deobfuscation isolated behind `WaSignatures`. |
| `:player` | Media3/ExoPlayer playback wrapper. |
| `:ui` | Total-black standalone app: one filterable list + player + (planned) settings. |

## Status

Compilable architecture skeleton. Real, working code for: the capture engine
(`AudioRecord` + `MediaCodec` Opus → Ogg with source cascade, delayed start, silence/waveform
analysis), storage + naming + Room + startup reconcile, phone trigger, player, and the UI
(single list, source/direction glyphs, waveform, mini-player, first-run legal notice).

Deliberately left as marked seams, because they are device-test tasks rather than things to guess:

- `DexKitWaResolver.runDexKitQueries()` — the WhatsApp signature queries. Fails safe (recording
  off) until tuned against a real build.
- `TwoLegCaptureStrategy` — two-sided WhatsApp capture.
- Routing output to a user-visible folder via a privileged (root) bridge; default writes to the
  app's own external storage.

## Building

CI (`.github/workflows/android.yml`) builds the debug APK on every push and PR:

- The APK is uploaded as a workflow **artifact** (`CallCatcher-debug-apk`) — download it from the
  run's summary page.
- Pushing a **tag** `vX.Y.Z` additionally creates a GitHub **Release** with the APK attached.

The single `:ui` debug APK is both the standalone app **and** the LSPosed module: install it, open
it once, then activate it in LSPosed/Vector and scope it to WhatsApp.

The Gradle wrapper jar is not committed; the workflow generates it (`gradle wrapper`) before
building. To build locally, run `gradle wrapper --gradle-version 8.11.1` once (needs a system
Gradle), or let Android Studio generate it on first sync, then `./gradlew :ui:assembleDebug`.

## Legal

Recording your own conversations is generally lawful, but rules vary by country and by use. The app
shows a first-run notice to that effect and disclaims responsibility for how recordings are used.
