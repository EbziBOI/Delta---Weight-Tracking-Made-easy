#!/usr/bin/env bash
# Runs on the CI emulator: installs the app, runs the on-device smoke test,
# and collects its screenshots and the device log into smoke-output/.
set -uo pipefail

PKG=io.github.ebziboi.delta
OUT=smoke-output
mkdir -p "$OUT"
status=0

# Give a freshly booted emulator a moment to settle; a busy one reports apps as not responding.
sleep 20
adb shell input keyevent KEYCODE_HOME || true
sleep 5

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

adb shell am instrument -w -e class "$PKG.AppearanceTest,$PKG.AppearanceGalleryTest,$PKG.RenderContractTest,$PKG.SmokeTest,$PKG.WidgetConfigurationTest,$PKG.WidgetPreviewRender" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee "$OUT/instrument.txt"

adb logcat -d > "$OUT/logcat.txt" || true

# The test saves its pictures in the app's private files; a debug build lets us copy them out.
adb exec-out run-as "$PKG" tar -cf - -C files smoke appearance-gallery widget-configuration | tar -xf - -C "$OUT" || true
ls -la "$OUT" "$OUT/smoke" || true

echo "----- notes from the test -----"
cat "$OUT/smoke/notes.txt" 2>/dev/null || echo "(none)"
echo "----- end of notes -----"

echo "----- Health Connect in the device log -----"
grep -iE "healthconnect|PermissionsActivity|HealthPermission|health\.connect|io\.github\.ebziboi" "$OUT/logcat.txt" \
  | grep -ivE "chatty|GC freed|Compiler allocated|hiddenapi|ziparchive|nativeloader|AppWidget|studio\.deploy|TestRunner|art_apex" \
  | cut -c1-320 | head -260 || true
echo "----- end of Health Connect log -----"

echo "----- crashes in the device log -----"
grep -n -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -300 || true
echo "----- end of crashes -----"

if grep -q "FAILURES!!!\|Process crashed\|INSTRUMENTATION_FAILED\|INSTRUMENTATION_STATUS: Error" "$OUT/instrument.txt"; then
  status=1
fi
if ! grep -q "^OK (" "$OUT/instrument.txt"; then
  status=1
fi
exit $status
