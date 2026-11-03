#!/usr/bin/env bash
# Video Tools v1.1 — emulator end-to-end test.
# Runs inside reactivecircus/android-emulator-runner (adb ready, APK prebuilt).
# Never aborts on a failed step: every check records PASS / FAIL / UNTESTED
# with evidence (screenshots + UI dumps + ls output) into test-output/.
set +e

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

cd "$(dirname "$0")/.." || exit 1          # -> video-tools-app/
ROOT="$PWD"
OUT="$ROOT/test-output"
SHOTS="$OUT/shots"
mkdir -p "$SHOTS"
RES="$OUT/results.txt"
: > "$RES"
PKG="com.videotools.app"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

say()  { echo "$*" | tee -a "$RES"; }
pass() { say "CHECK $1: PASS${2:+ — $2}"; }
fail() { say "CHECK $1: FAIL${2:+ — $2}"; }
untested() { say "CHECK $1: UNTESTED${2:+ — $2}"; }

dump() { timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; timeout 30 adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; }
has()  { python3 "$ROOT/ci-test/tap.py" check "$OUT/ui.xml" "$1" >/dev/null 2>&1; }
taptext() {
  dump
  local c
  c=$(python3 "$ROOT/ci-test/tap.py" find "$OUT/ui.xml" "$1")
  if [ -n "$c" ]; then adb shell input tap $c; return 0; fi
  return 1
}
waittext() { # $1 text, $2 tries (2s each)
  local n="${2:-15}" i
  for i in $(seq 1 "$n"); do dump; if has "$1"; then return 0; fi; sleep 2; done
  return 1
}
shot() { timeout 30 adb exec-out screencap -p > "$SHOTS/$1.png" 2>/dev/null; say "  shot $1.png ($(wc -c < "$SHOTS/$1.png" | tr -d ' ') bytes)"; }
backhome() {
  local i
  for i in 1 2 3 4 5; do dump; if has "Video Compressor"; then return 0; fi; adb shell input keyevent 4; sleep 2; done
  return 1
}
pick_first_video() {
  # System picker opened (Photo Picker or DocumentsUI). Record what it is,
  # then tap the first media cell of the grid.
  sleep 3; dump
  local pkgname
  pkgname=$(grep -o 'package="[^"]*"' "$OUT/ui.xml" | head -1)
  say "  picker root: $pkgname"
  adb shell input tap 180 480
  sleep 3
  if ! waittext "Duration" 8; then
    adb shell input tap 540 480   # second cell fallback
    sleep 2
    waittext "Duration" 8
  fi
}

say "=== Video Tools v1.1 emulator test — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
adb wait-for-device
B=""
for i in $(seq 1 60); do
  B=$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$B" = "1" ] && break
  sleep 5
done
say "boot_completed=$B"
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1
adb logcat -c >/dev/null 2>&1

# ---------------------------------------------------------------- sample media
# Round 7: media ships as committed assets (round 6 proved the ubuntu
# runner's ffmpeg produced NOTHING, silently killing every pick test).
say "tools: ffmpeg=$(command -v ffmpeg || echo MISSING) ffprobe=$(command -v ffprobe || echo MISSING)"
ASSETS="$ROOT/ci-test/assets"
cp "$ASSETS/sample.mp4" /tmp/sample.mp4 2>/dev/null
cp "$ASSETS/status1.jpg" /tmp/status1.jpg 2>/dev/null
cp "$ASSETS/status2.mp4" /tmp/status2.mp4 2>/dev/null
if [ ! -s /tmp/sample.mp4 ] && command -v ffmpeg >/dev/null; then
  ffmpeg -y -v error -f lavfi -i testsrc2=duration=10:size=1280x720:rate=30 \
    -f lavfi -i sine=frequency=440:duration=10 -c:v libx264 -pix_fmt yuv420p \
    -c:a aac -shortest /tmp/sample.mp4
fi
SAMPLE_SIZE=$(wc -c < /tmp/sample.mp4 | tr -d ' ')
say "sample.mp4 = $SAMPLE_SIZE bytes (10s, 1280x720, with audio)"
adb push /tmp/sample.mp4 /sdcard/Download/sample.mp4 >/dev/null 2>&1
adb push /tmp/sample.mp4 /sdcard/Movies/sample.mp4 >/dev/null 2>&1
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Download/sample.mp4 >/dev/null 2>&1
adb shell mkdir -p "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses" >/dev/null 2>&1
adb push /tmp/status1.jpg "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/status1.jpg" >/dev/null 2>&1
adb push /tmp/status2.mp4 "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/status2.mp4" >/dev/null 2>&1
say "fake .Statuses contents:"
adb shell ls -la "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses" 2>&1 | sed 's/^/  /' | tee -a "$RES"
sleep 3

# ---------------------------------------------------------------- install + home
if timeout 180 adb install -r "$APK" >>"$RES" 2>&1; then pass install; else fail install "adb install failed (see log above)"; fi
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1
if waittext "Video Compressor" 15; then pass launch_home; else fail launch_home "home cards not found"; fi
shot 01-home-en

# ---------------------------------------------------------------- screens open
open_screen() { # card, expected, shot, checkname
  backhome >/dev/null 2>&1
  if taptext "$1"; then
    sleep 2
    if waittext "$2" 8; then pass "screen_$4"; else fail "screen_$4" "expected text '$2' not found"; fi
  else
    fail "screen_$4" "card '$1' not tappable"
  fi
  shot "$3"
  adb shell input keyevent 4; sleep 2
}
open_screen "Video Compressor" "Pick a video"    02-compressor compressor
open_screen "Video Converter"  "Pick a video"    03-converter  converter
open_screen "Video Trimmer"    "Pick a video"    04-trimmer    trimmer

# ---------------------------------------------------------------- compressor
taptext "Video Compressor"; sleep 2
taptext "Pick a video"
if pick_first_video; then
  shot 05-compressor-picked
  taptext "Smallest size (480p)"; sleep 1
  taptext "Compress"
  if waittext "Done!" 60; then
    pass compressor_processing
    shot 06-compressor-done
    LS=$(adb shell ls -la /sdcard/Movies/VideoTools/ 2>&1)
    say "Movies/VideoTools after compress:"; echo "$LS" | sed 's/^/  /' | tee -a "$RES"
    NEWSIZE=$(echo "$LS" | grep -i '\.mp4' | awk '{print $5}' | sort -n | head -1)
    if [ -n "$NEWSIZE" ] && [ "$NEWSIZE" -lt "$SAMPLE_SIZE" ]; then
      pass compressor_output_smaller "output $NEWSIZE < sample $SAMPLE_SIZE bytes"
    else
      fail compressor_output_smaller "smallest output size seen: ${NEWSIZE:-none}"
    fi
  else
    fail compressor_processing "'Done!' never appeared"
    shot 06-compressor-stuck
  fi
else
  fail compressor_processing "video pick did not return Duration info"
  shot 05-compressor-pickfailed
fi
adb shell input keyevent 4; sleep 2

# ---------------------------------------------------------------- converter (audio extract)
taptext "Video Converter"; sleep 2
taptext "Pick a video"
if pick_first_video; then
  taptext "Audio only (M4A)"; sleep 1
  shot 07-converter-audio-selected
  taptext "Convert"
  if waittext "Done!" 60; then
    pass converter_audio_processing
    shot 08-converter-done
    LS=$(adb shell ls -la /sdcard/Music/VideoTools/ 2>&1)
    say "Music/VideoTools after convert:"; echo "$LS" | sed 's/^/  /' | tee -a "$RES"
    if echo "$LS" | grep -qi '\.m4a'; then pass converter_m4a_exists; else fail converter_m4a_exists "no .m4a in listing"; fi
  else
    fail converter_audio_processing "'Done!' never appeared"
    shot 08-converter-stuck
  fi
else
  fail converter_audio_processing "video pick did not return Duration info"
fi
adb shell input keyevent 4; sleep 2

# ---------------------------------------------------------------- trimmer
taptext "Video Trimmer"; sleep 2
taptext "Pick a video"
if pick_first_video; then
  dump
  C0=$(python3 "$ROOT/ci-test/tap.py" edit "$OUT/ui.xml" 0)
  C1=$(python3 "$ROOT/ci-test/tap.py" edit "$OUT/ui.xml" 1)
  say "  trimmer fields: start@$C0 end@$C1"
  [ -n "$C0" ] && { adb shell input tap $C0; sleep 1; adb shell input text "0:02"; sleep 1; }
  [ -n "$C1" ] && { adb shell input tap $C1; sleep 1; adb shell input text "0:07"; sleep 1; }
  dump; cp "$OUT/ui.xml" "$OUT/ui-trimmer-filled.xml"
  shot 09-trimmer-filled
  BEFORE=$(adb shell ls /sdcard/Movies/VideoTools/ 2>/dev/null | wc -l | tr -d ' ')
  taptext "Trim"
  if waittext "Done!" 60; then
    pass trimmer_processing
    shot 10-trimmer-done
    LS=$(adb shell ls -lt /sdcard/Movies/VideoTools/ 2>&1)
    say "Movies/VideoTools after trim:"; echo "$LS" | sed 's/^/  /' | tee -a "$RES"
    AFTER=$(adb shell ls /sdcard/Movies/VideoTools/ 2>/dev/null | wc -l | tr -d ' ')
    NEWF=$(adb shell ls -t /sdcard/Movies/VideoTools/ 2>/dev/null | head -1 | tr -d '\r')
    if [ "$AFTER" -gt "$BEFORE" ] && [ -n "$NEWF" ]; then
      adb pull "/sdcard/Movies/VideoTools/$NEWF" /tmp/trimmed.mp4 >/dev/null 2>&1
      DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 /tmp/trimmed.mp4 2>/dev/null)
      say "  trimmed file: $NEWF duration=${DUR}s (expected ~5s from 0:02-0:07)"
      OK=$(python3 -c "print(1 if $DUR and 3.5 <= float('$DUR') <= 6.5 else 0)" 2>/dev/null)
      if [ "$OK" = "1" ]; then pass trimmer_output_duration; else fail trimmer_output_duration "duration=$DUR"; fi
    else
      fail trimmer_output_duration "no new file appeared (before=$BEFORE after=$AFTER)"
    fi
  else
    fail trimmer_processing "'Done!' never appeared"
    shot 10-trimmer-stuck
    dump; if has "Please check the times"; then say "  note: invalid-range message shown — field entry may not have registered"; fi
  fi
else
  fail trimmer_processing "video pick did not return Duration info"
fi
adb shell input keyevent 4; sleep 2

# ---------------------------------------------------------------- status saver
backhome >/dev/null 2>&1
taptext "Status Saver"; sleep 2
if waittext "You only do this once" 8; then pass statussaver_guide_card; else fail statussaver_guide_card "guide title not found"; fi
shot 11-statussaver-guide

grant_current_folder() {
  # Taps USE THIS FOLDER in DocumentsUI, then a system Allow dialog if shown.
  sleep 2
  if taptext "USE THIS FOLDER"; then
    sleep 2
    taptext "Allow" >/dev/null 2>&1
    sleep 3
    return 0
  fi
  return 1
}
navtap() { # tap a folder row by name inside DocumentsUI, with retries
  local name="$1" i
  for i in 1 2 3; do
    if taptext "$name"; then sleep 2; return 0; fi
    sleep 2
  done
  return 1
}

# 3a) wrong folder first (deterministic): grant Download -> rescue state
if taptext "Choose folder"; then
  sleep 4; dump
  say "  tree picker root: $(grep -o 'package="[^"]*"' "$OUT/ui.xml" | head -1)"
  shot 12-tree-picker-open
  if navtap "Download" && grant_current_folder; then
    dump; shot 13-after-wrong-grant
    if has "That's not the right folder"; then
      pass statussaver_wrongfolder_rescue "rescue state shown after granting Download"
    else
      untested statussaver_wrongfolder_rescue "post-grant screen unclear — see 13-after-wrong-grant.png"
    fi
  else
    untested statussaver_wrongfolder_rescue "could not grant Download in picker"
  fi
else
  fail statussaver_guide_card "'Choose folder' button not tappable"
fi

# 3b) correct folder: reopen picker, show hidden files, navigate the full path
if taptext "Choose folder"; then
  sleep 4; dump
  # overflow menu (three dots, top-right of DocumentsUI) -> Show hidden files
  adb shell input tap 1009 187; sleep 2
  if taptext "Show hidden files"; then say "  hidden files: enabled"; else say "  hidden files: menu item not found (may already show)"; fi
  sleep 2
  OKNAV=1
  for d in "Android" "media" "com.whatsapp" "WhatsApp" "Media" ".Statuses"; do
    if ! navtap "$d"; then say "  nav failed at: $d"; OKNAV=0; break; fi
  done
  shot 14-tree-picker-statuses-dir
  if [ "$OKNAV" = "1" ] && grant_current_folder; then
    dump; shot 15-statussaver-after-grant
    if has "status1.jpg" || has "status2.mp4" || has "Tap any status to save"; then
      pass statussaver_statuses_listed "fake statuses visible after granting .Statuses"
      # tap the first status to save it
      if taptext "status1.jpg"; then
        sleep 3
        LS=$(adb shell ls -la /sdcard/Pictures/VideoTools/ 2>&1)
        say "Pictures/VideoTools after status tap:"; echo "$LS" | sed 's/^/  /' | tee -a "$RES"
        if echo "$LS" | grep -qi 'status1'; then pass statussaver_save; else untested statussaver_save "no saved file seen"; fi
      else
        untested statussaver_save "status thumbnail not tappable"
      fi
    elif has "No statuses found in this folder yet"; then
      fail statussaver_statuses_listed ".Statuses granted but listed EMPTY despite 2 pushed files"
    else
      untested statussaver_statuses_listed "post-grant screen unclear — see 15-statussaver-after-grant.png"
    fi
  else
    untested statussaver_statuses_listed "navigation/grant incomplete (OKNAV=$OKNAV) — see 14-tree-picker-statuses-dir.png"
  fi
else
  untested statussaver_statuses_listed "could not reopen picker from rescue/guide state"
fi

# ---------------------------------------------------------------- language toggle
if backhome; then
  if taptext "اردو"; then
    if waittext "ویڈیو کمپریسر" 10; then pass language_ur "home switched to Urdu strings"; else fail language_ur "Urdu card title not found after toggle"; fi
    shot 16-home-ur
    if taptext "EN"; then
      if waittext "Video Compressor" 10; then pass language_back_en; else fail language_back_en; fi
    else
      fail language_back_en "'EN' toggle not found"
    fi
  else
    fail language_ur "Urdu toggle not found on home"
  fi
else
  untested language_ur "could not navigate back home"
fi

# ---------------------------------------------------------------- crashes
timeout 60 adb logcat -d > "$OUT/logcat.txt" 2>&1
FATAL=$(grep -c "FATAL EXCEPTION" "$OUT/logcat.txt")
say "logcat FATAL EXCEPTION count: $FATAL"
grep -A 12 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60 > "$OUT/crashes.txt"
if grep -q "$PKG" "$OUT/crashes.txt"; then
  fail crash_free "fatal exception(s) mention $PKG — see crashes.txt"
elif [ "$FATAL" != "0" ]; then
  say "CHECK crash_free: PASS — fatals present but none for $PKG (see crashes.txt)"
else
  pass crash_free
fi

say "=== DONE ==="
