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
| Goal | Target weight required; target date optional |
| Units | kg, lb, or stone and pounds, switchable in settings |
| Health Connect | Read and write |
| Storage | Single user, on the phone. Android's built-in backup plus a manual export; no account, no server |
| Look | Glass, in the spirit of iOS 26 and One UI: translucent panes with bright rims over deep backgrounds lit by soft washes of the accent colour. Heavily customisable: themes (Ember, Aurora, Midnight, Graphite, Neon, Light, Auto, Material You) and six accents, applied to widgets too |
| Type | Bundled open-licence fonts in three pairings. Editorial: Instrument Serif headings, Space Grotesk figures, JetBrains Mono labels, Manrope text. Modern (the default) and Technical swap the headings and figures |
| Widgets | Drawn as images by Delta, so they match the app exactly. Glass is solid, frosted or clear, with separate opacity in light and dark mode. Widgets can follow the app's theme or the phone's dark mode; from Android 12 the home screen holds a light and a dark image and switches itself |
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
- Widgets: progress, journey (graph and progress bar together), start/now/target,
  trend, pace. All resizable, themed, and shown as real previews in the widget picker
- Themes: Ember, Aurora, Midnight, Graphite, Neon, Light, Auto, Material You. Six accent colours
- Log and delete weigh-ins (written to Health Connect)

### 2. Food diary

- Food search and barcode scanning against Open Food Facts
- Custom foods and recipes
- Diary by meal, with repeat items and copy from a previous day
- Macro and micronutrient targets
- Charts
- Fasting timer
- Photo logging with an AI vision API and the user's own key

### 3. The rest of Cronometer's paid tier

- Nutrient search: find the foods highest in a chosen nutrient
- Nutrition scores
- Macro scheduler: different targets on different days
- Recipe import from a web address
- Custom biometrics
- Custom charts, with PDF and CSV export
- Water, supplement, exercise and sleep tracking
- Voice logging
- Streak widget and food widgets
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
`DELTA_KEYSTORE_PASSWORD`) and never committed. A build without those secrets is
signed with a throwaway key and cannot update an earlier install.
