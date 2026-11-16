#!/usr/bin/env python3
"""Reproducible transfer-only companion from the last Stage 1 tree; no Stage 2 UI."""
from pathlib import Path
import subprocess,tarfile,io,shutil,sys
repo=Path(__file__).resolve().parents[1]
destination=Path(sys.argv[1]).resolve()
if destination.exists() and any(destination.iterdir()):raise SystemExit('Choose an empty output folder')
destination.mkdir(parents=True,exist_ok=True)
archive=subprocess.check_output(['git','archive','1a80210'],cwd=repo)
with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
 for member in tar.getmembers():
  if not (destination/member.name).resolve().is_relative_to(destination):raise SystemExit('Unsafe archive path')
 tar.extractall(destination)
for name in ['data/SettingsStore.kt','transfer/TransferSnapshot.kt','transfer/TransferOutActivity.kt']:
 relative=Path('app/src/main/java/io/github/ebziboi/delta')/name
 target=destination/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(repo/relative,target)
shutil.copy2(repo/'app/src/main/res/xml/beta_transfer_paths.xml',destination/'app/src/main/res/xml/beta_transfer_paths.xml')
p=destination/'app/src/main/AndroidManifest.xml';s=p.read_text().replace('<queries>','<queries>\n        <package android:name="io.github.ebziboi.delta.beta" />')
s=s.replace('        <activity\n            android:name=".MainActivity"','''        <activity android:name=".transfer.TransferOutActivity" android:screenOrientation="portrait" android:exported="true" />
        <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.files" android:exported="false" android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/beta_transfer_paths" />
        </provider>
        <activity
            android:name=".MainActivity"''')
p.write_text(s)
p=destination/'app/build.gradle.kts';s=p.read_text().replace('releaseVersionCode = 32','releaseVersionCode = 34').replace('versionName = "0.1.$buildNumber"','versionName = "0.1.$buildNumber-transfer"')
p.write_text(s)
tests=destination/'app/src/androidTest/java/io/github/ebziboi/delta'
tests.mkdir(parents=True,exist_ok=True)
shutil.copy2(repo/'ci/transfer-tests/TransferRoundTripTest.kt',tests/'TransferRoundTripTest.kt')
local=repo/'local.properties'
if local.exists():shutil.copy2(local,destination/'local.properties')
print(destination)
