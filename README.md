# <img width="auto" height="50" alt="CCLogo2" src="https://github.com/user-attachments/assets/e010a012-d54e-4b16-98d6-ea8e0cf15e8c" /> CallCatcher

Xposed module/apk for recording, organizing and playing phone and WhatsApp calls.

## What motivated me to create CC:
There are already excellent tools like BCR and WAE, but having to configure a folder for saving files, dealing with a file manager, or running into SAF (Storage Access Framework) restrictions when trying to save files to other apps can become quite annoying.
So, I created a single app that:
- Requires no configuration (other than installing the module and APK, and granting the necessary permissions via Vector/LSPosed)
- Automatically categorizes recordings by type and color
- Includes a built-in player
- Has no notification system (it’s unnecessary)

## Preview
Note: The display of "Whatsapp Contact" is intentional and serves only as a preview; the CC field displays the name correctly.
<br>
<img width="auto" height="400" alt="image" src="https://github.com/user-attachments/assets/2e552d0a-ec80-49c9-9cc1-5fece4496366" />


## How it works

One shared capture engine (`AudioRecord` → MediaCodec Opus → Ogg) runs in the CallCatcher app
process. Triggers only say *when* a call starts and ends:

| Trigger | Where it runs | Signal |
|---|---|---|
| Phone calls | `PhoneCallRecordingService` (InCallService) in the app | Telecom call state |
| WhatsApp calls (Telecom) | same InCallService, if WhatsApp registers its calls with Telecom | Telecom call state |
| WhatsApp calls (hook) | `:capture-wa` inside WhatsApp, via LSPosed/Vector | `soundPortCreated` / `fieldstatsReady`, sent to the app's `TriggerProvider` |
| Recording | microphone recording (voice memo) from the bottom dock, until Stop | button |

Capture needs `CAPTURE_AUDIO_OUTPUT` and call-state access needs `CONTROL_INCALL_EXPERIENCE`. Both
are **privileged permissions**: `pm grant` cannot grant them, which is why WAEnhancer (recording from
inside WhatsApp) falls back to the microphone. Like BCR, CallCatcher gets them by being installed as
a **privileged system app** through a KernelSU/Magisk module.

### Capture profiles

- **Phone:** `VOICE_CALL` → `VOICE_COMMUNICATION` → `MIC`. `VOICE_CALL` is confirmed on the target
  device (Redmi Note 12, BCR logs): both sides, 16 kHz mono. The network sets the quality ceiling,
  so Opus at 16 kHz / 32 kbps is transparent.
- **WhatsApp:** first test (MIC only, 48 kHz) showed why WAE sounds bad: the other party is not in
  any microphone source, and `VOICE_COMMUNICATION` is silenced for other apps while WhatsApp uses
  it. So the VoIP profile is:
  1. `VOICE_CALL`, skipped automatically if digitally silent during the 1.5 s start window;
  2. **two-leg**: your side from `MIC`, the other side from playback capture of
     voice-communication audio (`PlaybackCapture`: loop-back audio policy, needs
     `MODIFY_AUDIO_ROUTING` + `CAPTURE_VOICE_COMMUNICATION_OUTPUT`), each auto-levelled, mixed to mono;
  3. `MIC` alone as the last resort.

  The details line of a recording shows which one won (`MIC+PLAYBACK` is the target), and
  "Last saved" in the setup card lists why earlier ones were skipped.

Test with BCR and WAEnhancer's call recording **disabled**: they open the same audio sources at the
same time.

## Install (test builds)

CI (`.github/workflows/android.yml`) builds on every push and uploads one artifact with:

- `CallCatcher-<version>.apk`: the app, which is also the LSPosed module;
- `CallCatcher-module-<version>.zip`: KernelSU/Magisk module that installs that APK in
  `/system/priv-app` with its permission allowlist.

Requirement: the root solution must be able to mount module files into `/system`.

- **Magisk**: built in, nothing to add.
- **KernelSU**: recent versions no longer mount module files themselves; install a **metamodule**
  first (as for BCR). Magic Mount-rs is confirmed working on the reference device; others may work
  depending on the device. After reboot, the metamodule should list CallCatcher as mounted.

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

- "Recording" (bottom dock), then "Stop": the recording appears under Today; tap it to open the
  player deck (play, speed, share, delete, close), tap or drag the waveform to seek.
- Phone call: the deck's details line should read `VOICE_CALL`.
- WhatsApp call: the setup card (status pill, top right) shows "WhatsApp trigger: last …" if the
  hook reached the app; the deck shows which strategy won. Hook diagnostics are in Vector → Logs,
  lines starting with `CallCatcher:`.
- Share: share button in the deck opens the system share sheet (e.g. send to a WhatsApp chat).
- Delete: trash button in the deck, tap twice.

## Modules

| Module | Role |
|---|---|
| `:core` | Capture engine, storage, Room DB, Opus encoding, RecorderService, TriggerProvider. |
| `:capture-phone` | InCallService trigger and foreground host (BCR model). |
| `:capture-wa` | LSPosed hooks: WhatsApp call detection, package-visibility fix in system_server. WhatsApp class lookup is isolated in `WaClassFinder`. |
| `:player` | Media3/ExoPlayer playback wrapper. |
| `:ui` | Total-black app: one filterable list, status panel, player. |

## License

CallCatcher is free software, licensed under the [GNU General Public License v3.0](LICENSE).
You can redistribute and modify it under those terms; it comes with no warranty.

## Legal

Recording your own conversations is generally lawful, but rules vary by country and by use. The app
shows a first-run notice to that effect and disclaims responsibility for how recordings are used.
