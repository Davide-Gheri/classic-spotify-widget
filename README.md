# Classic Spotify Widget

Spotify removed its perfectly fine classic home-screen widget, and the new one is worse. This project recreates the classic widget experience.

Home-screen widgets for Spotify on Android: cover, title, artist, previous / play-pause / next.

- Shows only Spotify, even when YouTube or another app is playing.
- Keeps the last Spotify track after Spotify is closed or killed. The cover is cached on disk too.
- Two styles: *Classic Spotify Widget*, a 4×1 in the style of Spotify's old widget, and *Slim Spotify Widget*, a single slim row.
- Both work down to 1 row high. The slim one shows everything at 3×1 and up; narrower, it drops the cover, then the text.
- Kotlin. The widgets are plain `RemoteViews`; the app screen uses Jetpack Compose with Material 3 (Material You colors on Android 12+). minSdk 28 (Android 9), target 35, compile 37.

## Build

Open the folder in Android Studio and press Run. From a terminal you can also run:

```sh
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` also produces an installable APK. It's signed with the debug key because the app is meant for sideloading.

## Set up on the phone

1. Open **Classic Spotify Widget** and tap **Grant notification access**, then turn it on.
   - If the toggle is greyed out (Android 13+ restricts this for sideloaded apps): **App info → ⋮ → Allow restricted settings**, then try again.
2. Tap **Add to home screen** under the widget you want, or add it from the launcher's widget picker. Both start at 4×1.
3. Play something in Spotify once so the widget has a track to show.

## How it works

| Piece | What it does |
|---|---|
| `SpotifyListenerService` | Notification listener. The access lets it read media sessions. It watches every session to remember which app played last, and saves Spotify's track and cover. The only notification it reads is Spotify's player, to keep its buttons. |
| `TrackStore` | Last track, playback flags and last player in SharedPreferences, plus the cover in `files/cover.jpg`. |
| `WidgetRenderer` | Builds the `RemoteViews` from `TrackStore` and only re-renders when something changed. |
| `PlayerControls` | Button taps. If Spotify's session is alive, it uses the session's transport controls. If not, it presses Spotify's own notification button (`SpotifyActions`), or sends a media key that Android delivers to the last app that played. |
| `SpotifyActions` | Spotify's previous / play / next buttons from its media notification, kept in memory so they still work after Spotify has closed. |
| `SetupActivity` / `SetupScreen` | Compose screen: notification access, a live preview of each widget with the last track, and buttons to pin them. |

**When Spotify isn't running.** Android delivers media keys to the app that played last. Most of the time that's Spotify, and play resumes the last track without opening the app. If another app (YouTube, a podcast app…) was the last to play, the play button opens Spotify instead, so it never wakes the wrong app. 

The widget first presses Spotify's own button, saved from Spotify's media notification the last time it showed. That runs Spotify's own handler, the same one its notification and its official widget use. These buttons last until Spotify is force-stopped or the phone restarts, and they're lost if this app's process is killed until Spotify shows its notification again. Without one, the widget falls back to the media key.

If Spotify still hasn't started playing 6 seconds after play, the widget opens it: after a long idle Spotify can leave behind a session that ignores media keys.

## Tweaks

- Colors: `res/values/colors.xml`.
- Breakpoints for hiding the cover and text: `MIN_WIDTH_FOR_COVER` and `MIN_WIDTH_FOR_TEXT` in `WidgetRenderer.kt`.
- Layouts: `res/layout/widget_classic.xml` and `res/layout/widget_player.xml` (slim). Widgets only allow a limited set of views (`LinearLayout`, `FrameLayout`, `ImageView`, `TextView`…).

## Troubleshooting

- **Widget stuck on "needs access"**: open the app once after granting access, which refreshes the widget.
- **Stops updating after a while** (aggressive OEM battery managers, e.g. some Xiaomi and Samsung builds): set Classic Spotify Widget's battery usage to *Unrestricted* in App info.
- **Cover missing**: Spotify sometimes sends the cover a moment after the title. It appears with the next update.
