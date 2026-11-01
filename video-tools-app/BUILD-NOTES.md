# Video Tools v1 — Build Notes

_Last updated: 2026-11-01 (second update — compile attempt after the owner's
permission flip; see "Blocker" below). App: "Video Tools", package
`com.videotools.app` (placeholders)._

## Status in one line

Toolchain installed and verified, full source written and statically audited,
Gradle wrapper verified working offline — **the APK build is still blocked, now
empirically narrowed: the owner flipped `other_tcp` ON (per-connection "Ask"
mode), but in this runtime Ask mode black-holes/resets Java loopback sockets
instead of passing them through, so the Gradle client↔daemon handshake still
cannot complete.** No APK exists yet — none is faked, and nothing has been
tested on a device or emulator. Build logs: `build-run-1.log`, `build-run-2.log`
in this folder.

## The blocker (exact)

**Era 1 — `other_tcp` = Deny (verified 2026-11-01, morning):** any raw TCP
connection from a Java process (Gradle daemon client↔daemon on 127.0.0.1, or
Java→egress-proxy for dependency downloads) was intercepted by the platform and
answered with this banner instead of reaching its destination:

> `muse: Other TCP connections is turned off for this assistant. To allow it, ask
> the user to open Muse settings -> Permissions -> Direct network protocols and
> switch other_tcp from Deny to Ask.`

**Era 2 — `other_tcp` = ON / per-connection Ask (owner flipped it 2026-11-01;
compile attempted the same day): still blocked, different mechanism.** Proven
empirically:

- `build-vt.sh` ran twice (`build-run-1.log`, `build-run-2.log`). Both times the
  Gradle daemon started and listened on 127.0.0.1 (daemon logs
  `gradle-home/daemon/8.11.1/daemon-3482.out.log`, `daemon-3660.out.log`), and
  both times the client failed with: **"The first result from the daemon was
  empty. The daemon process may have died or a non-daemon process is reusing the
  same port."** The daemon logs show it *never received any connection*; it
  exited only after the client gave up and cleaned the registry.
- Minimal Java loopback echo test (two JVMs, `~/workspace/toolchain/tmp/nettest`,
  after the flip): `connect()` returns in ~18 ms — but to the interception
  layer, not the real listener. **Zero bytes reach the server**: the client
  timed out after 60 s with the server still blocked in `accept()`. A second
  client attempt made *after* an approval was recorded got
  `java.net.SocketException: Connection reset` on read. So in Ask mode the
  layer holds a new connection pending approval and the held connection never
  resumes; later connections are reset rather than passed through.
- python/curl (the sanctioned egress channels) still reach loopback and the
  proxy fine — this is specific to raw Java sockets.

**What would actually unblock the build (owner/platform decision):**
(a) if the platform offers an **Allow-always** state for Other TCP connections
(no per-connection prompt), switching to it should let the loopback handshake
pass — Ask mode as implemented here cannot; or (b) run the build outside this
VM's restricted network (owner's machine or CI) — the project, wrapper and
toolchain layout are portable; on a normal machine restore the wrapper's
`distributionUrl` to the official Gradle URL (noted in the properties file) and
run `./gradlew :app:assembleDebug`. Note the dependency downloads (Java→proxy)
face the same raw-TCP layer and were never reached — they remain unverified
even if the handshake is fixed.

## Build recipe (once unblocked)

```bash
~/workspace/toolchain/build-vt.sh                 # default: :app:assembleDebug
~/workspace/toolchain/build-vt.sh :app:lintDebug  # lint pass
# APK lands in app/build/outputs/apk/debug/app-debug.apk
# -> copy to ~/workspace/video-tools-app/VideoTools-v1.apk for sideloading
```

`build-vt.sh` refreshes the Gradle proxy settings from the current `$https_proxy`
env var first (the egress password rotates; a stale password in
`~/workspace/toolchain/gradle-home/gradle.properties` silently breaks downloads —
this bit us once), exports JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME / TMPDIR,
and invokes `./gradlew`. The wrapper's `distributionUrl` points at the local
Gradle zip (offline-safe); to build on a normal machine, switch it back to
`https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` (noted in the
properties file; sha256 already matches the official zip).

If a first compile surfaces unresolved `kotlinx.coroutines` references in
StatusSaverScreen, add `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")`
to `app/build.gradle.kts` (it is expected transitively via activity-compose /
lifecycle, but it was never compiler-verified).

## Toolchain (all under ~/workspace/toolchain/)

| Piece | Version | Location / notes |
|---|---|---|
| JDK | Temurin OpenJDK 17.0.21 | `toolchain/jdk` |
| Android SDK | platform android-36 rev 2, build-tools 36.0.0, platform-tools 37.0.1 | `toolchain/sdk` — installed manually from official zips (sdkmanager's Java proxy tunnelling is broken in this VM); license files in `sdk/licenses/` |
| Gradle | 8.11.1 | `toolchain/gradle-8.11.1`; `GRADLE_USER_HOME=toolchain/gradle-home` (proxy systemProps, daemon off, Kotlin compiler in-process, modest heaps) |
| Wrapper | Gradle 8.11.1 | project `gradlew`/`gradle-wrapper.jar` genuine (jar extracted from the distribution's `gradle-wrapper-main`, scripts from the official v8.11.1 tag); `./gradlew --version` verified working 2026-11-01 |

Disk note: keep caches under `~/workspace` (`/tmp` is a small tmpfs — `TMPDIR`
is redirected to `toolchain/tmp`). RAM is tight (~8 GB shared), hence the
in-process Kotlin compiler setting and single-use builds.

## App facts

- Kotlin + Jetpack Compose (BOM 2025.03.00), **Media3 Transformer 1.6.1** for all
  processing (no FFmpegKit — retired). AGP 8.9.2, Kotlin 2.1.20.
- compileSdk/targetSdk **36** (Play requirement for new apps since Aug 2026),
  minSdk 26. Fully offline: the manifest declares **no INTERNET permission**
  (only `WRITE_EXTERNAL_STORAGE` capped at API 28 for legacy saving).
- No ads SDK, no watermark anywhere (by design — ads arrive only at publish
  stage, light and after-task per the owner's monetization principle).
- Bilingual EN/اردو toggle in the top bar, persisted; Urdu strings are proper
  Urdu script (46 string fields, both languages complete — worth the owner's
  once-over for phrasing).
- Original vector launcher icon + original geometric tool glyphs
  (`res/drawable/ic_tool_*.xml`). Note: the four Material icons first used for
  the tool cards (Compress/SwapHoriz/ContentCut/Download) are **not** in
  material-icons-core (checked against the real 1.7.8 AAR) — replaced with the
  custom glyphs; the back arrow (AutoMirrored ArrowBack) is verified present.

## Per-tool status (code-complete; NOT compiled or run yet)

| Tool | Implementation | Notes |
|---|---|---|
| Compressor | ✅ code | Presets (WhatsApp-ready ≤720p/~15.5 MB, Small 480p/35%, Medium 720p/60%) + custom target-size slider; bitrate derived from target size & duration; before→after sizes shown; saves to Movies/VideoTools |
| Converter | ✅ code | MP4 at Original/1080/720/480/360, or audio extract; saves to Movies/VideoTools or Music/VideoTools |
| Video → MP3 | ⚠️ code, format caveat | Android/Media3 has no MP3 muxer: audio extract is **M4A (AAC)**, labelled honestly in the UI as "Audio only (M4A)" |
| Trimmer | ✅ code | mm:ss start/end fields with validation, Media3 clipping export, saves to Movies/VideoTools |
| Status Saver | ✅ code | SAF `ACTION_OPEN_DOCUMENT_TREE`, persisted grant, BFS-locates `.Statuses` inside the granted tree (WhatsApp / WA Business both work — user picks the folder), Coil thumbnails incl. video frames, tap saves to Pictures/VideoTools; no dangerous permission on API 29+; graceful folder-error state |

Processing is async via Transformer with a progress poller (250 ms), cancel
support, and plain-language error messages. Target size from the compressor is
approximate (encoder-dependent), as in all such apps.

### Static verification actually performed (no compiler available)
- All 7 Android XML files parse; manifest declares only the capped legacy
  storage permission.
- All cross-references between screens and the 46-field `AppStrings` resolve in
  both languages (46/46 named args each).
- All `VideoProcessor.process(...)` call sites match its signature; Media3 API
  usage reviewed against 1.6.1 (Listener signatures, ProgressHolder,
  Presentation.createForHeight, encoder settings, clipping).
- Icon availability verified against the real material-icons-core 1.7.8 AAR
  (see above — one real bug found and fixed).
- `./gradlew --version` runs end-to-end offline.

**Not done (blocked — see "The blocker"):** assembleDebug (attempted twice on
2026-11-01 after the owner's flip; failed at the daemon handshake both times,
before any source compilation — **no source file has been compiler-checked
yet**, and no compile error has been seen or fixed), lint, any run on
emulator/device — emulator is infeasible in this VM regardless (RAM), so first
real test is sideloading the debug APK on the owner's phone.

## Next steps toward a Play-ready AAB

1. Resolve the network blocker (Allow-always for Other TCP, or build off-VM —
   see "The blocker"). Run `build-vt.sh` → fix any first-compile
   nits → lint → sideload `VideoTools-v1.apk` on a real phone and test all four
   tools end-to-end (esp. a large-file compress and a WA Business status save).
2. Release signing: generate an upload keystore (owner keeps the password in the
   Secure Vault), wire `signingConfigs` in `app/build.gradle.kts`, build
   `:app:bundleRelease`.
3. AdMob integration at publish stage only: banner/native placements shown
   after a task completes, never interstitials mid-flow (owner's standing
   monetization principle); add the INTERNET permission + AdMob app id then.
4. Play Console (owner): developer account ($25 + CNIC identity verification),
   listing text/screenshots/privacy policy (Jarves prepares), closed test with
   **12 testers × 14 days** (mandatory for new personal accounts), then production.
5. Polish candidates for v1.1: true MP3 export is not possible with platform
   muxers (would need a bundled encoder — revisit only if users demand it);
   SAF tree re-grant prompt if WhatsApp changes storage again; share-intent entry
   points ("Send to Video Tools" from gallery apps).
