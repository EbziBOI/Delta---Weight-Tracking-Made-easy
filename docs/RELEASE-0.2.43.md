# Delta 0.2.43

Stage 2 joins the main app: food search and barcode scanning, local product-page
and nutrition-label import, meals, custom foods and recipes, targets, nutrition
insights, fasting, optional photo logging and four resizable food widgets.
Glass and Tubik remain independently customizable.

## Charts and interaction

- Daily averages, weekly averages and monthly averages are separate from the
  visible window. Nutrition starts with daily consumed totals; weekly/monthly
  points average recorded daily totals. Missing days never become zero.
- Daily windows: 7D, 1M, Journey, All. Weekly: 2M, 1Y, Journey, All.
  Monthly: 6M, 1Y, Journey, All. Every chart remembers its own choices,
  including a separate saved window for each averaging level.
- The date-view dot sits beside the chart title. Hold and slide to preview an
  averaging choice. Slim segmented controls also support holding and sliding,
  with a raised selection and live updates.
- Time-series charts cycle between dots, smooth bounded trend curves and
  connected dots. Period details show exact covered dates, recorded-day counts
  and partial current weeks/months.
- Horizontal pans/flings hide the selection while moving, then select the
  rightmost visible data point. Vertical drags continue scrolling the page.
- Rhythm graphs scroll through weeks and stay linked to the date strip.
  Forecast signals include 7, 14 and 30 days, with coverage and confidence reasons.
- The Diary’s existing daily nutrition card includes a compact seven-day macro
  plot: one point each for protein, carbohydrate and fat on each recorded day.
  Unknown labels stay empty; an explicitly reported zero is retained.
- Weight/body-fat calendar markers differ by both colour and shape.
  Press highlights match their surrounding shapes in both themes.
- Android Back handles screen navigation. Settings includes a persisted global
  Haptic feedback switch for buttons, selectors and custom gestures.

## Update and beta transfer

Install **Delta-0.2.43.apk** over Delta without uninstalling it. This retains the
normal application ID (`io.github.ebziboi.delta`) and permanent signing key,
including compatibility with the optional 0.2.42 Return candidate.

Beta remains a separate app. To move its data into this release, open
**Delta Beta → Settings → Move between Delta apps → Copy to Delta**, then review
and confirm replacement in Delta. Main Delta exposes the same transfer and
portable backup controls under **Settings → Backup and transfer**. Existing
confirmation and pre-import backup safeguards still apply. Android permissions,
placed widgets and API credentials remain with their respective apps.

Nutrition reads from Health Connect; food logging does not write meals into
Health Connect. Beta transfer itself does not write measurements into Health
Connect. Existing main-app weight/body-fat permissions and behavior continue.
