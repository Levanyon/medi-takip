# Phase 6 — Launch, onboarding and permission UX

Scope: only Phase 6 on `ui/premium-redesign-v2`, starting from accepted HEAD `c291bc15e0a6ce9e42232654cf8be29e510e6a0c`. Branch, HEAD and clean working tree were verified before inspection or edits. No commit or push.

1. **Exact files changed**

   Modified:

   ```text
   app/src/main/java/com/levanyon/meditakip/MainActivity.kt
   app/src/main/java/com/levanyon/meditakip/ui/Onboarding.kt
   app/src/main/res/values/colors.xml
   app/src/main/res/values/strings.xml
   app/src/main/res/values-v31/themes.xml
   app/src/main/res/values-night/colors.xml
   app/src/main/res/values-night-v31/themes.xml
   app/src/test/java/com/levanyon/meditakip/ui/AccessibilityTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/ScreenLayoutTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/UiFlowTest.kt
   ```

   Added:

   ```text
   app/src/main/java/com/levanyon/meditakip/ui/Launch.kt
   app/src/main/java/com/levanyon/meditakip/ui/OnboardingComponents.kt
   app/src/main/res/drawable/ic_launch_mark.xml
   app/src/main/res/drawable/ic_phase6_arrow.xml
   app/src/main/res/drawable/ic_phase6_bell.xml
   app/src/main/res/drawable/ic_phase6_check.xml
   app/src/main/res/drawable/ic_phase6_clock.xml
   app/src/main/res/drawable/ic_phase6_mark_check.xml
   app/src/main/res/drawable/ic_phase6_settings.xml
   app/src/main/res/drawable/ic_phase6_settings_action.xml
   app/src/main/res/drawable/ic_phase6_shield.xml
   app/src/main/res/drawable/ic_phase6_warning.xml
   app/src/main/res/values/phase6.xml
   app/src/test/java/com/levanyon/meditakip/ui/LaunchTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/OnboardingFlowTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/OnboardingPlatformTest.kt
   app/src/test/java/com/levanyon/meditakip/ui/Phase6ScreenshotTest.kt
   docs/PHASE6_REVIEW.md
   ```

   Downloaded SVG source files/converter, validation logs and generated PNGs remain under ignored build directories. No dependency added. Fifteen obsolete onboarding strings were removed after their replacement, resolving unused-resource lint findings.

2. **Figma nodes implemented**

   Source: [MediTakip V2](https://www.figma.com/design/XXHVX8smUwa5kVRcsfzm0d/MediTakip-V2-%E2%80%94-Phase-3-People), page **Phase 6 — Onboarding & Launch**.

   | View | Light | Dark |
   | --- | --- | --- |
   | Splash 01 | 29:117 | 29:137 |
   | Splash 02 | 29:123 | 29:143 |
   | Splash 03 | 29:130 | 29:150 |
   | Welcome | 29:157 | 29:182 |
   | Notifications | 29:207 | 29:233 |
   | Exact Alarm | 29:259 | 29:286 |
   | Ready | 29:313 | 29:332 |
   | Notification Denied | 29:351 | 29:374 |
   | Exact Alarm Denied | 29:397 | 29:420 |

   Motion specification: **29:105**. High-fidelity design contexts and reference screenshots were read for every approved node. Local SVGs were converted with the existing Android SDK converter; dimensions and call sites were checked.

3. **Launch architecture** — Android 12+ keeps its required native splash, using a local mark inside the adaptive safe zone and a theme-aware background. There is no splash library, keep-on-screen condition, startup sleep or extra loading activity. MainActivity wraps its existing destinations in a short Compose transition; the destination initializes immediately underneath. The brand consumes pointer events while visible, preventing taps reaching hidden dose/actions.

4. **Animation timing** — An elapsed frame clock drives an 800 ms transition: 0–220 ms logo opacity .35–1 and scale .92–1; 220–560 ms wordmark opacity and 6dp upward movement; 560–800 ms .97 settle and destination crossfade. Logo/text entrance uses ease-out. Resuming after an interruption accounts for elapsed frame time rather than adding another arbitrary delay.

5. **Reduced motion** — Android animator enabled state and global animator duration scale are read. Disabled animations or a reduced scale below 1 use a direct transition, without scale/movement. TalkBack touch exploration also takes the direct path. There is no new app preference. Increasing the system animation scale does not multiply this launch into a multi-second startup wait.

6. **First-launch routing** — Existing `onboardingCompleted` remains authoritative. First use reaches the new Welcome screen after launch. The completion preference implementation and keys are unchanged.

7. **Returning-user routing** — Completed onboarding routes into existing Home and its existing navigation. The normal fresh activity launch gets the branded transition; returning from background does not create a new launch sequence. Saved completed animation state avoids replay on activity/saved-state recreation.

8. **Welcome** — Approved title, body, Başla action and local-storage privacy note. Existing local database behavior and disabled Android automatic backup support the note. No encryption claim.

9. **Notification behavior** — An app-owned rationale precedes any Android request. Explicit action calls unchanged `PermissionAccess.notifications` / `rememberPermissionAccess`. Existing policy requests only POST_NOTIFICATIONS, only when required on API 33+, otherwise uses the existing app notification settings destination. Permission state and application notification toggle semantics are unchanged.

10. **Exact-alarm behavior** — Approved rationale calls unchanged `PermissionAccess.exact`. The integration test checks ACTION_REQUEST_SCHEDULE_EXACT_ALARM with the app package URI on API 31. Already available/not-required access is skipped. Manifest permission strategy and scheduling engine are unchanged.

11. **Dynamic skipped steps** — Initial missing requirements are saved with the route. Newly granted future requirements are omitted on advancement. Visited steps remain coherent with the current progress count. Current permission callbacks read the latest snapshot, avoiding stale settings-return values. No permission is requested automatically.

12. **Denied/recovery** — Both approved recovery layouts provide truthful consequences, Ayarları aç and Şimdilik geç. Denied/dismissed notification requests and denied exact settings return remain usable. Recovery invokes the same shared handlers; no fake permission dialog or repeated automatic request.

13. **Ready / first person** — Her şey hazır precedes completion. İlk kişiyi ekle calls existing `completeOnboarding(startSetup = true)`; Home consumes existing setupPending and opens the existing person editor, continuing the existing person → medication setup. The existing optional Daha sonra path calls completion without guided setup.

14. **Lifecycle / settings return** — Route, current step and attempted-permission state use rememberSaveable. Existing launcher callbacks and ON_RESUME permission refresh remain unchanged. A grant advances once; denial leaves recovery available; busy actions are disabled. Saved-state tests cover recovery and Ready restoration and theme changes. Process recreation can restore this presentation state when Android supplies saved instance state; completion itself remains persisted by the existing preference implementation.

15. **Camera absence** — No CAMERA permission/request, barcode/GTIN/scanner code, scanner destination or scanner dependency. Manifest unchanged.

16. **Responsive behavior** — Native screenshot/layout cases cover 320, 360, 390, 412 and 1200dp, plus 2.0 font scaling at 320dp, in both themes. A centered column caps at 560dp on tablets. Turkish text wraps; one scroll surface keeps all actions reachable on short/large-font layouts. Existing landscape accessibility coverage is retained.

17. **Dark mode** — Every approved screen and splash stage has a dark render. Existing Phase 1 colors and theme preferences remain authoritative. Native XML backgrounds now match the Compose light/dark background colors.

18. **Accessibility** — At least 48dp controls, visible text labels, semantic headings, progress range plus localized step description, and silent decorative icons/mark. Recovery does not depend on color. Launch is bypassed for touch exploration. Tests check target dimensions, labels, headings and progress; no physical-device TalkBack session is claimed.

19. **Screenshots** — 78 new native render cases produce 150 PNGs in `app/build/screenshots/phase6`: six splash keyframes, all twelve light/dark onboarding/recovery screens, responsive variants and action-scroll captures. Required screens, narrow/large-font actions and tablet layouts were visually inspected against Figma. These are rendered review artifacts plus layout/semantics assertions, not pixel-baseline tests. No PNG is tracked.

20. **Tests/result** — Final full run: **569 passed, 0 failures, 0 errors, 0 skipped**. This includes 355 behavioral/unit/UI/layout/accessibility cases and 214 native screenshot cases across the project. Phase 6 adds 8 launch tests, 14 onboarding-flow tests, 3 real platform/completion tests and 78 screenshot/layout/accessibility cases. Existing accessibility (68), layout (33), navigation/UI flow (10), and all protected-layer tests passed.

21. **Build** — `assembleDebug` passed. The final combined command completed with **BUILD SUCCESSFUL**. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

22. **Lint** — `lint` passed: **No issues found**, 0 errors and 0 warnings. No warning suppression was added.

23. **Protected-layer audit** — No changes to Room/schema/migrations, repositories, domain/history/dose/stock logic, reminder or notification scheduling, backup format, preference implementation/semantics, shared permission implementation, AppViewModel, manifest, dependencies, or Phase 2–5 business screens. No real user data was used for testing. `git diff HEAD --check` passed.

24. **Visual/native deviations** — Android owns its system splash duration, icon masking and actual OS permission/settings screens. Native splash theme follows device resources until Compose applies the persisted app theme. The motion text specification (.92–1 scale) takes precedence over the static 64/82/88px logo-size sketches: Compose uses one 88dp mark and a stable 24sp wordmark, animating transforms rather than discrete layout sizes. Phase 1 palette/typography remains authoritative where the reference differs. CTAs are raised to at least 48dp; Ready retains the existing Daha sonra exit. Progress dots are native and dynamic, rather than a fixed four-step SVG. Large text scrolls; tablet content stays centered. No device-frame rounded outer clipping is applied to the real app window.

25. **Regressions / fixes** — A new dynamic-route test exposed a stale permission snapshot in a retained click callback; it now reads the current state. Launch taps cannot pass through to hidden actions. Screenshot tests restore the original Android qualifiers and font scale. Intermediate suite runs exposed a People restore-message timeout and a new integration-test preference mismatch. A diagnostic identity assertion proved the new integration test could receive a ViewModel attached to a previous Robolectric Application. The new platform tests and the existing restore test now explicitly bind their ViewModel factory to the current test Application. The restore test's actions and exact message assertion are unchanged; no People production code changed. The subsequent full 569-case suite passed. Temporary diagnostics were removed. An initial SDK shadow API compile error was corrected using the installed API.

26. **Limits / unresolved issues** — No known failing automated checks or unresolved production defect found in this phase. No physical device/emulator system-splash capture, real-device permission-dialog/settings round trip, real process-kill run or TalkBack navigation is claimed. Robolectric tests exercise the real ActivityResult contracts and saved-state restoration. No Phase 7/barcode/camera/security/release work started; changes remain uncommitted for review.

Validation command (native Windows PowerShell):

```powershell
. .\tools\env.ps1
$env:MEDITAKIP_SCREENSHOTS = '1'
.\gradlew.bat --offline assembleDebug testDebugUnitTest lint --continue --console=plain
```

Full log: `build/phase6-final.log`. Test XML/HTML: `app/build/test-results/testDebugUnitTest`, `app/build/reports/tests/testDebugUnitTest`. Lint: `app/build/reports/lint-results-debug.txt`.
