# Delta: what we are building

The agreed plan for Delta, kept here so the project does not depend on anyone's
memory of a conversation. Decisions are recorded as decided; anything still open
is marked as such.

## The idea

A personal, Cronometer-style nutrition and weight tracker for Android that
connects to Health Connect. The headline feature is a set of home-screen widgets
that show target weight against current weight, counted from a start date in the
past (the first use is a journey that began in September).

## Decisions

| Area | Decision |
| --- | --- |
| Name | Delta |
| Platform | Android, sideloaded APK. First device: Galaxy S24 Ultra |
| Build | GitHub Actions builds the APK; each build is published as a release |
| Weight history | Read from Health Connect, including data from before install (needs the history permission) |
| Start point | First weigh-in on or after the start date. Start date defaults to 1 September and can be changed; the starting weight can be overridden |
| Goal | Weight target optional during onboarding; target date optional |
| Units | kg, lb, or stone and pounds, switchable in settings |
| Health Connect | Read and write |
| Storage | Single user, on the phone. Android's built-in backup plus a manual export; no account, no server |
| Look | A shared, themeable glass control panel: independent background/accent colors, derived accessible roles, solid/vertical/radial/mesh/photo backgrounds, adjustable glow/blur/opacity/radius, reduced motion and reduced transparency. Ember, Ice, Violet, Mint, Rose and Mono presets; earlier themes remain available |
| Type | Bundled open-licence fonts in three pairings. Editorial: Instrument Serif headings, Space Grotesk figures, JetBrains Mono labels, Manrope text. Modern (the default) and Technical swap the headings and figures |
| Widgets | Drawn as images by Delta, so they match the app exactly. Glass is solid, frosted or clear, with separate opacity in light and dark mode. Widgets can follow the app's theme or the phone's dark mode; from Android 12 the home screen holds a light and a dark image and switches itself |
| Widget settings | Launcher long-press Settings with live preview, Save/Cancel and independent per-widget appearance; global defaults remain available |
| Food data | Open Food Facts |
| Photo logging | AI vision with the user's own API key; Claude, Gemini or OpenAI chosen in settings |
| Icon | The "Echo" delta: a typographic Δ inside one delta-shaped progress track. The launcher icon is fixed; in the app and widgets the track is live |

## Milestones

### 1. Weight (in progress)

- Health Connect sync with full history, hourly background refresh
- Progress from start to target on a live delta track
- Weekly rate, projected goal date, required rate and on-track check when a
  target date is set, weigh-in streak
- Graph as a smoothed trend line or as connected dots (chosen separately for the
  app and the widgets), with the target marked and an optional path to the goal
- Widgets: Progress card, Complication, Trend (graph with an optional progress
  bar), Pace and Body fat. All resizable from one cell to a page, themed, and
  shown as real previews in the widget picker
- Themes: Ember, Aurora, Midnight, Graphite, Neon, Light, Auto, Material You. Six accent colours
- Log and delete weigh-ins (written to Health Connect)

### 2. Food diary (separate Stage 2 beta)

- Food search and barcode scanning against Open Food Facts
- Custom foods and recipes
- Diary by meal, with repeat items and copy from a previous day
- Macro and micronutrient targets
- Charts
- Fasting timer
- Photo logging with an AI vision API and the user's own key

#### Stage 2 data and interaction rules

- Three main destinations: Progress, Diary and History. Settings stays at the top
  right. The adjacent plus logs food in Diary and weight in the weight screens.
  Detail screens have their own Back action. Both design systems share the existing
  animated selectors, typography, surfaces and reduced-motion preferences.
- Diary dates are local calendar dates. A log stores a complete food/recipe snapshot,
  amount, unit, meal and creation time. Editing a library food leaves past entries
  unchanged. Repeating an old portion never replaces a newer library food. Copies
  create new IDs in a single transaction and require different source/destination dates.
- Food values are per 100 g or per 100 ml, with optional measured serving/pack
  amounts. Volume is not assumed to equal mass. OFF's canonical mineral amounts
  are converted from g into Delta's displayed mg/µg; energy uses kcal or kJ / 4.184.
  Explicit 100-unit values are used; serving-only fields are not mixed into them.
- Missing label nutrients remain unknown. Day totals report coverage by food.
  Recipes calculate a nutrient only when every ingredient reports it. A recipe's
  finished weight and serving count determine per-100-g and per-serving values;
  each ingredient scales using its own mass/volume unit. Nested ingredient snapshots
  keep their computed nutrition and estimate provenance without recursively copying
  entire recipe trees into every diary entry.
- Macro targets support grams or energy percentages (sum 100%, with a positive
  kcal target), using 4/4/9 kcal per gram for protein/carbohydrate/fat. Optional
  micronutrient targets distinguish daily goals from daily limits.
- Charts keep unlogged dates as gaps and average across days with reported intake, with coverage
  shown for incomplete nutrient labels. Incomplete periods are excluded from
  target/limit success counts. Weekly bins begin at the selected period's
  first day. Daily intake contains food only. Home combines weight, body fat and
  energy behind independent toggles outside the chart card; each series has its
  own labelled scale. Measurement points use the latest actual reading per day.
  Charts end at today by default and snap selection to the latest visible recorded
  reading after date zoom/pan; vertical drags remain with the page.
- Fasting stores UTC start/end timestamps and an elapsed-hours target; there is one
  running fast. Timers survive process death, time-zone changes and app restarts.
  Reaching a target does not silently end a fast. History includes early finishes.
- OFF searches are submitted explicitly, cached during the session and spaced to
  respect the public search limit. Food detail links and an in-app source/licence
  link attribute Open Food Facts (ODbL). The bundled ZXing decoder needs no network
  or scanner-service download; CameraX owns the preview and releases it on exit.
- Photo providers are fixed official HTTPS endpoints, with editable model IDs and
  encrypted, non-backed-up device credentials. Analysis is user-triggered; images
  are resized/re-encoded to remove EXIF. Results are estimates and require a separate
  review/log step. Requests, credentials, images and provider response bodies are
  excluded from error messages/logging. There is no automatic nutrition write to
  Health Connect in Stage 2.
- Weight goals can be set later during onboarding, so nutrition does not require
  Health Connect access or a weight goal. Stage 3 remains separate.

### 3. The rest of Cronometer's paid tier

- Nutrient search: find the foods highest in a chosen nutrient
- Nutrition scores
- Macro scheduler: different targets on different days
- Recipe import from a web address
- Custom biometrics
- Custom charts, with PDF and CSV export
- Water, supplement, exercise and sleep tracking
- Voice logging
- Streak widget (future); four food widgets are available in Beta 3: Energy budget, Macro balance, Food diary/quick add and Fasting clock. Each has independent live-preview configuration and responsive layouts.
- Writing nutrition to Health Connect
- Manual export and import of all data

## How the progress numbers are worked out

- **Start weight**: the first weigh-in on or after the start date, unless overridden.
- **Current weight**: the most recent weigh-in, as the scale read it.
- **Progress**: (start − current) ÷ (start − target), held between 0 and 100%.
  Works for gaining as well as losing.
- **Weekly rate**: a straight-line fit through the last 28 days of weigh-ins
  (56 days, then everything since the start, if there are too few).
- **Projected goal date**: distance left ÷ weekly rate, only when the trend is
  heading towards the target.
- **On track** (with a target date): the projected date is within six days of it.
  Earlier is "ahead", later is "behind".
- **Streak**: consecutive days with a weigh-in, counting back from today, or from
  yesterday if there is none yet today.
- **Trend line**: at each weigh-in, a straight line fitted to its neighbours with
  nearby days weighted most, so the line follows the data without lagging.
- **Graph scale**: when a graph shows the whole journey, its height always runs
  from the starting weight to the target, so the distance covered and the
  distance left are drawn to one scale and the latest point sits as far down as
  the percentage says.
- **Path to the goal**: a dashed line from the latest point to the target at the
  projected goal date. If that date is far off, the line runs to the edge of the
  graph and points onward, so recent weigh-ins keep most of the width.

## Signing

Android installs an update over an existing app only if both are signed with the
same key. The key is kept in repository secrets (`DELTA_KEYSTORE_B64`,
`DELTA_KEYSTORE_PASSWORD`) and never committed. Release publication fails if those secrets are missing, so a throwaway key can
never silently break updates. Run `bash ci/setup-signing.sh` once to create and
configure the permanent signing key.

## Beta testing channel

The Stage 2 test app uses `io.github.ebziboi.delta.beta` and copies Stage 1 data
only after consent through the optional same-signed transfer helper. Beta
measurement writes/deletions/imports stay in its own cache. Health Connect is
read-only; refresh and background widget work preserve the measurement test
snapshot. Foreground refresh reads permitted Health Connect food records into a
separate table, upserting by source record ID without replacing local meals.
Existing nutrition databases migrate in place, and saved launcher aliases are
restored on package replacement.
See [BETA.md](BETA.md) for installation and reproduction details.

### Beta 3 nutrition presentation and widgets

Nutrition bar charts anchor their scale to a 30-day median of reported daily
intake, with that typical nonzero value at 75% height. Empty dates and unknown
labels are excluded; explicit reported zeros remain real data. Visual overflow
uses neutral upward markers without altering recorded values. Panning does not
recompute the scale. Weight/body-fat series remain independent.

Home detail links share an adaptive row in Weight, Body fat, Nutrition order.
Home's nutrition charts have a separate navigation entry that returns to Home;
Diary's chart route still returns to Diary. Week-strip swipes change the week,
while the rest of Your rhythm retains metric-page swiping.

Open Food Facts store filters use retailer and UK country tags. The initial
retailer ordering is based on major GB grocers, then adjusted locally for
successful searches and diary use; this does not claim current stock availability.
Food metadata stores retailer tags in backward-compatible diary snapshots.

Food widgets read the local diary and targets, use truthful unknown/partial-label
states, and never write to Health Connect. They share widget material/corner
rules and per-instance style storage. Large fasting widgets use Android's native
chronometer, while bounded background work refreshes their snapshots. Widget
removal/restoration delegates broadcast ownership to Glance.


### Beta 4 local food import

The barcode library lookup precedes Open Food Facts. A missing or unavailable
external record leads to an explicit import route. Google discovers candidate
pages; only a displayed product document is parsed. Product identity and source
are preserved, and missing identifiers remain unverified. Local barcode-photo
recognition and bundled ML Kit Latin OCR require no cloud AI credentials.

OCR and page parsing preserve quantity basis and units. Bounded values, unknown
labels and ambiguous columns never become fabricated exact zeros. Label/page
comparisons are separated from editable accepted values; only explicitly used
label values retain their label provenance. Editing them removes that provenance.
The import review survives activity recreation, and saving requires user review.
Home detail navigation is independent of the graph selection; week navigation
uses a single arrow pair alongside the existing week swipe gesture.


### Beta 5 charts, detail pages and return transfer

Time-series graphs use a hold/slide/release dot control for one-day, seven-day
and thirty-day date windows, with animated viewport and hover selection, plus
click/accessibility alternatives. Pinch zoom and zoom button rows are removed.
Fixed weekly rhythm and forecast projection graphs retain their format. Home
trend smoothing remains a persisted double-tap option. Detail buttons have equal
width and step left/centre/right outside the graph.

Nutrition and Forecast use the shared reorderable card system with independent
persisted orders. Nutrition summaries report coverage and unknown labels rather
than fabricating zero intake. The nutrient pie compares selected macro energy
or mass normalized to grams. Recent pace fits all distinct recorded dates in
each seven/fourteen-day window; confidence labels describe coverage and scatter,
not probabilities. Rhythm calendars and weekly dot graphs share a selected day;
pager spacing separates adjacent pages at physical screen edges.

Full-copy schema 1 includes the legacy measurement/settings envelope, nutrition
food/recipe snapshots, diary entries, Health Connect read snapshots, targets,
fasts and allowlisted presentation preferences. Only the same-signed sibling app
can invoke the receiver, which asks before replacement. JSON import validates
before mutation, saves an atomic private pre-import backup and attempts rollback
on failure. Credentials, permission grants and widget instances never transfer.
The optional base-package return candidate is attached to the beta prerelease;
main and latest stable stay unchanged. Imported base measurements are local-copy
records retained through refresh and deduplicated against Health Connect reads.
