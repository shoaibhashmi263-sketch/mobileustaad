#!/usr/bin/env bash
# Round 3 driver: boot the emulator MANUALLY (ubuntu runner + KVM) and then
# run the end-to-end checks. Everything is tee'd into test-output/ so the
# commit-back step preserves evidence even when a phase fails.
set +e

# GitHub step shells do NOT put the SDK tools on PATH (round 3 failed here:
# avdmanager/adb "command not found", AVD never created).
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

cd "$(dirname "$0")/.." || exit 1          # -> video-tools-app/
ROOT="$PWD"
OUT="$ROOT/test-output"
mkdir -p "$OUT"
exec > >(tee -a "$OUT/ci-main.log") 2>&1

echo "=== ci-main — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
echo "KVM device: $(ls -la /dev/kvm 2>&1)"
echo "ANDROID_HOME=$ANDROID_HOME"

echo "--- creating AVD"
echo no | avdmanager create avd -n vt \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force

echo "--- launching emulator"
"$ANDROID_HOME/emulator/emulator" -avd vt -no-window -gpu swiftshader_indirect \
  -noaudio -no-boot-anim -camera-back none -camera-front none -no-snapshot \
  > "$OUT/emu.log" 2>&1 &

adb wait-for-device
B=""
for i in $(seq 1 72); do
  B=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  echo "boot poll $i: sys.boot_completed='$B'"
  [ "$B" = "1" ] && break
  sleep 10
done
echo "boot_completed=$B"

if [ "$B" = "1" ]; then
  bash "$ROOT/ci-test/run-tests.sh"
else
  echo "EMULATOR BOOT FAILED — test script not run. See emu.log."
fi

adb emu kill >/dev/null 2>&1
sleep 2
echo "=== ci-main done — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
