#!/usr/bin/env bash
# Round 5 driver: boot the emulator MANUALLY (ubuntu runner + KVM) and then
# run the end-to-end checks. Lessons baked in:
#  - round 3: SDK tools were not on PATH in the step shell -> export it.
#  - round 4: script stalled for 70 min at/around the emulator launch +
#    `adb wait-for-device` (unbounded) and produced no boot polls.
#    -> every adb call is timeout-wrapped, emulator state is logged right
#       after launch, and emu.log starts fresh so evidence can't be stale.
set +e

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

cd "$(dirname "$0")/.." || exit 1          # -> video-tools-app/
ROOT="$PWD"
OUT="$ROOT/test-output"
mkdir -p "$OUT"
rm -f "$OUT/emu.log"
exec > >(tee -a "$OUT/ci-main.log") 2>&1

echo "=== ci-main — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
echo "KVM device: $(ls -la /dev/kvm 2>&1)"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "adb: $(command -v adb) | avdmanager: $(command -v avdmanager) | emulator: $(ls "$ANDROID_HOME/emulator/emulator" 2>&1)"

echo "--- creating AVD"
echo no | avdmanager create avd -n vt \
  -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
echo "avdmanager exit=$? ; AVD dir: $(ls ~/.android/avd/ 2>&1)"

echo "--- launching emulator"
setsid "$ANDROID_HOME/emulator/emulator" -avd vt -no-window -gpu swiftshader_indirect \
  -noaudio -no-boot-anim -camera-back none -camera-front none -no-snapshot \
  > "$OUT/emu.log" 2>&1 < /dev/null &
echo "emulator launched, shell pid $!"
sleep 8
echo "--- emulator state after 8s:"
ps aux | grep -i "[q]emu" | head -3
echo "emu.log size: $(wc -c < "$OUT/emu.log" 2>/dev/null) bytes; head:"
head -20 "$OUT/emu.log" 2>/dev/null
timeout 20 adb wait-for-device
echo "wait-for-device exit=$?"
timeout 15 adb devices

B=""
for i in $(seq 1 60); do
  B=$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  echo "boot poll $i: sys.boot_completed='$B'"
  [ "$B" = "1" ] && break
  sleep 10
done
echo "boot_completed=$B"

if [ "$B" = "1" ]; then
  bash "$ROOT/ci-test/run-tests.sh"
else
  echo "EMULATOR BOOT FAILED — test script not run. emu.log tail:"
  tail -30 "$OUT/emu.log" 2>/dev/null
fi

timeout 15 adb emu kill >/dev/null 2>&1
sleep 2
echo "=== ci-main done — $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
