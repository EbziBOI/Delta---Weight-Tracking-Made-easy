# Delta 0.2.48

The energy and BMI charts, rebuilt to be read at a glance.

## Energy today (Home)

- One number and one bar: kcal left today, and what you've eaten against your
  budget. Anything over the budget shows past a notch in amber. No legend needed.
- The last 7 days as small bars against a single budget line: under the line is
  under budget, over pokes above it in amber. Days you only partly logged are
  outlines instead of disappearing.
- Replaces the gauge with dashed and solid ticks, and the deficit bars around
  maintenance.

## Energy page

- Today's hero matches the Home card, and the plan line is in your own unit
  ("about 0.7 lb a week"). Today's Health Connect activity sits under it.
- "Eaten vs budget": bars of what you ate each day against that day's budget,
  which steps up on active days. Tap or slide to read a day ("301 over", "ate
  2,161 · budget 1,860 · +320 activity").
- Three plain numbers under it: days on budget, kcal a day vs budget, and the
  expected weight change. The "Running total" view and the second switch are gone.
- A Health Connect line shows how many past days have activity from Health
  Connect and when it was last read, with **Read again**. If Delta can't read
  activity, or finds none, it says so.

## BMI & weight group

- Every group side by side and named (Under, Healthy, Over, Obese I–III), with
  you as a dot and your target as a ring. The Home card shows this band plus the
  distance to the healthy range; the full ladder of weight ranges is on the
  energy page.

## Fixes

- Tapping the Energy or BMI card on Home now opens the page over Home, with the
  same press animation as other cards, and Back returns to Home. Before, it
  jumped to the Food tab.
- Tappable cards across the app now press in, with haptics, like the tiles.
- Activity from Health Connect is read each time the energy page opens and on
  every app sync, even when the weight sync fails.
- The minimum budget is rounded the same way as the budget (no more "1,861" next
  to "1,860").
