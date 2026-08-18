# Boomer Solitaire

A genuinely clean, calm Klondike solitaire for Android. **No ads, no accounts,
no tracking, no internet — provably.** The manifest declares no permissions,
so the app is incapable of phoning home.

Built for players who want a beautiful, dignified card table and nothing else.

## Features

- **Klondike** — Draw 1 by default, Draw 3 in settings, unlimited passes
- **Every deal winnable** — a built-in solver proves each deal has a winning
  line before it's offered (toggle off for purist random deals)
- **Unlimited undo** with no penalty, all the way back to the deal
- **Tap-to-move** — tapping a card sends it to the best legal home; full
  drag-and-drop with generous drop zones also works
- **Kind hints** that prefer foundation plays and uncovering hidden cards
- **Auto-complete** ("Finish game") when the win is guaranteed
- **Statistics & personal bests** per draw mode, stored locally with Room
- Continuous auto-save: process death, rotation, or a week away — the game
  resumes exactly where it was
- Four themes (felt green, linen, dark, high contrast) + system dark mode,
  four-colour deck option, three card sizes, left-handed layout, optional
  timer, reduce-motion toggle, TalkBack descriptions on every card
- Cards are drawn entirely in code (Compose Canvas) — crisp at any size,
  ~1.2 MB APK

## Building a release APK

Requires JDK 17+ and an Android SDK (Android Studio's bundled versions work).

```bash
# from the repository root
./gradlew :app:assembleRelease
```

The APK lands at `app/build/outputs/apk/release/app-release.apk`
(signed with the debug key so it can be sideloaded directly).

Install on a connected device:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

If `local.properties` is missing, create it with your SDK path:

```
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Permissions

None. Verify yourself:

```bash
aapt dump permissions app/build/outputs/apk/release/app-release.apk
```

The only line printed is the app's own
`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a self-scoped marker AndroidX
adds to every modern app. It grants no capability and is never shown to the
user. No `INTERNET`, no `VIBRATE`, nothing.

## Project layout

- `engine/` — pure-Kotlin Klondike engine (rules, solver, hints,
  auto-complete, undo). Zero Android dependencies, 61 unit tests:
  `./gradlew :engine:test`
- `app/` — Jetpack Compose UI, DataStore settings/auto-save, Room statistics
