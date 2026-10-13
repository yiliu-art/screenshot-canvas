#!/bin/bash
# Copy a folder of images from this Mac into the Screenshot Canvas app on a
# USB-connected Android phone. The app imports them the next time it is open.
#
#   scripts/import-to-phone.sh <folder> [device-serial]
#
# Needs USB debugging on, and the debug build of the app installed (run-as only
# works for debuggable apps). Duplicate files (same content) are skipped.
set -euo pipefail

DIR="${1:?usage: import-to-phone.sh <folder> [device-serial]}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=art.yiliu.screenshotcanvas
[ -n "${2:-}" ] && ADB="$ADB -s $2"

TMP=/data/local/tmp/sc-import
$ADB shell "rm -rf $TMP && mkdir -p $TMP"
$ADB shell "run-as $PKG mkdir -p files/inbox"

seen=""
n=0; skipped=0; i=0
while IFS= read -r -d '' f; do
  h=$(md5 -q "$f")
  case " $seen " in *" $h "*) skipped=$((skipped+1)); continue;; esac
  seen="$seen $h"
  i=$((i+1))
  name=$(basename "$f" | tr -c 'A-Za-z0-9._\n-' '_')
  # numeric prefix keeps the order; the app strips it from the displayed name
  dest="$(printf '%d%04d' "$(date +%s)" "$i")-$name"
  $ADB push "$f" "$TMP/$dest" >/dev/null 2>&1 </dev/null
  $ADB shell "chmod 644 '$TMP/$dest' && run-as $PKG cp '$TMP/$dest' 'files/inbox/$dest'" </dev/null
  n=$((n+1))
done < <(find "$DIR" -maxdepth 1 -type f \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' -o -iname '*.gif' \) -print0 | sort -z)

$ADB shell "rm -rf $TMP"
$ADB shell am start -n $PKG/.MainActivity >/dev/null
echo "Queued $n image(s) for import ($skipped duplicate(s) skipped)."
