# Delta

Stage 2 is being tested as a separate **Delta Beta** package. See [beta install and copy instructions](docs/BETA.md). The normal release remains unchanged.

An open-source weight and nutrition tracker for Android, built around home-screen
widgets that show where you started, where you are now and where you are going.

Δ is the symbol for change. The app icon is a delta whose outline is a progress
track; in the app and on the widgets that track is live and fills as you close
the gap to your target.

Choose **Settings → Appearance → Design system → Tubik** for a distinct
dashboard: coral actions, sage progress modules, violet graphs, instrument
dials, compact headings and matching widgets. Glass keeps its own saved colors,
background and blur when you switch back. The visual reference is
[Tubik’s restaurant operations concept](https://dribbble.com/shots/26787072-AI-Powered-Mobile-App-for-Restaurant-Operations).

## Status

**Stage 2 is available as a separate beta prerelease.** The existing online release remains the weight-tracking build. See the local build and validation notes below.

- Reads your full weight history from Health Connect, so progress starts from
  your real start date rather than from the day you install
- Start, current and target weight, with an optional target date
- Weekly rate, projected goal date, on-track check and weigh-in streak
- Five widgets, each resizable from a single cell to a whole page: the Progress
  card (the Progress tab's first card), the Complication (the percentage art on
  its own), Trend (graph with an optional progress bar), Pace (weekly rate, goal
  date, 7-day average and streak) and Body fat. The Progress widget grew into
  the Progress card; the retired Journey and Start/now/target widgets are hidden
  from the picker, and ones already placed carry on as Trend and the Progress card
- See-through widgets check their coloured text against the wallpaper they sit
  on, and lift only what would otherwise be hard to read
- Ten launcher icons, including the original: swipe the icon deck in Appearance,
  then hold or double-tap an icon to apply it
- Pinch to zoom dates horizontally and drag to pan; the vertical scale stays fixed. Tap a reading for its value/date,
  with buttons for zoom and reading navigation
- Measured body-fat percentages in Progress, History, goals, scale CSV imports,
  Health Connect and widgets; weight remains independent of missing body-fat data
- Studio typography pairs bold geometric headings with editorial and mono accents
- Signature, Orbit, Bloom, Contours, Ribbon, Mosaic and Summit percentage complications follow both design themes and scale into widgets
- Hold a card’s heading and drag to rearrange Home, Weight trend or Body composition. Each page saves its own order, including individual Home metric tiles and detail insight cards. Dragging scrolls the page near its edges; graph and calendar touches retain their own behavior
- Combined Home trends with independent Weight, Body fat and Nutrition toggles; recorded-only points, today at the right edge and an animated selection marker after date panning
- Read-only Health Connect food integration, including supported micronutrients; imported totals stay separate from local diary entries
- A compact Progress/Diary/History dock with an adjacent plus button; both hide on downward scrolling and return together. Active tabs use accent text and icons without a pill
- A single Health Connect status pill opens its dedicated access and sync page
- Short, equally sized top/bottom fades retain fully black ends while content scrolls behind the toolbar
- Tap Weekly rate, 7-day average or Streak for one shared weekly calendar. Seven Sunday–Saturday bubbles set the saved start of the week; metric tabs retain that choice. Goal by opens the projection and recent pace/data-quality insights
- Weight trend and Body composition open calendars marked separately for weight and body fat. Selecting a date shows every recorded measurement and its time in a compact card; empty dates remain empty. Body-fat logging lives in its detail page
- Insights open a dedicated view with 7-day, 30-day and journey periods. Expand each statistic for its calculation: daily averages, recorded change, previous-period comparison, coverage, fitted pace, range, variation and recording rhythm. Duplicate readings and future timestamps are excluded
- Choose art, a smooth/segmented/dotted bar, thicker card-perimeter progress with a goal percentage, or both in **Settings → Complications**, with a live card preview
- Glass dashboard panels, animated progress fills and press feedback respect Reduce motion
- Vertical graph drags scroll the page even after zooming; horizontal pans change dates, with centered disabled boundary controls
- The home complication fits the card’s available width, alongside the current weight with compact, aligned journey totals
- Original supplied launcher artwork, preserving its detail and gradients with adaptive-mask padding
- Per-widget graph ranges: 1 week, 1 month, 3 months, 1 year or the full journey
- Live transparency and blur controls in both themes with an in-place material preview
- Portrait layouts, equal-width animated selectors, responsive presets, and separate main-theme/accent choices
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

Stage 2 adds a daily breakfast/lunch/dinner/snacks diary, Open Food Facts search
and an offline camera barcode decoder, custom foods, recipes with cooked yield
and servings, repeat portions, meal/day copying, gram/energy-percentage macro
targets, 45 nutrients, daily/weekly intake charts with optional weight comparison,
and a persistent fasting timer with history. Foods and diary snapshots live in a
separate transactional SQLite database; Health Connect sync does not replace them.

Photo logging supports OpenAI, Gemini and Claude with a user-supplied key.
Choose a model in Settings → Photo logging. Images are resized and stripped of
metadata, sent only after **Analyse photo**, and every estimate can be edited or
removed before **Log reviewed foods**. API keys are encrypted using Android
Keystore, kept in `noBackupFilesDir`, and never added to saved-instance state.
Model availability and API charges depend on the chosen provider. No authenticated
AI request is made by the automated tests.

The full plan and data semantics are in [docs/SPEC.md](docs/SPEC.md).

Local verification (16 November 2026): 19 instrumented checks passed on the
DeltaVisual emulator, including full diary/recipe/targets/copy/fasting/photo-review
flows in both themes, light surfaces at 135% text size, the native barcode screen,
SQLite reopen/copy/snapshot invariants (including bounded nested recipes), real numeric-keyboard visibility, encrypted credentials, all three AI request
and response contracts, offline barcode fixtures, incomplete-label target comparisons, and existing weight/widget
rendering contracts. Open Food Facts product lookup and name search were also
verified against the public service. Android lint reported no errors. Live
AI authentication and photographing physical products still require a device/key;
the tests do not send a meal photo or authenticated AI request.

## Install

Pushes to `main` and `claude/**` branches build an APK. On your phone, download the latest one:

https://github.com/EbziBOI/Delta---Weight-Tracking-Made-easy/releases/latest/download/Delta.apk

Open the downloaded file and allow your browser to install apps when asked.
Requires Android 9 or newer; Health Connect works best on Android 14 or newer.

## Privacy

Your weight and diary are stored on your phone. Delta has no account, backend or
analytics. Food searches and barcode lookups go to Open Food Facts. The camera
barcode decoder runs on-device. Photo analysis sends only your selected image to
the provider you choose, after you tap Analyse; it does not send weight history or
the diary. Provider policies apply to that request. Android backup can include
local nutrition data, but excludes API credentials and cached photos.

## Building

GitHub Actions builds the app (`.github/workflows/build.yml`). To build locally,
open the project in Android Studio or run:

```
./gradlew :app:assembleRelease
```

A local release build without `DELTA_KEYSTORE_FILE` uses Android's debug key.
For an update to a permanent-key installation, supply the existing
`DELTA_KEYSTORE_FILE`, `DELTA_KEYSTORE_PASSWORD` and `DELTA_KEY_ALIAS` environment
variables. The beta uses the existing Delta signing key with a separate package ID,
`io.github.ebziboi.delta.beta`, and version `0.2.34-beta.1`. The optional transfer
update retains the normal app's package and uses version `0.1.34-transfer`.

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
| `nutrition` | Food labels, SQLite diary snapshots, recipes, targets, intake charts, fasting, camera scanning and review-first photo logging |
| `render` | Palettes, fonts, the delta track, the graph and each widget; shared by app and widgets |
| `ui` | The screens and the glass components they are built from (Jetpack Compose) |
| `widget` | The home-screen widgets: providers, the images they show, and picker previews |

## Credits

- Fonts: Instrument Serif, Space Grotesk, Manrope and JetBrains Mono, under the
  SIL Open Font License 1.1. The licences ship in `app/src/main/assets/licenses`.
- Icons: Material Symbols, under the Apache License 2.0.
- Food labels: [Open Food Facts contributors](https://world.openfoodfacts.org/),
  under ODbL. Product and licence links appear in the food search/detail screens.
- Barcode decoder: [ZXing core](https://github.com/zxing/zxing), under the Apache
  License 2.0. Camera preview: AndroidX CameraX. Licence notices ship in the APK.
- Backdrop blur: [Haze](https://github.com/chrisbanes/haze) by Chris Banes, under
  the Apache License 2.0.

Release APKs include the version in their filename (for example `Delta-Beta-0.2.34-beta.1.apk`) and Android package metadata. GitHub builds keep increasing the version code and use the stored signing key for in-place updates. `Delta.apk` remains as a compatibility download. Local release builds also write a versioned copy next to the standard Android Studio output.

Widgets lay out by size class (one cell, a row, a block, a tall block), dropping the least useful line first as they shrink and adding detail as they grow. Figures are large with small units, text shrinks only to a readable floor before a shorter wording takes over, and values never end in an ellipsis. Tubik controls and data modules follow the saved transparency and blur. Repeated interface guidance is reduced to essential access, validation and data context.

The nine supplied icon source PNGs are retained unchanged. On macOS, `swift -module-cache-path /tmp/delta-swift-cache ci/restore-launcher-art.swift .` rebuilds their padded adaptive foregrounds by removing the outer tile rim and extending the inner background; the central artwork is never redrawn.
