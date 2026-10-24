# Oscilla

A personal Android music visualizer for the Samsung Galaxy Z Fold 8. This slice is the project skeleton, the release pipeline, and an audio-source probe. The probe is there to learn, on the real device, which audio sources actually deliver samples. Fold-specific layouts come later.

The probe offers three sources:

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

Debug builds use `versionName` `dev` and `versionCode` 1. They are signed with the debug key. A later release build is signed with a different key, so Android will not upgrade a debug install in place. Uninstall the debug app first (`adb uninstall com.dannyowelch.oscilla`) before installing a release APK.

Package id: `com.dannyowelch.oscilla`. `minSdk` is 29. `targetSdk` is 37. The activity sets `resizeableActivity` to true.

On the phone, pick a source and tap Start. Playback asks for the microphone permission (AudioPlaybackCapture requires `RECORD_AUDIO`), notifications, and the system capture dialog, in that order. Then play music in another app. Visualizer and the microphone also ask for `RECORD_AUDIO`. `MODIFY_AUDIO_SETTINGS` is a normal permission declared in the manifest. If you deny the microphone permission permanently, use Open settings.

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

After this branch is merged to `main`:

```bash
git checkout main
git pull origin main
git tag v0.1.0
git push origin v0.1.0
```

GitHub Actions builds a signed universal APK (one ABI-universal file, no splits) and attaches `oscilla-0.1.0.apk` to the GitHub Release for that tag. Confirm the Release page has that asset before installing it.

```bash
adb install -r oscilla-0.1.0.apk
```

## Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add App. Source: GitHub. URL: `https://github.com/dannyowelch/oscilla`.
3. Leave "include prereleases" off. If more than one APK is ever attached, set the APK filter to `oscilla-.*\.apk`.
4. Install. Obtainium downloads the APK asset from the latest GitHub Release.

Later tags install as updates only while they are signed with this same keystore and their `versionCode` is higher.

## CI

`.github/workflows/ci.yml` runs `./gradlew assembleDebug testDebugUnitTest` on pull requests and on branch pushes. `.github/workflows/release.yml` is the tag workflow above.
