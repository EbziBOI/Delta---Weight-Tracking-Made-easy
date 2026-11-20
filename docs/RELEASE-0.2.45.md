# Delta 0.2.45

An energy budget built on Health Connect, Delta's own multi-lens camera, a much
more automatic nutrition-label reader, and smoother charts and navigation.

## Energy budget (Settings → Body & energy, or the card on the Diary)

- Optional height, year of birth and sex. Height is read from Health Connect when
  it's there. BMI is shown, with a note that body fat says more.
- Resting energy uses lean mass (Katch–McArdle) when you have a recent body-fat
  reading, otherwise Mifflin–St Jeor. A resting-energy figure a scale wrote to
  Health Connect takes precedence.
- Activity comes from Health Connect active calories and steps, merged by Health
  Connect's own source priority, with 75% counted by default (adjustable), or from
  a chosen activity level. Today's activity and workouts appear on the Diary card.
- After about two weeks of complete logging and regular weigh-ins, maintenance is
  learned from your intake and the weight trend, and blended with the estimate.
- The deficit or surplus follows your target weight and date, or a chosen weekly
  pace. It is capped at 1% of body weight a week and never goes below resting
  energy (or 1,200–1,500 kcal).
- "Set my daily energy target automatically" keeps the Diary target and food
  widgets in step with the budget.
- These Health Connect reads are optional and requested only from this page.

## Food diary in Health Connect

- Optionally, every food you log is written to Health Connect (main app only).
  Edits replace and deletions remove the same record; failures are retried on the
  next sync. Delta skips its own records when reading food back, so nothing is
  counted twice. Turning writing off offers to remove what Delta wrote.

## Camera

- The barcode scanner and the new label camera offer every back lens the phone
  exposes, including the ultrawide and telephotos (0.6×, 1×, 3×, 5× on a Galaxy
  Ultra), with pinch zoom. Phones with a logical multi-camera switch lens through
  the zoom ratio; separately listed cameras are opened directly.
- Barcode frames are read at 720p and more often.
- Label photos no longer go through the system camera app.

## Nutrition labels

- The label camera captures by itself once it has seen a readable table on two
  consecutive frames (Manual mode and the shutter remain).
- The table is found and cropped automatically; no crop step.
- Row names in English, French, German, Spanish, Italian, Dutch, Polish,
  Portuguese and Danish, with accents folded and small OCR misspellings tolerated.
- Values stay in their columns from their position on the photo, so a blank cell
  no longer shifts the rest; %RI columns are dropped.
- kJ/kcal pairs without units are read correctly.
- A "g" read as "9" (39 instead of 3g) is corrected only when that makes the
  label's arithmetic work, with a warning.
- Salt is filled from sodium, or the reverse, and marked as calculated.
- A read that is complete and adds up is ready to save without ticking "reviewed".
- When a barcode isn't in the database, the label is scanned straight into the
  new-food form.

## Everyday foods

- About 170 everyday foods and home-style dishes are built in and work offline:
  eggs every way, toast, porridge, pasta dishes, rice, potatoes, meat and fish,
  curries, sandwiches, pizza, fruit, vegetables, dairy, snacks, spreads and drinks.
- Each opens at a typical portion ("1 large egg", "1 bowl", "1 slice") that can be
  changed. Values are typical per 100 g from UK and USDA tables, with carbohydrate
  excluding fibre as on UK labels.
- Typing finds them by everyday names: "bowl of pasta", "fried eggs", "spag bol",
  "chiken breast". They also have their own Everyday tab with categories, and
  "Correct values" saves your own version.

## Charts and navigation

- Tap a dot on any graph to select it immediately. Hold and slide to scrub. A
  double tap on the same spot changes the style; tapping two dots in a row no
  longer does.
- History swipes between Weight and Body fat.
- The nutrient-balance donut sizes its centre text to the hole and moves its
  legend underneath when the screen or text size is too tight.
- Add food's search bar sits higher, on its own glass panel, clear of the screen's
  bottom fade. Online search starts when typing pauses; typed barcodes are looked
  up; "200g rice" or "2 eggs" sets the portion; typos still match; Recent leads
  with what you usually eat at that meal.
