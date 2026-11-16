# Delta Stage 2 beta

This is a separate app, **Delta Beta** (`io.github.ebziboi.delta.beta`). It installs
beside **Delta** (`io.github.ebziboi.delta`). The normal release and its download
are unchanged. This prerelease adds the Stage 2 food diary in Glass and Tubik.

## Beta 4 changes

- Missing barcodes can open an import flow instead of ending at “not found”.
  Previously saved imports are checked first. Barcode photos can also be cropped
  and decoded locally.
- Product lookup opens Google for the barcode plus “nutrition”, with a first-result
  shortcut, normal browsing, name search and direct product links. Import reads the
  displayed product page locally, including dynamically rendered nutrition tables.
  Search-result summaries cannot be imported as product evidence. No AI/search API
  accounts or keys are required. Google/retailer availability and consent screens
  still apply; no blocked page is bypassed.
- Structured product identifiers distinguish matched, different and undetected
  barcodes. The food review retains the source URL; an unverified match is never
  labelled as a barcode confirmation.
- Optional bundled, on-device nutrition-label OCR includes camera/gallery input,
  crop handles, rotation, physical table-row reconstruction, skew correction,
  contrast processing and additional orientation passes when needed. No label
  photo is sent to an AI provider. Original photos stay in the selected gallery
  or temporary app cache; parsed values and provenance are saved to the library.
- Reviews support multiple nutrition columns, serving conversion, g/ml, kJ/kcal,
  decimal commas, US thousands and daily-value percentages. Missing, bounded or
  conflicting values are left for review. Product-page and label differences are
  shown separately, with editable values, a transcription editor and explicit
  confirmation before saving. OCR failure keeps the page values usable; label-only
  import works too. Values exceeding expected ranges or inconsistent macros are
  flagged, without pretending a software check proves package accuracy.
- Home always shows three vertically stacked detail buttons outside the chart,
  preserving their colours and using triangle symbols. Chart toggles affect only
  the plotted series. Your rhythm has one pair of week arrows, coral in Tubik;
  individual dates remain tappable and the week remains swipeable.

## Beta 3 changes

- Nutrition bars use the median of recorded daily totals from the 30 days ending
  at the chart's end date. Typical nonzero intake sits at 75% height. Missing days
  and unknown labels are absent; the scale stays fixed while zooming/panning.
  Larger values are capped visually with a neutral upward triangle; selecting
  them still shows the full actual amount.
- Home's selected measurements expose neatly ordered, animated detail links.
  Nutrition details opens the same food charts as Diary, and both Back controls
  return to Home when opened there. Opening charts from Diary still returns there.
- Swipe the week strip in Your rhythm to change weeks. Swipes elsewhere retain
  the Rate/Average/Streak page gesture. Week/day arrow controls disable at bounds.
- Food search has UK store filters. Major grocers are the initial recommendations;
  successful searches and logged products promote frequently used stores locally.
  Store filtering uses Open Food Facts' retailer/country tags, not live stock data.
- Four independent food widgets: Energy budget, Macro balance, Food diary with
  quick add, and Fasting clock with a native live elapsed timer at suitable sizes.
  All resize from a single launcher cell to full grids and offer long-press
  Settings with a live preview, per-widget material, theme, opacity and corners.
  Existing weight widgets and their settings stay available.

## Beta 2 changes

- The home charts are combined, with independent Weight, Body fat and Nutrition
  toggles above the card. Choose any combination; it is saved between updates.
- Daily intake contains food only. The chart opens ending today, and missing
  dates have no points. Unknown nutrient labels are excluded from daily averages.
- A dotted selection marker starts at the latest recorded value. Date zoom/pan
  hides it while moving and selects the latest visible reading on release.
  Vertical gestures continue scrolling the page; Reduce motion is respected.
- Health Connect food reads include consumed energy, macros and all supported
  micronutrients. Allow **Read food and nutrients** in Settings → Health Connect.
  Reads run on foreground refresh; imported records are read-only and separate
  from local meals. Refresh updates/removes imported records without duplicates.
  This beta requests no Health Connect write permissions.
- A narrower tab arrangement leaves more room inside the bottom bar's corners.
  The chosen launcher icon is saved before applying it and restored after updates.

Existing beta users can install the new versioned beta APK over an earlier beta. Your
local diary, goals, appearance and selected icon are retained. The transfer
companion below is only needed when initially copying the original app.

## Install and copy the current app's data

1. Install the versioned **Delta-Beta-…apk** attached to this release.
2. For a full copy from the published Stage 1 app, install the optional
   **Delta-Transfer-0.1.34-transfer.apk** as an update to your current Delta.
   Keep Delta installed: this update retains its package, signing key, data and
   Stage 1 interface. It adds only the private transfer/consent screen.
3. Open **Delta Beta → Copy from Delta → Copy**. Delta opens a confirmation
   screen; choose **Copy to Delta Beta**.

Copied: weight and body-fat history, goals, units, appearance (including available
photo backgrounds), card layouts, icon choice and global widget defaults.
Android permissions, placed widgets and API credentials stay with their original
app. The beta food diary and API keys are independent and are not overwritten by
re-copying Stage 1 measurements/settings. Re-copying asks for confirmation before
replacing the beta's measurement snapshot and settings.

## Testing stays separate

- Beta additions, scale imports and deletions are local to Beta. They are not
  written to Health Connect or the current Delta app.
- The beta does not request Health Connect write or background-read permissions.
  It has an explicit, one-way **Copy Health Connect readings** action if desired;
  ordinary refresh leaves your test snapshot intact.
- Both apps must have the same signing certificate for the private copy flow.
  The source app also asks for confirmation, and grants only temporary read
  access to the prepared snapshot. No transfer is sent to a server.
- Remove Delta Beta when finished to discard its test data. The normal app remains.

## Builds and publication

This branch defaults to the beta package. Use `-Pdelta.channel=stable` only when
intentionally preparing a future normal release. The beta workflow builds on
`beta/**` pushes; publishing a further prerelease is an explicit workflow-dispatch
option. Prereleases are never marked as the latest normal release.

The optional transfer APK is built reproducibly from the last Stage 1 tree
(`1a80210`) plus the shared transfer code using `ci/prepare-beta-transfer.py`.
It is intended for the current published 0.1.x app, not as a Stage 2 downgrade.
The GitHub prerelease includes both APKs; no changes are merged into main.

AI photo logging uses your own provider API key. Provider request/response
contracts are tested with fixtures; authenticated AI requests are not made by
the test suite.

## Validation

The release build and lint pass. All 54 distinct device checks pass across the
full regression run and focused UI rerun. New checks cover actual bundled OCR on
a rendered nutrition-label image, barcode-photo decoding, a rendered WebView
product document, wrong/absent/related-product identifiers, UK/US nutrition units
and columns, label/page conflicts, explicit value selection and review-state
restoration in both themes. Automated OCR inputs are fixtures, not a claim of
perfect accuracy on arbitrary physical packaging.

A live device check loaded the Morrisons Oasis Garlic & Herb Dip page, rejected
optional cookies through its normal prompt, and imported its name, brand and
nutrition, including 590 kcal per 100 g. Its barcode was not exposed in the parsed
product data, so the import correctly remained unconfirmed by barcode.
Installing the signed release over the beta preserved all three selected chart
channels and restored the saved launcher icon after a simulated launcher reset.

Food widget layouts are checked on all 48 supported cell combinations (1–6 wide,
1–8 tall), in both design systems, light/dark modes and all three materials:
2,304 rendered combinations, with audited text bounds. Device checks also cover
native widget binding, live configuration, Save/Cancel and activity recreation.
The normal release remains untouched; this channel requests no Health Connect
write permissions.

Retailer seed references: [Worldpanel GB grocery update](https://worldpanelbynumerator.com/insights/grocery-sales-rise-2-0-but-parents-tighten-belts-on-school-uniform-spend).
Filter contract: [Open Food Facts search API](https://github.com/openfoodfacts/openfoodfacts-server/blob/main/docs/api/ref/api.yaml).

Label OCR implementation: [ML Kit bundled text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
