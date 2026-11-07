# CallCatcher

Xposed suite + integrated player for recording, organizing and playing phone and WhatsApp calls.
Total-black single-window Android app, root-assisted capture, Opus encoding.

## How it works

One shared capture engine (`AudioRecord` → MediaCodec Opus → Ogg) runs in the CallCatcher app
process. Triggers only say *when* a call starts and ends:

| Trigger | Where it runs | Signal |
|---|---|---|
| Phone calls | `PhoneCallRecordingService` (InCallService) in the app | Telecom call state |
| WhatsApp calls (Telecom) | same InCallService, if WhatsApp registers its calls with Telecom | Telecom call state |
| WhatsApp calls (hook) | `:capture-wa` inside WhatsApp, via LSPosed/Vector | `soundPortCreated` / `fieldstatsReady`, sent to the app's `TriggerProvider` |
| Test | 5 s microphone recording from the app header | button |

Capture needs `CAPTURE_AUDIO_OUTPUT` and call-state access needs `CONTROL_INCALL_EXPERIENCE`. Both
are **privileged permissions**: `pm grant` cannot grant them, which is why WAEnhancer (recording from
inside WhatsApp) falls back to the microphone. Like BCR, CallCatcher gets them by being installed as
a **privileged system app** through a KernelSU/Magisk module.

### Capture profiles

- **Phone:** `VOICE_CALL` → `VOICE_COMMUNICATION` → `MIC`. `VOICE_CALL` is confirmed on the target
  device (Redmi Note 12, BCR logs): both sides, 16 kHz mono. The network sets the quality ceiling,
  so Opus at 16 kHz / 32 kbps is transparent.
- **WhatsApp:** same cascade, with a 2 s start window. A source that delivers only digital silence
  in that window (e.g. `VOICE_CALL` during VoIP on many devices) is skipped automatically. Whether
  `VOICE_CALL` carries WhatsApp audio on this device is exactly what the first tests will show.

## Install (test builds)

CI (`.github/workflows/android.yml`) builds on every push and uploads one artifact with:

- `CallCatcher-<version>.apk`: the app, which is also the LSPosed module;
- `CallCatcher-module-<version>.zip`: KernelSU/Magisk module that installs that APK in
  `/system/priv-app` with its permission allowlist.

First install:

1. Uninstall any CallCatcher installed as a normal app with a different signing key.
2. Flash the module zip in KernelSU and reboot. (If the phone doesn't boot, KernelSU's safe mode,
   volume-down during boot, disables modules.)
3. Open CallCatcher, accept the notice and the permissions. The status panel should show all
   checks green.
4. In Vector, enable the CallCatcher module with scope **WhatsApp** and **System Framework**, then
   reboot. System Framework is needed so WhatsApp can reach the app (Android 11+ package visibility).

Later builds with the same permissions: install the new APK over the old one; no reflash needed.
If the permissions change, reflash the module.

### Signing

Updates only install if every APK has the same signature. CI signs with the key in the
`DEV_KEYSTORE_B64` repository secret (base64 of `ui/dev.keystore`, which is git-ignored). Without the
secret, CI warns and uses a throwaway key.

## Testing checklist

- Mic button (header): 5 s recording appears under All; tap to play, tap the waveform to seek.
- Phone call: the row's details line should read `VOICE_CALL · 16 kHz`.
- WhatsApp call: the status panel shows "WhatsApp trigger: last received …" if the hook reached
  the app; the details line shows which source won. Hook diagnostics are in Vector → Logs, lines
  starting with `CallCatcher:`.
- Long-press a row to delete it.

## Modules

| Module | Role |
|---|---|
| `:core` | Capture engine, storage, Room DB, Opus encoding, RecorderService, TriggerProvider. |
| `:capture-phone` | InCallService trigger and foreground host (BCR model). |
| `:capture-wa` | LSPosed hooks: WhatsApp call detection, package-visibility fix in system_server. WhatsApp class lookup is isolated in `WaClassFinder`. |
| `:player` | Media3/ExoPlayer playback wrapper. |
| `:ui` | Total-black app: one filterable list, status panel, player. |

## Not done yet

- Two-sided WhatsApp capture if `VOICE_CALL` turns out silent for VoIP here (uplink
  `VOICE_COMMUNICATION` + downlink playback capture, mixed).
- Settings screen (folder, naming template, format, whitelist/blacklist).
- Writing to a user-visible folder (recordings are in `Android/data/com.callcatcher/files/Recordings`).

## Legal

Recording your own conversations is generally lawful, but rules vary by country and by use. The app
shows a first-run notice to that effect and disclaims responsibility for how recordings are used.
