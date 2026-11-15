# Delta

An open-source weight and nutrition tracker for Android, built around home-screen
widgets that show where you started, where you are now and where you are going.

Δ is the symbol for change. The app icon is a delta whose outline is a progress
track; in the app and on the widgets that track is live and fills as you close
the gap to your target.

Choose **Settings → Appearance → Design system → Tubik inspired** for a distinct
matte dashboard: coral actions, sage progress modules, violet graphs, instrument
dials, compact headings and matching widgets. Glass keeps its own saved colors,
background and blur when you switch back. The visual reference is
[Tubik’s restaurant operations concept](https://dribbble.com/shots/26787072-AI-Powered-Mobile-App-for-Restaurant-Operations).

## Status

**Weight tracking is available.** GitHub Actions builds and publishes an installable APK.

- Reads your full weight history from Health Connect, so progress starts from
  your real start date rather than from the day you install
- Start, current and target weight, with an optional target date
- Weekly rate, projected goal date, on-track check and weigh-in streak
- Five resizable widgets: progress, journey (graph and progress bar together),
  start/now/target, trend graph and pace
- A customizable glass design system shared by the app and widgets: independently
  choose background and accent colors, with automatic text and surface contrast
- Backgrounds: solid, vertical gradient, radial gradient, mesh, or a photo picked
  from your device; custom hex/HSV color controls and Ember, Ice, Violet, Mint,
  Rose and Mono presets. Earlier theme choices remain available
- Adjustable glow intensity, backdrop blur, glass transparency and corner radius,
  with reduced-motion and reduced-transparency options
- Widget glass can be solid, frosted or clear, with its own light/dark opacity
  and theme mode; widgets use static shading and respect the app's accessibility settings
- Touch and hold a placed widget, then choose Settings to customize it with a live
  preview. Save applies only to that widget; Cancel keeps its previous look
- Three bundled type pairings: Editorial, Modern and Technical
- Graphs can use a smoothed trend or connected dots, separately in the app and
  widgets, with an optional dashed path to your goal
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
release publication stops when those secrets are missing. Run `bash ci/setup-signing.sh`
once to create a permanent key and configure them. Keep the generated `.signing/`
folder backed up privately: future updates must use the same key.

Builds 13 and 14 used different throwaway signing keys, so Android cannot install
one over the other. A build made with the permanent key needs a one-time fresh
installation if the currently installed app used a throwaway key. Uninstalling
can remove local settings and cached data; record your goal and start date first.

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
