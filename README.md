# Delta

An open-source weight and nutrition tracker for Android, built around home-screen
widgets that show where you started, where you are now and where you are going.

Δ is the symbol for change. The app icon is a delta whose outline is a progress
track; in the app and on the widgets that track is live and fills as you close
the gap to your target.

## Status

**Milestone 1: weight.** Working towards a first installable build.

- Reads your full weight history from Health Connect, so progress starts from
  your real start date rather than from the day you install
- Start, current and target weight, with an optional target date
- Weekly rate, projected goal date, on-track check and weigh-in streak
- Four resizable widgets: progress, start/now/target, trend graph, pace
- Themes (Midnight, Graphite, Neon, Light, Material You) and accent colours,
  shared by the app and the widgets
- kg, lb or stone and pounds
- Log a weigh-in in the app and it is written back to Health Connect

Food tracking comes next. The full plan is in [docs/SPEC.md](docs/SPEC.md).

## Install

Every push builds an APK. On your phone, download the latest one:

https://github.com/EbziBOI/Delta---Weight-Tracking-Made-easy/releases/latest/download/Delta.apk

Open the downloaded file and allow your browser to install apps when asked.
Requires Android 9 or newer; Health Connect works best on Android 14 or newer.

## Privacy

Your weight stays on your phone. Delta has no account, no server and no analytics.

## Building

GitHub Actions builds the app (`.github/workflows/build.yml`). To build locally,
open the project in Android Studio or run:

```
./gradlew :app:assembleRelease
```

Signing keys are never stored in this repository. The workflow reads the key
from the repository secrets `DELTA_KEYSTORE_B64` and `DELTA_KEYSTORE_PASSWORD`;
without them it signs with a throwaway key.

## How it is put together

| Package | What it does |
| --- | --- |
| `data` | Health Connect access, saved settings, and the progress maths |
| `render` | Draws the delta track, the graph and each widget; shared by app and widgets |
| `ui` | The screens (Jetpack Compose) |
| `widget` | The home-screen widgets (Glance) |
