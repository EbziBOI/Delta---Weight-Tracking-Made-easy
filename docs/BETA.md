# Delta Stage 2 beta

This is a separate app, **Delta Beta** (`io.github.ebziboi.delta.beta`). It installs
beside **Delta** (`io.github.ebziboi.delta`). The normal release and its download
are unchanged. This prerelease adds the Stage 2 food diary in Glass and Tubik.

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
