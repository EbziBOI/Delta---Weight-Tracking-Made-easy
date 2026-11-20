# Delta

**Stage 2 is now in the main Delta release.** Install the versioned APK as an update to Delta. Beta users can copy their diary and settings through **Settings → Backup and transfer**; see [release notes](docs/RELEASE-0.2.45.md).

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

**The main release includes the food diary, nutrition charts, food widgets and full backup/transfer.** The original package and signing key are retained.

- Reads your full weight history from Health Connect, so progress starts from
  your real start date rather than from the day you install
- Start, current and target weight, with an optional target date
- Weekly rate, projected goal date, on-track check and weigh-in streak
- Five weight widgets, each resizable from a single cell to a whole page: the Progress
  card (the Progress tab's first card), the Complication (the percentage art on
  its own), Trend (graph with an optional progress bar), Pace (weekly rate, goal
  date, 7-day average and streak) and Body fat. The Progress widget grew into
  the Progress card; the retired Journey and Start/now/target widgets are hidden
  from the picker, and ones already placed carry on as Trend and the Progress card
- See-through widgets check their coloured text against the wallpaper they sit
  on, and lift only what would otherwise be hard to read
- Ten launcher icons, including the original: swipe the icon deck in Appearance,
  then hold or double-tap an icon to apply it
- Hold the chart’s title-row dot for Daily, Weekly or Monthly averages; separate slim controls choose the visible window. Each chart remembers its averaging, window and drawing style
- Measured body-fat percentages in Progress, History, goals, scale CSV imports,
  Health Connect and widgets; weight remains independent of missing body-fat data
- Studio typography pairs bold geometric headings with editorial and mono accents
- Signature, Orbit, Bloom, Contours, Ribbon, Mosaic and Summit percentage complications follow both design themes and scale into widgets
- Hold a card’s heading and drag to rearrange Home, Weight trend or Body composition. Each page saves its own order, including individual Home metric tiles and detail insight cards. Dragging scrolls the page near its edges; graph and calendar touches retain their own behavior
- Combined Home trends with independent Weight, Body fat and Nutrition toggles; recorded-only points, today at the right edge and an animated selection marker after date panning
- Health Connect food integration, including supported micronutrients; imported totals stay separate from local diary entries. Optionally, the local diary is written to Health Connect too (main app only): edits update and deletions remove the same records, and Delta never reads its own entries back
- Optional energy budget (Settings → Body & energy): resting energy from lean mass when body fat is known, otherwise from height, age and sex; activity from Health Connect active calories and steps (discounted, 75% by default) or a chosen activity level; maintenance learned from complete logging days and the weight trend; and a deficit or surplus from your target and date, capped at 1% of body weight a week and never below resting energy. BMI and today's activity and workouts are shown alongside, and the budget can set the diary's energy target automatically
- Four diet widgets: Energy budget, Macro balance, Food diary with quick add, and a live Fasting clock; all resize with per-widget settings and previews
- Nutrition charts place the 30-day median of recorded daily totals at 75% height and mark larger values with an overflow triangle, retaining their full values
- Three compact, equal-width Home detail cards form a staircase outside the chart; Android Back returns to Home
- Reorder Nutrition trends and Forecast cards; configure nutrient balance pies and inspect data-driven intake comparisons and coverage
- Full backup and confirmed copying between Beta and main Delta preserve diary, measurements, goals, appearance and individual chart choices
- Your rhythm's week strip swipes independently, with one bounded triangle-arrow pair, coral in Tubik
- Store-filtered food search starts with major UK grocers and learns from local search/logging use
- Diary: tap a day on the seven-day macro chart to open it everywhere (kcal headline, macros, meals); swipe the chart for weeks and the summary card for days. Protein, carbs and fat sit beside the kcal total with their targets
- Every nutrient breakdown donut is tappable: choose a segment to see a bar chart of the foods that supplied it. The diary card expands to show the selected day’s breakdown
- Repeat suggests only foods previously eaten at that meal, ranked by frequency and recency, and swipes between meals; the meal defaults to the time of day. Diary lines swipe left to delete, with Undo
- Add food keeps search and the barcode scanner in a raised bottom bar, with swipeable Recent / My foods / Recipes / Fast food tabs, instant local matches and one-tap quick add. Online search starts when typing pauses, typed barcodes are looked up directly, "200g rice" or "2 eggs" sets the portion, small typos still match, your own foods are ranked by how often you eat them, and Recent leads with what you usually have at this meal
- About 170 built-in everyday foods and home-style dishes (fried egg, a bowl of pasta, spaghetti bolognese, jacket potato, curry, sandwiches, fruit, drinks…) with typical portions, found by everyday names and listed by category in the Everyday tab
- Bundled UK fast-food menus (McDonald’s, KFC, Burger King, Subway, Greggs, Nando’s, Domino’s, Five Guys, Starbucks, Costa) with approximate published values and links to each chain’s guide
- The scanner is full screen with a viewfinder, flashlight, tap to focus, pinch zoom, number entry and a gallery button, and offers every back lens the phone exposes (for example 0.6×, 1×, 3× and 5× on a Galaxy Ultra) inside Delta; products the database lacks, or lists without nutrition, open straight into label entry with an in-place label scan
- Nutrition labels are photographed with Delta's own camera, which captures automatically once a readable table is in view, finds and crops the table, and reads it on the phone. The reader understands English, French, German, Spanish, Italian, Dutch, Polish, Portuguese and Danish row names, tolerates OCR misspellings, keeps values in their columns, pairs kJ/kcal figures, corrects a "g" misread as "9" only when the label's arithmetic requires it, and fills salt from sodium (or the reverse); consistent reads are ready to save without an extra review step
- Every selector also responds to a horizontal swipe; trend lines use a centred, time-weighted fit that no longer lags recent readings
- A compact Progress/Diary/History dock with an adjacent plus button; both hide on downward scrolling and return together. Active tabs use accent text and icons without a pill
- A single Health Connect status pill opens its dedicated access and sync page
- Short, equally sized top/bottom fades retain fully black ends while content scrolls behind the toolbar
- Tap Weekly rate, 7-day average or Streak for one shared weekly calendar. Seven Sunday–Saturday bubbles set the saved start of the week; metric tabs retain that choice. Goal by opens the projection and recent pace/data-quality insights
- Weight trend and Body composition open calendars marked separately for weight and body fat. Selecting a date shows every recorded measurement and its time in a compact card; empty dates remain empty. Body-fat logging lives in its detail page
- Insights open a dedicated view with 7-day, 30-day and journey periods. Expand each statistic for its calculation: daily averages, recorded change, previous-period comparison, coverage, fitted pace, range, variation and recording rhythm. Duplicate readings and future timestamps are excluded
- Choose art, a smooth/segmented/dotted bar, thicker card-perimeter progress with a goal percentage, or both in **Settings → Complications**, with a live card preview
- Glass dashboard panels, animated progress fills and press feedback respect Reduce motion
- Vertical graph drags scroll the page; horizontal pans and flings select the rightmost visible reading when they settle, with centered disabled boundary controls. Tapping a dot selects it immediately, holding and sliding scrubs through readings, and a double tap on the same spot changes the style. Workouts from Health Connect appear as ticks on the home graph
- History swipes between Weight and Body fat
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
targets, available micronutrients, food-only daily/weekly intake charts,
and a persistent fasting timer with history. Foods and diary snapshots live in a
separate transactional SQLite database; Health Connect sync does not replace them.

Barcode misses can now open **Import food**: search Google by barcode or name,
open the first result or paste a product link, and extract the displayed page's
nutrition locally. Optional on-device nutrition-label OCR supports crop/rotation,
serving columns and unit conversion, editable comparisons and saved barcode/source
provenance. It needs no AI account or API key, and the label image is not uploaded.
This differs from the optional meal-photo estimation below. Web pages may require
consent, block access or omit identifiers; unverified matches stay unverified.

Photo logging supports OpenAI, Gemini and Claude with a user-supplied key.
Choose a model in Settings → Photo logging. Images are resized and stripped of
metadata, sent only after **Analyse photo**, and every estimate can be edited or
removed before **Log reviewed foods**. API keys are encrypted using Android
Keystore, kept in `noBackupFilesDir`, and never added to saved-instance state.
Model availability and API charges depend on the chosen provider. No authenticated
AI request is made by the automated tests.

The full plan and data semantics are in [docs/SPEC.md](docs/SPEC.md).

Device validation covers diary/recipe/targets/copy/fasting/photo-review flows in
both themes, larger text, native barcode decoding, nutrition persistence and
isolation, encrypted credentials, chart gestures and widget rendering. Food-import
checks exercise the bundled OCR engine against a rendered label image, barcode
photo decoding, rendered web-page extraction, source matching, unit conversions,
editable comparisons and activity-state restoration. Release-specific results are
in [release notes](docs/RELEASE-0.2.43.md). Meal-photo AI request contracts use fixtures;
authenticated cloud AI requests are not made by the automated tests.

## Install

Pushes to `main` and `claude/**` branches build an APK. On your phone, download the latest one:

https://github.com/EbziBOI/Delta---Weight-Tracking-Made-easy/releases/latest/download/Delta.apk

Open the downloaded file and allow your browser to install apps when asked.
Requires Android 9 or newer; Health Connect works best on Android 14 or newer.

## Privacy

Your weight and diary are stored on your phone. If you turn on writing in Body & energy, diary entries are also written to Health Connect on the phone; the optional height, age and sex are kept in Delta's settings and never leave the device. Delta has no account, backend or
analytics. Food searches and barcode lookups go to Open Food Facts. The camera
barcode decoder and nutrition-label OCR run on-device. Optional web lookup opens
Google and retailer/manufacturer pages, whose normal website policies apply; it
sends the chosen product query rather than your diary or weight history.
Meal-photo AI analysis sends only your selected image to
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
variables. Main is the default channel. Pass `-Pdelta.channel=beta` to build
the separate `io.github.ebziboi.delta.beta` package with the same permanent key.
Historical beta/transfer builds are documented in `docs/BETA.md`.

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

Release APKs include the version in their filename (for example `Delta-0.2.43.apk`) and Android package metadata. GitHub builds keep increasing the version code and use the stored signing key for in-place updates. `Delta.apk` remains as a compatibility download. Local release builds also write a versioned copy next to the standard Android Studio output.

Widgets lay out by size class (one cell, a row, a block, a tall block), dropping the least useful line first as they shrink and adding detail as they grow. Figures are large with small units, text shrinks only to a readable floor before a shorter wording takes over, and values never end in an ellipsis. Tubik controls and data modules follow the saved transparency and blur. Repeated interface guidance is reduced to essential access, validation and data context.

The nine supplied icon source PNGs are retained unchanged. On macOS, `swift -module-cache-path /tmp/delta-swift-cache ci/restore-launcher-art.swift .` rebuilds their padded adaptive foregrounds by removing the outer tile rim and extending the inner background; the central artwork is never redrawn.
