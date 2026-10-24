# Oscilla

A personal Android music visualizer for the Samsung Galaxy Z Fold 8. The show defaults to `Visualizer` session 0, which is the source that delivered a moving signal from YouTube Music without an extra share step. Playback capture and the microphone are in the settings sheet. The audio probe is still there as a debug view.

Untagged builds are `versionName` `0.2.0` and `versionCode` `2000`. After this slice is merged, the next release tag is `v0.2.0` (same versionCode). Do not push that tag until the pull request is merged. `v0.1.0` is the probe release already on GitHub.

## Using the show

The screen is fullscreen. Tap to show or hide the controls. The screen stays on while the visualizer is open. Controls, from left to right:

- **Prev / Next** change the visual. The current visual's parameters stay.
- **Shuffle** randomizes palette, sensitivity, symmetry, and speed for the current visual, with a short crossfade.
- **Auto** turns auto mode on or off. Auto reshuffles on the interval chosen in settings, and can also switch to another visual. The change crossfades.
- **Settings** opens the sheet: audio source (Visualizer, Playback, Microphone), auto interval in seconds or beats, whether auto switches visuals, and **Open audio probe**.

Playback still asks for the system capture consent and runs in a `mediaProjection` foreground service. The chosen source, visual, parameters, and auto settings are remembered. Fold and unfold keeps that state; the layout is one full-screen canvas so the cover screen and the inner screen both just resize. Dedicated fold layouts come later.

The five visuals:

- **Radial** — spectrum bars around a core that grows with the bass. Symmetry repeats the ring.
- **Lissajous** — the waveform drawn as an oscilloscope figure, x against a delayed copy of itself. Symmetry rotates extra copies.
- **Burst** — particles leave the center when a bass hit is detected.
- **Terrain** — recent spectra scroll forward as filled ribbons. Symmetry of 2 or more mirrors them.
- **Kaleidoscope** — short arcs repeated around the circle, with a slow plasma drift.

The audio probe offers three sources:

- **Playback** — `AudioPlaybackCapture` for `USAGE_MEDIA`, `USAGE_GAME`, and `USAGE_UNKNOWN`, after the system MediaProjection consent dialog. Capture runs in a `mediaProjection` foreground service. Consent is requested before that service starts. If the system revokes the token, capture stops.
- **Visualizer** — `android.media.audiofx.Visualizer` on session 0 (the output mix).
- **Microphone** — `AudioRecord` from the mic, so there is a known-good source to compare against.

The level meter is the frame RMS. The waveform and FFT bars are scaled to the current frame so a quiet shape is still visible; the numbers next to them are the raw values. **SILENT** means the source delivered only zeros for 3 seconds. **NO FRAMES** means it delivered nothing for 3 seconds. Apps that set `android:allowAudioPlaybackCapture="false"` will look silent to playback capture.

## Build and install

You need JDK 21 (17 or newer is enough) and an Android SDK with platform `android-37.2` and build-tools `37.0.0`. Point the SDK at the project with `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`. Do not commit `local.properties`.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Builds without a `v*` tag use `versionName` `0.2.0` and `versionCode` `2000`. A release built from tag `v0.2.0` uses the same numbers and the upload keystore. A debug APK is signed with the debug key, so Android will not upgrade the `v0.1.0` release in place. Uninstall first (`adb uninstall com.dannyowelch.oscilla`) or install the signed release.

Package id: `com.dannyowelch.oscilla`. `minSdk` is 29. `targetSdk` is 37. The activity sets `resizeableActivity` to true.

Visualizer and the microphone ask for `RECORD_AUDIO` when the show starts. Playback asks for that, then notifications, then the system capture dialog, and you pick the app to share. `MODIFY_AUDIO_SETTINGS` is a normal permission declared in the manifest. If you deny the microphone permission permanently, Settings offers Open app settings. The probe's own Start button still works after you open it from the sheet.

## versionCode

Every release tag must look like `vMAJOR.MINOR.PATCH` (for example `v0.1.0`). The release workflow passes that tag to Gradle. `versionName` is the tag without the leading `v`. `versionCode` is:

```text
MAJOR * 1000000 + MINOR * 1000 + PATCH
```

Minor and patch must be 0 through 999, and `versionCode` must be at least 1, so `v0.0.0` is rejected. A higher tag then always produces a higher `versionCode`:

| Tag | versionName | versionCode |
| --- | --- | --- |
| `v0.1.0` | 0.1.0 | 1000 |
| `v0.1.1` | 0.1.1 | 1001 |
| `v0.2.0` | 0.2.0 | 2000 |
| `v1.0.0` | 1.0.0 | 1000000 |

Android refuses to install an update whose `versionCode` is less than or equal to the installed one. Do not reuse a tag, and do not publish a lower tag after a higher one. If a release APK is already installed and you need to ship a fix, bump the tag.

## Signing

Generate the keystore once, on your machine. `keytool` prompts for the password so it is not stored in the command. Use the same password for the keystore and the key: this command writes a PKCS12 file, and PKCS12 does not keep a separate key password. Keep the file somewhere safe and backed up. If you lose it, Obtainium cannot update the installed app; you would have to uninstall first.

```bash
keytool -genkeypair -v \
  -keystore oscilla-release.keystore \
  -storetype PKCS12 \
  -alias oscilla \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Oscilla, O=dannyowelch"
```

Do not commit `oscilla-release.keystore` (or any `*.keystore` / `*.jks`). Encode it for the GitHub secret:

```bash
# Linux
base64 -w 0 oscilla-release.keystore

# macOS
base64 -i oscilla-release.keystore | tr -d '\n'
```

In the GitHub repo, open Settings → Secrets and variables → Actions and add these repository secrets:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | The base64 text from the command above, as one line or wrapped. |
| `SIGNING_KEYSTORE_PASSWORD` | The password you typed for `keytool`. |
| `SIGNING_KEY_ALIAS` | `oscilla`, unless you picked a different `-alias`. |
| `SIGNING_KEY_PASSWORD` | The same password as `SIGNING_KEYSTORE_PASSWORD`. |

The release workflow decodes the base64 secret into a temp file and points Gradle at it with `SIGNING_KEYSTORE_PATH`. That path is not a repository secret. No keystore or password is stored in git.

`./gradlew assembleRelease` on a machine without those four environment variables fails on purpose, so an unsigned release APK is not treated as shippable. `SIGNING_KEYSTORE_PATH` is the decoded file. The other three names match the secrets.

## Cut a release

The release workflow does not run on pull requests. It runs when a `v*` tag is pushed, and it fails until the four secrets exist.

`v0.1.0` is already released. The next tag, after the visualizer slice is merged, is `v0.2.0`. Do not push it before that merge.

```bash
git checkout main
git pull origin main
git tag v0.2.0
git push origin v0.2.0
```

GitHub Actions builds a signed universal APK and attaches `oscilla-0.2.0.apk` to that release. `versionCode` 2000 is higher than `v0.1.0`'s 1000, so Obtainium can update an install signed with the same keystore.

```bash
adb install -r oscilla-0.2.0.apk
```

## Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add App. Source: GitHub. URL: `https://github.com/dannyowelch/oscilla`.
3. Leave "include prereleases" off. If more than one APK is ever attached, set the APK filter to `oscilla-.*\.apk`.
4. Install. Obtainium downloads the APK asset from the latest GitHub Release.

Later tags install as updates only while they are signed with this same keystore and their `versionCode` is higher.

## CI

`.github/workflows/ci.yml` runs `./gradlew assembleDebug testDebugUnitTest` on pull requests and on branch pushes. `.github/workflows/release.yml` is the tag workflow above.
