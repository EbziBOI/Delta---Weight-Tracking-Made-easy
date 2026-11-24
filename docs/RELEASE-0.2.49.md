# Delta 0.2.49

Faster food logging, and online product search works again.

## Product search

- Searching supermarket products (Open Food Facts) works again. The old search
  service stopped answering, so every search came back empty; Delta now uses the
  new one. Try "ferrero rocher".
- UK products are listed first. The store chips (Tesco, Sainsbury's, …) still filter.
- Picking a result loads its full record first, so serving and pack sizes are there.
- Searching again straight away waits a moment instead of saying "please wait".
- Can't find it? **Create "…"** at the end of the results starts a custom food
  with that name filled in.

## Hold a diary entry

- Touch and hold any food in the diary for: Edit amount, Duplicate, Add to today
  (when viewing another day), Move to another meal, Save or remove favourite,
  and Delete. Each offers Undo.

## Your usual amount

- Quick add (+) logs the amount you last logged for that food, instead of
  one serving or 100 g. A portion typed in the search ("45g oats") still wins.
- Food rows show what + will add, e.g. "Last 45 g", with its calories.
- Opening a food starts from your last amount too, in servings when it's a whole
  or half number of them.
