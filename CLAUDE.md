# CLAUDE.md

Android home-screen widgets that replace Spotify's own widget. The README covers what each class does; this file covers what isn't obvious from the code.

## Build

- `./gradlew assembleDebug` / `./gradlew assembleRelease`. APKs land in `app/build/outputs/apk/{debug,release}/`.
- Runs on the default JDK 25 (Android Studio's bundled JBR). Older AGP versions crash in `lintVitalAnalyzeRelease` on JDK 25, so don't downgrade AGP below 9.x.
- AGP 9.4.1 uses **built-in Kotlin**. Don't add `org.jetbrains.kotlin.android`. The Compose compiler plugin version must match the Kotlin version bundled with AGP (2.2.10 for AGP 9.4.1); when upgrading AGP, check its POM for `kotlin-gradle-plugin`.
- `compileSdk = 37` because Compose BOM 2026.09.00 requires it. `targetSdk` stays at 35 on purpose.
- Release builds run R8 with resource shrinking (about 1.8 MB, versus about 23 MB without). There are no custom keep rules: the app uses no reflection. After build changes, check that `layout/widget_*`, `xml/*_widget_info` and the drawables they use still appear in `aapt2 dump resources`.
- Release builds are signed with the debug key (sideload-only app). `adb install -r` therefore swaps between debug and release builds and keeps data, placed widgets and notification access.

## Architecture notes

- Two widgets share one renderer. `PlayerWidget` is the slim one; `ClassicPlayerWidget` is a 4×1 in the style of Spotify's old widget, with a blue band on top and a dark controls bar below. Both layouts use the **same view IDs**, so `WidgetRenderer.build()` fills either one. Only the slim widget applies the width breakpoints (`fitWidth`).
- The widgets are plain `RemoteViews`. Only the setup screen (`SetupActivity` and `SetupScreen`) uses Compose / Material 3. Its widget previews inflate the real widget layouts through `AndroidView`.
- Classic layout: the band and the bar split the height 3:2 by weight, and the cover fills the band's height. RemoteViews can't clip children, so the root has the dark rounded background and the band has a shape with only its top corners rounded.
- The user's phone uses a font scale of 1.15. Text in 1-row layouts clips easily, so check descenders when changing sizes or padding.

## Playback controls: what was learned about Spotify

The fallback order in `PlayerControls` is deliberate:

1. Spotify's active session (transport controls).
2. Spotify's own notification buttons (`SpotifyActions`): previous, play and next, cached from its media notification. Its compact actions are `[prev, play/pause, next]`. The middle button is only cached while Spotify isn't playing.
3. A media key, but only if Spotify was the last app to play.
4. For play only: open Spotify if it isn't playing after 6 s. A cold resume takes about 5 s, and `isPlaying` counts BUFFERING and CONNECTING as playing.

Dead ends; don't retry them:

- **`MediaBrowserService`:** Spotify returns "No root" to unknown callers, even with `EXTRA_RECENT`. Its media3 `SpotifyMediaLibraryService` is disabled.
- **Spotify's widget receiver:** not exported.
- **Binding to wake Spotify, then sending a key:** the fresh session ignores the key. It's worse than doing nothing, because it hides the media button receiver path.
- **After a long idle,** Spotify can leave behind an inactive session (`state=NONE`, no metadata) that swallows every media key. Its process may also be frozen. That's why step 4 exists.

## Testing on the device

- A Pixel is usually attached over wireless adb: `~/Library/Android/sdk/platform-tools/adb`. Verify changes on the phone, not just by building.
- Read Spotify's state with `adb shell dumpsys media_session` (look for `spotify-media-session`: `active=` and `state=`).
- To simulate Spotify being closed, use `adb shell am stop-app com.spotify.music`. **Not** `force-stop`: that cancels Spotify's PendingIntents and puts it in the stopped state, which breaks paths that work in real life. `am kill` doesn't work while Spotify holds its foreground service.
- `adb shell cmd media_session dispatch play|pause` sends a system media key.
- Home-screen pages and widget positions change, and Home doesn't always land on the same page. Take a screenshot before tapping by coordinates. The phone may also be locked; you can't unlock it.
- Don't change the user's settings (dark mode, notification access) or home screen (pin dialogs) to test something; ask them.
- The user also has the official Spotify widget placed, which may keep Spotify warm and hide fallback bugs.

## Gotchas

- The shell is zsh: never name a variable `path`. In zsh it's tied to `PATH`, so assigning it breaks every later command in that call.
- Keep the README's class table and the "When Spotify isn't running" section in sync with behavior changes.
