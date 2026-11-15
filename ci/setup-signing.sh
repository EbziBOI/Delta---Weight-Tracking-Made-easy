#!/usr/bin/env bash
# Generates a permanent local key. With an authenticated GitHub CLI, uploads the
# two encrypted repository secrets. Never prints key or password contents.
set -euo pipefail
umask 077

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
signing_dir="$project_dir/.signing"
repository="${DELTA_GITHUB_REPO:-EbziBOI/Delta---Weight-Tracking-Made-easy}"
keystore="$signing_dir/delta.jks"
password="$signing_dir/keystore-password.txt"
encoded="$signing_dir/keystore-base64.txt"

command -v keytool >/dev/null || { echo "Java's keytool is required. Use the JDK installed with Android Studio." >&2; exit 1; }
mkdir -p "$signing_dir"
chmod 700 "$signing_dir"
if [ -f "$keystore" ] && [ ! -s "$password" ]; then
    echo "Existing signing key has no password file. Restore its password; the key will not be replaced." >&2
    exit 1
fi
if [ ! -f "$keystore" ]; then
    if [ ! -s "$password" ]; then
        openssl rand -hex 32 > "$password"
    fi
    keytool -genkeypair -storetype JKS -keystore "$keystore" -alias delta \
        -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Delta" \
        -storepass:file "$password" -keypass:file "$password" >/dev/null 2>&1
fi
keytool -list -keystore "$keystore" -alias delta -storepass:file "$password" >/dev/null 2>&1 || {
    echo "Cannot open the existing signing key. Nothing has been replaced." >&2; exit 1;
}
base64 < "$keystore" | tr -d '\r\n' > "$encoded"
chmod 600 "$keystore" "$password" "$encoded"

if command -v gh >/dev/null && gh auth status >/dev/null 2>&1; then
    existing="$(gh secret list --repo "$repository" --json name --jq '.[].name')"
    if printf '%s\n' "$existing" | grep -qE '^DELTA_KEYSTORE_(B64|PASSWORD)$'; then
        echo "Signing secrets already exist. They have not been overwritten. Reuse the original key for updates." >&2
        exit 1
    fi
    gh secret set DELTA_KEYSTORE_B64 --repo "$repository" < "$encoded"
    gh secret set DELTA_KEYSTORE_PASSWORD --repo "$repository" < "$password"
    echo "Permanent signing secrets configured for $repository. Run the Build APK workflow."
else
    echo "Permanent signing key is ready. Add these GitHub Actions repository secrets:"
    echo "DELTA_KEYSTORE_B64: contents of $encoded"
    echo "DELTA_KEYSTORE_PASSWORD: contents of $password"
    echo "Settings: https://github.com/$repository/settings/secrets/actions"
fi
echo "Keep $signing_dir backed up privately. Reuse this key for every future update."
