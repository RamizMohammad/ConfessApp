#!/usr/bin/env bash
# Push lab: fire each Confess notification template on the connected phone (debug build installed),
# screenshot the pop-up once it has settled, and dump the real on-screen bounds of every view in it.
#
#   scripts/push_lab.sh                 # all five templates
#   scripts/push_lab.sh whisper teaser  # just these
#
# Output: push_lab_out/<template>.png and <template>.txt (bounds of the pop-up and our views).
# Send the folder back to Claude to tune the card to this phone's real sizes.
set -euo pipefail
PKG=in.mohammad.ramiz.confess
OUT=push_lab_out
mkdir -p "$OUT"
templates=("$@"); [ ${#templates[@]} -eq 0 ] && templates=(spark whisper teaser digest winback streak)

adb shell getprop ro.product.model > "$OUT/device.txt"
adb shell getprop ro.build.version.release >> "$OUT/device.txt"
adb shell getprop ro.build.display.id >> "$OUT/device.txt"
adb shell wm density >> "$OUT/device.txt"
adb shell settings get system font_scale >> "$OUT/device.txt" || true

for t in "${templates[@]}"; do
  echo "→ $t"
  adb shell cmd notification cancel_all >/dev/null 2>&1 || true
  adb shell am broadcast -a $PKG.PUSH_LAB -p $PKG --es template "$t" >/dev/null
  sleep 1.6                                   # let the pop-up finish sliding in
  adb exec-out screencap -p > "$OUT/$t.png"
  adb shell uiautomator dump /sdcard/push_lab.xml >/dev/null 2>&1 || true
  adb pull /sdcard/push_lab.xml "$OUT/$t.xml" >/dev/null 2>&1 || true
  # Our views (push_card, push_badge, push_title, push_body) and anything SystemUI wraps them in.
  grep -oE '(resource-id="[^"]*(push_|headsup|heads_up|notification|expanded|content)[^"]*"[^>]*bounds="[^"]*")' \
    "$OUT/$t.xml" | sed -E 's/ (text|class|package|content-desc|checkable|checked|clickable|enabled|focusable|focused|scrollable|long-clickable|password|selected)="[^"]*"//g' \
    > "$OUT/$t.txt" || true
  sleep 4
done
echo "Done → $OUT/"
