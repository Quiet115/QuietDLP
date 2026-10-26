# QuietDLP

Open-source video, audio and photo downloader for Android. Uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) for video and audio and [gallery-dl](https://github.com/mikf/gallery-dl) for photos and galleries.

Early version (0.2). Free and open source (GPL-3.0).

## What it does

- **Share and done.** Tap *Share* on a video and pick QuietDLP. It downloads in the background without opening the app.
- **No watermarks**, when the site offers a clean version.
- **Photos and galleries** when a link has no video.
- **Quality of your choice:** best, 1080p, 1440p, 2160p…, or audio only (M4A or MP3).
- **Its own album:** videos and photos go to `DCIM/QuietDLP`; audio goes to `Music/QuietDLP`. The folder name and location can be changed.
- **Default download** for Share and copied links: best video, 1080p, 720p, music (M4A or MP3), or ask each time. Rules per website (for example, music sites always audio).
- **Simple and Advanced mode**, and color palettes: dark, colorful, light, wallpaper colors, or your own.
- **History** with share and delete, a warning when a link was already downloaded, and automatic retry when the connection drops.
- **Automatic updates** for yt-dlp, gallery-dl and the app itself. On by default, can be turned off in Settings.
- **Sessions** for content that requires signing in, or by importing a `cookies.txt`.
- No ads, no accounts of its own, no telemetry.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 34).

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties   # not committed

./gradlew assembleDebug      # test build
./gradlew assembleRelease    # for distribution (signed if keystore.properties exists)
./gradlew testDebugUnitTest
```

APKs end up in `app/build/outputs/apk/`. Install by copying one to the phone, or with `adb install -r file.apk`.

## Publish a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew assembleRelease`.
3. On GitHub, create a *Release* tagged `vX.Y.Z` and attach the APK. The release text is the changelog users see, so write only what's new in that version.
4. Set `GITHUB_REPO = "user/repository"` in `Links.kt` (once). Installed apps will notify users.

**The signing key matters most.** Android only updates an app when the new version is signed with the same key. Keep `quietdlp-release.jks` and its password backed up. It is never committed (`keystore.properties` and `*.jks` are git-ignored).

To test the updater without publishing anything: `tools/dev_update.py` serves a version from your PC and `adb reverse tcp:8765 tcp:8765` brings it to the phone. Debug builds only.

## Parts

| | |
|---|---|
| `ShareReceiverActivity` | Invisible target of *Share*. |
| `DownloadService` | Foreground download service with a notification. |
| `Engine` | Prepares and updates Python, yt-dlp and ffmpeg. |
| `GalleryDl` | Photos and galleries, run as a separate program. |
| `MediaSaver` | Saves to the gallery or to Music. |
| `CookieStore`, `LoginActivity` | The user's sessions, in the app's private folder. |
| `AppUpdater` | Finds, downloads, verifies and installs new versions. |
| `MainActivity`, `MainScreen` | UI (Jetpack Compose). |

Built on [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp, Python 3.12, QuickJS and ffmpeg for Android).

## Limits

- arm64 only.
- Private or restricted content may require a session, and may still be unavailable.
- Up to 100 photos per link.
- When a site changes, yt-dlp and gallery-dl take a while to catch up. Updating usually fixes it.
- App stores tend to reject this kind of app. It is distributed through GitHub.

## Privacy

- Sessions live only in the app's private folder and are sent only to their own website.
- You type your password on the site itself; QuietDLP never sees it and doesn't let the phone save it.
- The app's only own connections are to GitHub and PyPI, for updates. No personal data is sent.
- Copied links are only checked locally, and only a hash of the last one is kept.

Download only content you have the right to save.

## License

[GPL-3.0](LICENSE). Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
