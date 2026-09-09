# Pause — Onboarding & Release Tracker

**Pause** is an Android digital-wellbeing app: a draggable floating bubble runs a timer; when it
ends, a 4-7-8 breathing wind-down (skippable) plays, and a "Stop for now" break can cover chosen
apps (TikTok/Instagram/…) for a set time. Kotlin, Jetpack Compose setup screen + a foreground
`OverlayService` that draws the overlays. Package `io.github.mzuhairkhan.pause`.

Near-term goal: **ship on F-Droid**. Possibly Google Play later (optional).

---

## Build, run, test

`java` is **not** on PATH; use the JDK bundled with Android Studio. All commands from the repo root:

```bash
# Windows (PowerShell): set JAVA_HOME for the session first.
# With two Android Studio folders, the real one is whichever has jbr\bin\java.exe;
# the other is an incomplete install and gives a misleading "invalid directory".
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"

./gradlew.bat testDebugUnitTest     # run JVM unit tests
./gradlew.bat assembleDebug         # build the installable debug APK
./gradlew.bat lintDebug             # lint (CI runs this)
./gradlew.bat assembleRelease       # minified release APK (UNSIGNED — see below)
```

- Debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
- To share a test build, copy it to the repo root as `Pause-<version>-debug.apk` (git-ignored).
- Version lives in `app/build.gradle.kts` (`versionName` / `versionCode`). Currently **0.5.1 / 7**.
  Bump `versionCode` for every build you distribute. Tag releases `vX.Y.Z`.

## Architecture (key files)

| File | Role |
|---|---|
| `OverlayService.kt` | The foreground service: floating bubble, timer + `AlarmManager` scheduling, breathing wind-down, "Stop for now" app-blocking break, notification. The big one. |
| `MainActivity.kt` | Compose setup screen: permissions, bubble alignment, theme/accent, breathing settings, app blocking. |
| `SettingsStore.kt` | SharedPreferences-backed settings. First-run defaults come from `SettingsDefaults`. |
| `PauseLogic.kt` | **Pure, Android-free** logic — `TimeFormat`, `HourglassMath`, `BubblePosition`, `BubblePresets`, `SettingsRanges`, `SettingsDefaults`. Unit-tested. |
| `ui/theme/Accents.kt` | Accent palette (Blue is the default, listed first). |
| `HourglassDrawable` / `RingDrawable` / `ShadowDrawable` | Custom bubble glyphs (white + soft shadow). |
| `TimerReceiver` / `BootReceiver` | Alarm fire; re-post the "Start" notification after reboot. |
| `res/layout/` | `overlay_bubble`, `timer_picker`, `breathing`, `block_overlay`, `dismiss_target`. |

Pure logic lives in `PauseLogic.kt` and is covered by `app/src/test/.../PauseLogicTest.kt`; keep
that split so behaviour stays unit-testable without an emulator.

## Permissions

`SYSTEM_ALERT_WINDOW` (overlay), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS` (Usage Access — for the
break's foreground-app detection), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. No `INTERNET` — the
app makes no network calls and has no analytics. Data is local SharedPreferences only;
`allowBackup="false"`.

---

## Translations

UI text lives in `res/values/strings.xml` (+ `<plurals>`); each language is a
`res/values-<code>/strings.xml`. `res/xml/locales_config.xml` powers the Android 13+ per-app
language picker (Settings → Apps → Pause → Language).

Translation happens on **Weblate** (hosted.weblate.org, libre plan, component `App strings`).
Translators work there and Weblate opens a pull request per language from its own fork, so it
never holds write access; a push webhook keeps it in step with `main`.

Shipped: **English** + **Finnish** (reviewed by Joonas Nivala). In the tree but **not shipped**:
`de`, `es`, `it`, `pt`, `sv`, `tr` — machine-assisted drafts flagged "needs rewriting" in Weblate,
checked by no native speaker. `androidResources.localeFilters` pins the APK to `en` and `fi` and is
what actually keeps them out: resource resolution follows the device locale and ignores
`locales_config.xml`. To ship one, review it and add its code to **both** files.

Five strings have no draft in any of the six — both `stepper_*` labels, `picker_minutes_label`
and the two plurals, all of which postdate the review documents. They carry
`tools:ignore="MissingTranslation"`; drop it once they are filled in.

**Finnish round 2.** The 22 strings changed after Joonas's first pass (`kelluva painike` → `kupla`,
`peiteilmoituspalvelu` → `Näytä/Piilota kupla`, `breathing_done` → `Harjoitus ohi`, the new `unit_*`
and `slider_readout` resources, the quote-mark change) came back with one correction, applied:
`onb_size_body` is "Sovitamme kuplan koon sovelluksen painikkeisiin."

By hand instead: copy `values/strings.xml` to `values-<code>/strings.xml`, keep the `name=` keys and
the `%1$s` / `%1$d` / `✓` intact, add a `<locale>`. `lintDebug` flags missing keys and bad placeholders.

## Release checklist

### Done (audit follow-up, this round)
- [x] Timer-fire path shows the wind-down even if foreground-service promotion is rejected.
- [x] Media stays muted continuously across the breathing → app-block hand-off (no audio blip).
- [x] Block cover re-detection uses a rolling usage-event cursor (survives missed events better).
- [x] Breathing wind-down accessibility: phase announced (live region), circle marked decorative,
      no-skip lock bypassed under a screen reader.
- [x] 48dp minimum tap targets (picker tabs, snooze, steppers, accent swatches).
- [x] Accent swatches labelled for TalkBack; dead code removed; settings-defaults unit tests added.

### F-Droid (blockers first)
- [x] **Added a `LICENSE`: GPLv3** (strong copyleft — derivatives must stay open source, no
      proprietary forks). Compatible with the app's Apache-2.0 deps (Apache-2.0 is one-way
      compatible *into* GPLv3); **avoid GPLv2**, which is incompatible with Apache-2.0. F-Droid
      requires a free license.
- [x] **Release build compiles** — `assembleRelease` with R8/minify + resource shrinking succeeds
      against current code (1.7 MB unsigned APK); `lintVitalRelease` passes. F-Droid builds release
      from the git tag and signs it themselves, so **no signing config is needed for F-Droid**.
- [ ] **Smoke-test that release APK on a real device** — minified builds can fail at runtime where
      debug builds do not (R8 stripping reflectively-reached code); `proguard-rules.pro` is empty,
      so this is unverified.
- [ ] **On-device verify the timer fires the wind-down** when backgrounded / screen-off / swiped
      from recents (the highest-risk functional path; grant "Ignore battery optimization").
- [x] F-Droid metadata: `fastlane/metadata/android/en-US/` (title, short + full descriptions,
      per-versionCode changelogs) **and `images/`** — 512x512 `icon.png` plus five 1080x2340
      `phoneScreenshots/` (welcome, settings, break cover, language step, permissions step). Regenerate the screenshots with `gradlew recordRoborazziDebug` and
      re-copy from `app/build/outputs/roborazzi/`; they are renders of the real UI, so they stay
      honest as the app changes. F-Droid orders `phoneScreenshots` alphabetically, hence `1.png`…
      Finnish images are deliberately *not* added yet: a locale's `images/` dir replaces the
      en-US set rather than supplementing it, and only three Finnish captures exist, so Finnish
      users would see fewer screenshots, not localized ones. Add `fi/` once the overlay screens
      are captured in Finnish too.
- [ ] **The two signature screens are missing from the listing.** The timer picker and the
      breathing wind-down are what the app *is*, but `OverlayLayoutScreenshotTest` inflates their
      layouts with no runtime state — preset buttons render unlabelled and the breathing screen
      renders as a bare circle, because both are populated in `OverlayService` (around
      `OverlayService.kt:773` and `:1022`). Capturing them honestly means driving that service
      path under Robolectric rather than inflating the XML. Worth doing before the listing is
      really good; do not stage them by hand, or the screenshots stop tracking the real UI.
- [x] **Reproducible release build — verified.** `assembleRelease` is byte-identical across a
      clean rebuild *and* across a fresh clone at a detached HEAD in a different directory
      (`87e1b9d9…`, JDK 21, build cache off). `release { vcsInfo { include = false } }` drops
      `META-INF/version-control-info.textproto`, which records the commit built and so matches
      only for a rebuilder with the same git metadata — not one building from a tarball, a
      shallow clone, or a moved tag. `dependenciesInfo { includeInApk/includeInBundle = false }`
      is insurance rather than a fix: the Google-signed dependency blob lives in the APK signing
      block, so it is already absent from our externally-signed unsigned APK, and the setting
      stops the Play AAB or a future in-Gradle `signingConfig` reintroducing it. Nothing else
      needed stripping — every file under `res/` is vector XML, so F-Droid's usual advice about
      PNG crunching and generated densities is moot here.
      The `Reproducible build` workflow guards all of this; it rebuilds from a fresh clone
      precisely because two builds in one working tree share a git state and an absolute path,
      and would agree even if the APK embedded either.
      **Build with JDK 21** (what CI and Android Studio's JBR use); a different major JDK changes
      the dex, and that is the one variance neither the workflow nor a local rebuild can catch —
      pin it in the fdroiddata metadata and let F-Droid's verification prove it.
- [ ] **fdroiddata submission** (source + issue-tracker URLs, license, `UpdateCheckMode: Tags`).
      With the build reproducible, prefer `Binaries:` + `AllowedAPKSigningKeys:` so F-Droid
      verifies and ships *our* signed APK instead of re-signing — see the signing-key decision
      below.
- [x] `<monochrome>` layer on the adaptive icon (themed-icon polish).

### Google Play — release plan

Play is a bigger lift than F-Droid: F-Droid builds and signs from our git tag, whereas Play
demands a signed bundle, a console full of declarations, and review of three permissions that
Google treats as sensitive. Ordered by what blocks what.

#### 1. Build changes (do these first — they gate everything else)
- [x] **Moved to SDK 36** (`compileSdk` and `targetSdk`), which required AGP 8.7.3 → 8.13.2 and
      the Gradle wrapper 8.11 → 8.14.5 (AGP 8.13 refuses to run on anything below Gradle 8.13).
      Deliberately stayed on AGP 8.x rather than jumping to 9.x, which carries breaking changes.
      Verified afterwards: 37 unit tests pass, lint reports **0 errors**, and the minified release
      APK still builds. Lint surfaced no foreground-service or notification warnings from the
      new target — but lint cannot see runtime behaviour changes, and this app leans hard on
      foreground services, so treat the emulator job's release smoke test as the real check.
      This also satisfies Play's target-API floor.
- [x] **Uses Android 16 promoted ongoing notifications.** Now unblocked by the SDK bump, and the
      only supported route to a genuinely pinned notification: `setOngoing` plus `FLAG_NO_CLEAR`
      does not survive an individual swipe on Android 14+, and Samsung's One UI clears them
      regardless (confirmed on device). The API is
      `Notification.Builder.setRequestPromotedOngoing(boolean)` with `FLAG_PROMOTED_ONGOING` and
      `hasPromotableCharacteristics()`. Three caveats: it is a **request** the system may decline;
      the notification has to qualify as promotable; and `androidx.core` 1.15.0 **does not wrap
      it**, so either bump core and re-check or call the platform builder behind an API-36 guard.
      Test on a Samsung device specifically — that is where the current behaviour falls down.
      The platform gate turned out to be **ongoing + `setColorized(true)` with a colour** —
      `ProgressStyle` and `setShortCriticalText` are *not* required, contrary to the obvious
      reading of the Live Updates docs. Established empirically against
      `NotificationCompat.hasPromotableCharacteristics`, and locked in by
      `PromotedNotificationTest` so a future edit cannot silently drop the pin.
      Still to confirm on hardware, Samsung especially: promotion is a request the system may
      decline, and One UI is where the old behaviour fell down.
- [ ] **Produce an `.aab`.** Play has not accepted APKs for new apps since 2021. `assembleRelease`
      is not enough; wire and test `bundleRelease`.
- [ ] **`bundle { language { enableSplit = false } }`.** Without it, Play splits by language and
      the per-app language picker can select a locale whose strings were never downloaded. Lint
      already flags this (`AppBundleLocaleChanges`). Non-negotiable now that we ship Finnish.
- [ ] **Release signing config + Play App Signing.** `app/build.gradle.kts` has no `signingConfig`
      at all — the release workflow signs externally with `apksigner`. Play needs an upload key
      enrolled in Play App Signing.
- [ ] **Smoke-test the minified bundle on a device.** `isMinifyEnabled = true` with a completely
      empty `proguard-rules.pro`. R8 can strip reflectively-reached code, and a bundle adds
      resource-splitting on top. This has never been run on hardware.

#### 2. Console declarations (expect review friction)
- [ ] **`FOREGROUND_SERVICE_SPECIAL_USE`** — highest rejection risk. Google wants a
      standard FGS type where one fits, and reviews `specialUse` by hand. A justification is
      already drafted in `AndroidManifest.xml` (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`); reuse that
      wording in the console so the two match.
- [ ] **`PACKAGE_USAGE_STATS`** — a sensitive permission. Needs a declaration plus a prominent
      in-app disclosure *before* the permission is requested. Our advantage: it is genuinely
      optional and only powers the app-blocking break, so say exactly that.
- [ ] **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** — restricted; must map to an allowed use case.
      An alarm that must fire on time is a defensible one, but expect to argue it.
- [ ] **`SYSTEM_ALERT_WINDOW`** — must be core to the product. It is, and that is easy to show.
- [ ] Data safety form. Easy win: no collection, no sharing, no network code at all.
- [ ] Content rating questionnaire; target-audience declaration.
- [ ] **Hosted privacy policy URL** — required, and doubly so with the sensitive permissions.

#### 3. Account and testing runway (start early — this is the long pole)
- [ ] Developer account (one-off 25 USD).
- [ ] **Closed testing before production.** Personal (non-organisation) accounts must run a
      closed test with a minimum number of testers held for a continuous period — recently
      12 testers for 14 days — before production access unlocks. Recruit testers *now*; this
      is calendar time no amount of engineering removes. Verify the current numbers in the console.

#### 4. Store listing assets (none exist yet)
- [ ] 512x512 PNG app icon, 1024x500 feature graphic, and at least two phone screenshots.
      `fastlane/metadata/android/en-US/` has all the text but no `images/` directory.
- [ ] The listing text can be reused from the F-Droid `full_description.txt` largely as-is.

#### 5. Product polish worth doing before either store
- [x] `<monochrome>` layer on the adaptive icon (closes lint `MonochromeLauncherIcon`; themed
      icons). Same geometry and progress frame (0.58) as `ic_launcher_foreground.xml`, generated
      with `tools/gen_hourglass_frame.py` so the two can't drift into different silhouettes.
- [ ] **The launcher glyph is undersized.** The hourglass occupies roughly 43% of the 108dp
      canvas where the adaptive-icon safe zone is about 61%. It will read noticeably smaller than
      neighbouring icons. The notification icon, by contrast, is correct.
- [ ] Delete the four dead `reminder_*` strings (unreferenced; superseded by the wind-down).
- [ ] Finnish re-review (21 strings). The six other languages never came back from review — their
      drafts are seeded in-tree and gated out of the build; Weblate is the way to get them corrected.
- [ ] Instrumented tests — `app/src/androidTest` does not exist. The overlay, alarm and
      app-blocking paths are exactly what unit tests cannot reach.

#### 6. Decisions to make deliberately
- **GPLv3 and Play App Signing.** Google holds the signing key, so a user cannot rebuild and
  install their own binary. Many GPL apps ship on Play regardless, but decide consciously rather
  than discover the argument later.
- **Two stores, two signing keys — now avoidable, but only if decided before Play enrolment.**
  By default an F-Droid install and a Play install are different apps to Android and cannot
  update each other. The way out: the build is reproducible, so F-Droid can verify and publish
  our own signed APK (`Binaries:` + `AllowedAPKSigningKeys:`) rather than re-signing. That only
  helps if we also hold the Play key — meaning **upload our own key to Play App Signing** instead
  of letting Google generate one. Google will not hand over a key it generated, so choosing
  wrong here is permanent. If we take the default instead, pick which store is canonical and say
  so in the README.
- **No crash reporting.** The store description promises no analytics and no network access.
  Keeping that promise means shipping blind. That is a legitimate choice — just an explicit one.

### Quality backlog (not blocking)
- [x] Internationalization: all UI strings live in `strings.xml`; Finnish (`values-fi`) added
      (reviewed by Joonas Nivala) and Android 13 per-app language wired (`res/xml/locales_config.xml`).
- [ ] More tests (instrumented flows); de-duplicate the permission-check helpers.
