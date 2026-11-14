# Delta

An open-source weight and nutrition tracker for Android, built around home-screen
widgets that show where you started, where you are now and where you are going.

Δ is the symbol for change. The app icon is a delta whose outline is a progress
track; in the app and on the widgets that track is live and fills as you close
the gap to your target.

## Status

**Weight tracking is available.** GitHub Actions builds and publishes an installable APK.

- Reads your full weight history from Health Connect, so progress starts from
  your real start date rather than from the day you install
- Start, current and target weight, with an optional target date
- Weekly rate, projected goal date, on-track check and weigh-in streak
- Five resizable widgets: progress, journey (graph and progress bar together),
  start/now/target, trend graph and pace
- A glass look: translucent panes over soft washes of colour, in the app and on
  the widgets. Themes Aurora, Midnight, Graphite, Neon, Light, Auto (light by day,
  dark at night) and Material You, each with six accent colours
- Widget glass can be solid, frosted or clear, with its own opacity for light and
  dark mode, and can follow the phone's dark mode
- The graph can be a smoothed trend line or connected dots, chosen separately for
  the app and the widgets, with an optional dashed path to your goal
- Three type pairings (Editorial, Modern, Technical) from bundled open-licence fonts
- kg, lb or stone and pounds
- Log a weigh-in in the app and it is written back to Health Connect

Food tracking comes next. The full plan is in [docs/SPEC.md](docs/SPEC.md).

## Install

Pushes to `main` and `claude/**` branches build an APK. On your phone, download the latest one:

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
| `render` | Palettes, fonts, the delta track, the graph and each widget; shared by app and widgets |
| `ui` | The screens and the glass components they are built from (Jetpack Compose) |
| `widget` | The home-screen widgets: providers, the images they show, and picker previews |

## Credits

- Fonts: Instrument Serif, Space Grotesk, Manrope and JetBrains Mono, under the
  SIL Open Font License 1.1. The licences ship in `app/src/main/assets/licenses`.
- Icons: Material Symbols, under the Apache License 2.0.
- Backdrop blur: [Haze](https://github.com/chrisbanes/haze) by Chris Banes, under
  the Apache License 2.0.
